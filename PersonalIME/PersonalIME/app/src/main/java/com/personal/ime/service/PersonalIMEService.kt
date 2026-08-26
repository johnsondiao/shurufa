package com.personal.ime.service

import android.inputmethodservice.InputMethodService
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.personal.ime.data.ClipboardManager
import com.personal.ime.data.DictionaryDatabase
import com.personal.ime.data.PreferencesManager
import com.personal.ime.engine.EnglishEngine
import com.personal.ime.engine.PinyinEngine
import com.personal.ime.util.FeedbackManager
import kotlinx.coroutines.*

class PersonalIMEService : InputMethodService() {

    /** 输入模式：中文 9 键 / 英文 26 键 */
    private enum class InputMode { CHINESE_T9, ENGLISH_QWERTY }

    /** 键盘页面：T9 / 符号 / 数字 */
    private enum class KeyboardPage { T9, SYMBOL, NUMBER }

    private lateinit var database: DictionaryDatabase
    private lateinit var pinyinEngine: PinyinEngine
    private lateinit var englishEngine: EnglishEngine
    private lateinit var preferencesManager: PreferencesManager
    private lateinit var clipboardManager: ClipboardManager
    private lateinit var feedbackManager: FeedbackManager

    private var currentInput = ""
    private var inputMode = InputMode.CHINESE_T9
    private var keyboardPage = KeyboardPage.T9
    private var symbolPage = 1
    private var isShifted = false
    private var isPrivacyMode = false
    private var vibrationStrength = 30
    private var soundVolume = 20

    private var keyboardContainer: LinearLayout? = null
    private var candidateLayout: LinearLayout? = null
    private var shiftKey: Button? = null
    private val qwertyLetterKeys = mutableListOf<Button>()

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // 符号键盘两页内容
    private val symbolPages = arrayOf(
        // 第 1 页：常用中文标点与符号
        arrayOf("，", "。", "、", "；", "：", "？", "！", "「", "」"),
        // 第 2 页：括号、数学、货币等
        arrayOf("（", "）", "【", "】", "《", "》", "…", "—", "·")
    )

    override fun onCreate() {
        super.onCreate()
        database = DictionaryDatabase(this)
        pinyinEngine = PinyinEngine(database)
        englishEngine = EnglishEngine(database)
        preferencesManager = PreferencesManager(this)
        clipboardManager = ClipboardManager(this)
        feedbackManager = FeedbackManager(this)

        serviceScope.launch {
            preferencesManager.privacyMode.collect { isPrivacyMode = it }
        }
        serviceScope.launch {
            preferencesManager.vibrationStrength.collect { vibrationStrength = it }
        }
        serviceScope.launch {
            preferencesManager.soundVolume.collect { soundVolume = it }
        }
    }

    override fun onCreateInputView(): View {
        val keyboardView = layoutInflater.inflate(com.personal.ime.R.layout.keyboard_view, null)
        keyboardContainer = keyboardView.findViewById(com.personal.ime.R.id.keyboardContainer)
        candidateLayout = keyboardView.findViewById(com.personal.ime.R.id.candidateLayout)
        rebuildKeyboard()
        return keyboardView
    }

    // ==================== 键盘构建 ====================

    private fun rebuildKeyboard() {
        val container = keyboardContainer ?: return
        container.removeAllViews()
        qwertyLetterKeys.clear()
        shiftKey = null
        when {
            inputMode == InputMode.ENGLISH_QWERTY -> buildQwertyKeyboard(container)
            keyboardPage == KeyboardPage.SYMBOL -> buildSymbolKeyboard(container)
            keyboardPage == KeyboardPage.NUMBER -> buildNumberKeyboard(container)
            else -> buildT9Keyboard(container)
        }
    }

    /**
     * T9 键盘布局（参考主流输入法）：
     * 左列：, / 。 ?    中3列：@# ABC DEF / GHI JKL MNO / PQRS TUV WXYZ    右列：⌫ 重输 换行
     * 底行：符号  123  空格  中/英
     */
    private fun buildT9Keyboard(container: LinearLayout) {
        // Row 1: , | @#  ABC  DEF | ⌫
        val row1 = createKeyboardRow()
        row1.addView(createNarrowKey("，", { commitPlainText("，") }))
        row1.addView(createT9Key("@#", '1') { showSymbols() })
        row1.addView(createT9Key("ABC", '2', ::handleT9Key))
        row1.addView(createT9Key("DEF", '3', ::handleT9Key))
        row1.addView(createSpecialKey("⌫", ::handleDelete))
        container.addView(row1)

        // Row 2: / | GHI  JKL  MNO | 重输
        val row2 = createKeyboardRow()
        row2.addView(createNarrowKey("/", { commitPlainText("/") }))
        row2.addView(createT9Key("GHI", '4', ::handleT9Key))
        row2.addView(createT9Key("JKL", '5', ::handleT9Key))
        row2.addView(createT9Key("MNO", '6', ::handleT9Key))
        row2.addView(createSpecialKey("重输", ::clearInput))
        container.addView(row2)

        // Row 3: 。 | PQRS  TUV  WXYZ | 换行(跨2行)
        val row3 = createKeyboardRow()
        row3.addView(createNarrowKey("。", { commitPlainText("。") }))
        row3.addView(createT9Key("PQRS", '7', ::handleT9Key))
        row3.addView(createT9Key("TUV", '8', ::handleT9Key))
        row3.addView(createT9Key("WXYZ", '9', ::handleT9Key))
        row3.addView(createSpecialKey("换行", ::handleEnter))
        container.addView(row3)

        // Row 4: ? | 0 | 空格 | 中/英
        val row4 = createKeyboardRow()
        row4.addView(createNarrowKey("？", { commitPlainText("？") }))
        row4.addView(createT9Key("0", '0', ::handleT9Key))
        row4.addView(createSpecialKey("空格", { handleSpace() }, weight = 2f))
        row4.addView(createSpecialKey(modeLabel(), ::toggleInputMode))
        container.addView(row4)

        // Row 5: 符号  123  (底部快捷入口)
        val row5 = createKeyboardRow()
        row5.addView(createSpecialKey("符号", ::showSymbols, weight = 1f))
        row5.addView(createSpecialKey("123", ::showNumbers, weight = 1f))
        // 占位填充剩余宽度
        row5.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 2f)
        })
        container.addView(row5)
    }

    /**
     * 符号键盘布局：
     * 左列：, / 。 ?    中3列：符号页    右列：⌫ 翻页 返回
     * 底行：123  空格  中/英
     */
    private fun buildSymbolKeyboard(container: LinearLayout) {
        val symbols = symbolPages[symbolPage - 1]
        var idx = 0

        for (row in 0 until 3) {
            val rowView = createKeyboardRow()
            // 左列常用标点
            val leftLabels = arrayOf("，", "/", "。", "？")
            rowView.addView(createNarrowKey(leftLabels[row], { commitPlainText(leftLabels[row]) }))
            // 中间 3 列符号
            for (col in 0 until 3) {
                val sym = if (idx < symbols.size) symbols[idx] else ""
                rowView.addView(createSymbolKey(sym, { commitPlainText(sym) }))
                idx++
            }
            // 右列
            when (row) {
                0 -> rowView.addView(createSpecialKey("", ::handleDelete))
                1 -> rowView.addView(createSpecialKey(if (symbolPage == 1) "2/2" else "1/2", ::toggleSymbolPage))
                2 -> rowView.addView(createSpecialKey("返回", ::backToT9))
            }
            container.addView(rowView)
        }

        // 底行
        val bottomRow = createKeyboardRow()
        bottomRow.addView(createSpecialKey("123", ::showNumbers))
        bottomRow.addView(createSpecialKey("空格", { handleSpace() }, weight = 2f))
        bottomRow.addView(createSpecialKey(modeLabel(), ::toggleInputMode))
        container.addView(bottomRow)
    }

    /**
     * 数字键盘布局（4 列）：
     * 1  2  3  ⌫
     * 4  5  6  重输
     * 7  8  9  换行(跨2行)
     * 0  #  *  返回
     * 底行：符号  空格  中/英
     */
    private fun buildNumberKeyboard(container: LinearLayout) {
        val rows = arrayOf(
            arrayOf("1", "2", "3"),
            arrayOf("4", "5", "6"),
            arrayOf("7", "8", "9"),
            arrayOf("0", "#", "*")
        )
        for (row in rows.indices) {
            val rowView = createKeyboardRow()
            for (col in rows[row].indices) {
                val label = rows[row][col]
                rowView.addView(createNumberKey(label, { commitPlainText(label) }))
            }
            when (row) {
                0 -> rowView.addView(createSpecialKey("⌫", ::handleDelete))
                1 -> rowView.addView(createSpecialKey("重输", ::clearInput))
                2 -> rowView.addView(createSpecialKey("换行", ::handleEnter))
                3 -> rowView.addView(createSpecialKey("返回", ::backToT9))
            }
            container.addView(rowView)
        }

        val bottomRow = createKeyboardRow()
        bottomRow.addView(createSpecialKey("符号", ::showSymbols))
        bottomRow.addView(createSpecialKey("空格", { handleSpace() }, weight = 2f))
        bottomRow.addView(createSpecialKey(modeLabel(), ::toggleInputMode))
        container.addView(bottomRow)
    }

    /** 英文 26 键 QWERTY 布局 */
    private fun buildQwertyKeyboard(container: LinearLayout) {
        val row1 = createKeyboardRow()
        "qwertyuiop".forEach { row1.addView(createLetterKey(it)) }
        container.addView(row1)

        val row2 = createKeyboardRow()
        "asdfghjkl".forEach { row2.addView(createLetterKey(it)) }
        container.addView(row2)

        val row3 = createKeyboardRow()
        val shiftButton = createSpecialKey("⇧", ::toggleShift)
        shiftKey = shiftButton
        row3.addView(shiftButton)
        "zxcvbnm".forEach { row3.addView(createLetterKey(it)) }
        row3.addView(createSpecialKey("⌫", ::handleDelete))
        container.addView(row3)

        val row4 = createKeyboardRow()
        row4.addView(createSpecialKey("中文", ::toggleInputMode))
        row4.addView(createSpecialKey(",", { commitPlainText(",") }))
        row4.addView(createSpecialKey("空格", { handleSpace() }, weight = 2.5f))
        row4.addView(createSpecialKey(".", { commitPlainText(".") }))
        row4.addView(createSpecialKey("↵", ::handleEnter))
        container.addView(row4)
    }

    // ==================== 按键工厂 ====================

    private fun createKeyboardRow(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { weight = 1f }
        }
    }

    private fun createNarrowKey(label: String, onClick: () -> Unit): Button {
        return Button(this).apply {
            text = label
            textSize = 16f
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 0.6f).apply {
                setMargins(2, 2, 2, 2)
            }
            setOnClickListener { onClick() }
        }
    }

    private fun createT9Key(label: String, digit: Char, onT9Click: (Char) -> Unit): Button {
        return Button(this).apply {
            text = label
            textSize = 14f
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply {
                setMargins(2, 2, 2, 2)
            }
            setOnClickListener { onT9Click(digit) }
        }
    }

    private fun createSymbolKey(label: String, onClick: () -> Unit): Button {
        return Button(this).apply {
            text = label
            textSize = 16f
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply {
                setMargins(2, 2, 2, 2)
            }
            setOnClickListener { onClick() }
        }
    }

    private fun createNumberKey(label: String, onClick: () -> Unit): Button {
        return Button(this).apply {
            text = label
            textSize = 18f
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply {
                setMargins(2, 2, 2, 2)
            }
            setOnClickListener { onClick() }
        }
    }

    private fun createLetterKey(letter: Char): Button {
        return Button(this).apply {
            tag = letter
            text = letter.toString()
            textSize = 18f
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply {
                setMargins(2, 2, 2, 2)
            }
            setOnClickListener { handleLetterKey(letter) }
            qwertyLetterKeys.add(this)
        }
    }

    private fun createSpecialKey(label: String, onClick: () -> Unit, weight: Float = 1f): Button {
        return Button(this).apply {
            text = label
            textSize = 14f
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, weight).apply {
                setMargins(2, 2, 2, 2)
            }
            setOnClickListener { onClick() }
        }
    }

    // ==================== 输入处理 ====================

    private fun handleT9Key(digit: Char) {
        feedbackManager.vibrate(vibrationStrength)
        feedbackManager.playSound(soundVolume)
        currentInput += digit
        updateCandidates()
    }

    private fun handleLetterKey(letter: Char) {
        feedbackManager.vibrate(vibrationStrength)
        feedbackManager.playSound(soundVolume)
        currentInput += if (isShifted) letter.uppercaseChar() else letter
        if (isShifted) {
            isShifted = false
            updateShiftState()
        }
        updateComposingText()
        updateCandidates()
    }

    private fun toggleShift() {
        feedbackManager.vibrate(vibrationStrength)
        feedbackManager.playSound(soundVolume)
        isShifted = !isShifted
        updateShiftState()
    }

    private fun updateShiftState() {
        shiftKey?.text = if (isShifted) "⇧✓" else "⇧"
        qwertyLetterKeys.forEach { button ->
            val letter = button.tag as Char
            button.text = if (isShifted) letter.uppercaseChar().toString() else letter.toString()
        }
    }

    /** 清空当前输入（重输） */
    private fun clearInput() {
        feedbackManager.vibrate(vibrationStrength)
        feedbackManager.playSound(soundVolume)
        currentInput = ""
        updateCandidates()
    }

    /** 中/英切换 */
    private fun toggleInputMode() {
        feedbackManager.vibrate(vibrationStrength)
        feedbackManager.playSound(soundVolume)
        if (currentInput.isNotEmpty()) {
            currentInput = ""
            currentInputConnection?.finishComposingText()
        }
        inputMode = if (inputMode == InputMode.CHINESE_T9) InputMode.ENGLISH_QWERTY else InputMode.CHINESE_T9
        keyboardPage = KeyboardPage.T9
        isShifted = false
        rebuildKeyboard()
        updateCandidates()
    }

    /** 切换到符号键盘 */
    private fun showSymbols() {
        feedbackManager.vibrate(vibrationStrength)
        keyboardPage = KeyboardPage.SYMBOL
        symbolPage = 1
        rebuildKeyboard()
    }

    /** 切换到数字键盘 */
    private fun showNumbers() {
        feedbackManager.vibrate(vibrationStrength)
        keyboardPage = KeyboardPage.NUMBER
        rebuildKeyboard()
    }

    /** 从符号/数字键盘返回 T9 */
    private fun backToT9() {
        feedbackManager.vibrate(vibrationStrength)
        keyboardPage = KeyboardPage.T9
        rebuildKeyboard()
    }

    /** 符号键盘翻页 */
    private fun toggleSymbolPage() {
        feedbackManager.vibrate(vibrationStrength)
        symbolPage = if (symbolPage == 1) 2 else 1
        rebuildKeyboard()
    }

    private fun handleDelete() {
        feedbackManager.vibrate(vibrationStrength)
        feedbackManager.playSound(soundVolume)
        if (currentInput.isNotEmpty()) {
            currentInput = currentInput.dropLast(1)
            if (currentInput.isEmpty()) {
                currentInputConnection?.finishComposingText()
            } else {
                updateComposingText()
            }
            updateCandidates()
        } else {
            currentInputConnection?.deleteSurroundingText(1, 0)
        }
    }

    private fun handleSpace() {
        feedbackManager.vibrate(vibrationStrength)
        feedbackManager.playSound(soundVolume)
        if (currentInput.isEmpty()) {
            currentInputConnection?.commitText(" ", 1)
            return
        }
        if (inputMode == InputMode.CHINESE_T9) {
            val candidates = pinyinEngine.inputT9(currentInput)
            if (candidates.isNotEmpty()) {
                commitCandidate(candidates[0])
            } else {
                commitTextDirectly(currentInput)
            }
        } else {
            val predictions = englishEngine.predict(currentInput)
            val isKnownWord = predictions.any { it.equals(currentInput, ignoreCase = true) }
            val commit = if (isKnownWord || predictions.isEmpty()) currentInput else predictions.first()
            commitEnglishText(applyInputCase(commit))
        }
    }

    private fun handleEnter() {
        feedbackManager.vibrate(vibrationStrength)
        feedbackManager.playSound(soundVolume)
        if (currentInput.isNotEmpty()) {
            commitTextDirectly(currentInput)
        } else {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
        }
    }

    /** 直接上屏标点/数字等文本 */
    private fun commitPlainText(text: String) {
        feedbackManager.vibrate(vibrationStrength)
        feedbackManager.playSound(soundVolume)
        if (currentInput.isNotEmpty()) {
            if (inputMode == InputMode.CHINESE_T9) {
                commitTextDirectly(currentInput)
            } else {
                commitEnglishText(currentInput)
            }
        }
        currentInputConnection?.commitText(text, 1)
    }

    // ==================== 候选与上屏 ====================

    private fun updateComposingText() {
        currentInputConnection?.setComposingText(currentInput, 1)
    }

    private fun updateCandidates() {
        val candidatesView = candidateLayout ?: return
        candidatesView.removeAllViews()
        if (currentInput.isEmpty()) return

        if (inputMode == InputMode.CHINESE_T9) {
            pinyinEngine.inputT9(currentInput).take(10).forEach { candidate ->
                candidatesView.addView(
                    createCandidateView(candidate.text) { commitCandidate(candidate) }
                )
            }
        } else {
            englishEngine.predict(currentInput).take(10).forEach { word ->
                val display = applyInputCase(word)
                candidatesView.addView(
                    createCandidateView(display) { commitEnglishText(display) }
                )
            }
        }
    }

    private fun createCandidateView(text: String, onClick: () -> Unit): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 16f
            setPadding(16, 8, 16, 8)
            setOnClickListener { onClick() }
        }
    }

    private fun applyInputCase(word: String): String {
        val first = currentInput.firstOrNull() ?: return word
        return if (first.isUpperCase()) word.replaceFirstChar { it.uppercase() } else word
    }

    private fun commitTextDirectly(text: String) {
        currentInputConnection?.commitText(text, 1)
        currentInput = ""
        updateCandidates()
    }

    private fun commitCandidate(candidate: PinyinEngine.Candidate) {
        currentInputConnection?.commitText(candidate.text, 1)
        if (!isPrivacyMode) {
            pinyinEngine.incrementFrequency(currentInput, candidate.text)
        }
        currentInput = ""
        updateCandidates()
    }

    private fun commitEnglishText(text: String) {
        currentInputConnection?.commitText(text, 1)
        if (!isPrivacyMode && text.length > 1) {
            englishEngine.learn(text)
        }
        currentInput = ""
        updateCandidates()
    }

    /** 根据当前输入模式返回切换键标签 */
    private fun modeLabel(): String =
        if (inputMode == InputMode.CHINESE_T9) "中/英" else "英/中"

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        feedbackManager.release()
        database.close()
    }
}
