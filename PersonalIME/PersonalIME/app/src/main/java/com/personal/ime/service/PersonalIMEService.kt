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

    /** 中文 9 键 / 英文 26 键 */
    private enum class InputMode { CHINESE_T9, ENGLISH_QWERTY }

    private lateinit var database: DictionaryDatabase
    private lateinit var pinyinEngine: PinyinEngine
    private lateinit var englishEngine: EnglishEngine
    private lateinit var preferencesManager: PreferencesManager
    private lateinit var clipboardManager: ClipboardManager
    private lateinit var feedbackManager: FeedbackManager

    private var currentInput = ""
    private var inputMode = InputMode.CHINESE_T9
    private var isShifted = false
    private var isPrivacyMode = false
    private var vibrationStrength = 30
    private var soundVolume = 20

    private var keyboardContainer: LinearLayout? = null
    private var candidateLayout: LinearLayout? = null
    private var shiftKey: Button? = null
    private val qwertyLetterKeys = mutableListOf<Button>()

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        database = DictionaryDatabase(this)
        pinyinEngine = PinyinEngine(database)
        englishEngine = EnglishEngine(database)
        preferencesManager = PreferencesManager(this)
        clipboardManager = ClipboardManager(this)
        feedbackManager = FeedbackManager(this)

        // Load preferences
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

    // ---------- 键盘构建 ----------

    private fun rebuildKeyboard() {
        val container = keyboardContainer ?: return
        container.removeAllViews()
        qwertyLetterKeys.clear()
        shiftKey = null
        if (inputMode == InputMode.CHINESE_T9) {
            buildT9Keyboard(container)
        } else {
            buildQwertyKeyboard(container)
        }
    }

    private fun buildT9Keyboard(container: LinearLayout) {
        // Row 1: 1 (.), 2 (abc), 3 (def)
        val row1 = createKeyboardRow()
        row1.addView(createT9Key("1", ".", listOf('.', ',', '!', '?')))
        row1.addView(createT9Key("2", "abc", listOf('2')))
        row1.addView(createT9Key("3", "def", listOf('3')))
        container.addView(row1)

        // Row 2: 4 (ghi), 5 (jkl), 6 (mno)
        val row2 = createKeyboardRow()
        row2.addView(createT9Key("4", "ghi", listOf('4')))
        row2.addView(createT9Key("5", "jkl", listOf('5')))
        row2.addView(createT9Key("6", "mno", listOf('6')))
        container.addView(row2)

        // Row 3: 7 (pqrs), 8 (tuv), 9 (wxyz)
        val row3 = createKeyboardRow()
        row3.addView(createT9Key("7", "pqrs", listOf('7')))
        row3.addView(createT9Key("8", "tuv", listOf('8')))
        row3.addView(createT9Key("9", "wxyz", listOf('9')))
        container.addView(row3)

        // Row 4: 中英切换, 0 (+), 删除
        val row4 = createKeyboardRow()
        row4.addView(createSpecialKey("英文", ::toggleInputMode))
        row4.addView(createT9Key("0", "+", listOf('0')))
        row4.addView(createSpecialKey("⌫", ::handleDelete))
        container.addView(row4)

        // Row 5: 空格, 回车
        val row5 = createKeyboardRow()
        row5.addView(createSpecialKey("空格", { handleSpace() }, weight = 2f))
        row5.addView(createSpecialKey("↵", ::handleEnter))
        container.addView(row5)
    }

    private fun buildQwertyKeyboard(container: LinearLayout) {
        // Row 1: q w e r t y u i o p
        val row1 = createKeyboardRow()
        "qwertyuiop".forEach { row1.addView(createLetterKey(it)) }
        container.addView(row1)

        // Row 2: a s d f g h j k l
        val row2 = createKeyboardRow()
        "asdfghjkl".forEach { row2.addView(createLetterKey(it)) }
        container.addView(row2)

        // Row 3: ⇧, z x c v b n m, ⌫
        val row3 = createKeyboardRow()
        val shiftButton = createSpecialKey("⇧", ::toggleShift)
        shiftKey = shiftButton
        row3.addView(shiftButton)
        "zxcvbnm".forEach { row3.addView(createLetterKey(it)) }
        row3.addView(createSpecialKey("⌫", ::handleDelete))
        container.addView(row3)

        // Row 4: 中英切换, 逗号, 空格, 句号, 回车
        val row4 = createKeyboardRow()
        row4.addView(createSpecialKey("中文", ::toggleInputMode))
        row4.addView(createSpecialKey(",", { commitPlainText(",") }))
        row4.addView(createSpecialKey("空格", { handleSpace() }, weight = 2.5f))
        row4.addView(createSpecialKey(".", { commitPlainText(".") }))
        row4.addView(createSpecialKey("↵", ::handleEnter))
        container.addView(row4)
    }

    private fun createKeyboardRow(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                weight = 1f
            }
        }
    }

    @Suppress("UNUSED_PARAMETER")
    private fun createT9Key(digit: String, letters: String, digits: List<Char>): View {
        return Button(this).apply {
            text = "$digit\n$letters"
            textSize = 14f
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply {
                setMargins(2, 2, 2, 2)
            }
            setOnClickListener {
                handleT9Key(digit)
            }
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
            setOnClickListener {
                handleLetterKey(letter)
            }
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

    // ---------- 输入处理 ----------

    private fun handleT9Key(digit: String) {
        feedbackManager.vibrate(vibrationStrength)
        feedbackManager.playSound(soundVolume)

        currentInput += digit
        updateComposingText()
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

    private fun toggleInputMode() {
        feedbackManager.vibrate(vibrationStrength)
        feedbackManager.playSound(soundVolume)

        // 切换键盘时放弃未上屏的输入
        if (currentInput.isNotEmpty()) {
            currentInput = ""
            currentInputConnection?.finishComposingText()
        }
        inputMode = if (inputMode == InputMode.CHINESE_T9) {
            InputMode.ENGLISH_QWERTY
        } else {
            InputMode.CHINESE_T9
        }
        isShifted = false
        rebuildKeyboard()
        updateCandidates()
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
            // Delete last character from input connection
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
            // 中文：空格上屏首选候选
            val candidates = pinyinEngine.inputT9(currentInput)
            if (candidates.isNotEmpty()) {
                commitCandidate(candidates[0])
            } else {
                commitTextDirectly(currentInput)
            }
        } else {
            // 英文：已输入的是完整单词则原样上屏，否则采用首选预测自动补全
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

    /** 输入英文时直接键入标点：先上屏当前输入，再补标点 */
    private fun commitPlainText(text: String) {
        feedbackManager.vibrate(vibrationStrength)
        feedbackManager.playSound(soundVolume)

        if (currentInput.isNotEmpty()) {
            commitEnglishText(currentInput)
        }
        currentInputConnection?.commitText(text, 1)
    }

    private fun showSymbols() {
        // TODO: Show symbol keyboard
    }

    // ---------- 候选与上屏 ----------

    /** 将未上屏内容以 composing 形式显示在输入框中 */
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

    /** 保留用户输入的大小写风格（首字母大写则预测词也大写） */
    private fun applyInputCase(word: String): String {
        val first = currentInput.firstOrNull() ?: return word
        return if (first.isUpperCase()) word.replaceFirstChar { it.uppercase() } else word
    }

    private fun commitTextDirectly(text: String) {
        // commitText 会自动替换 composing 区域
        currentInputConnection?.commitText(text, 1)
        currentInput = ""
        updateCandidates()
    }

    private fun commitCandidate(candidate: PinyinEngine.Candidate) {
        currentInputConnection?.commitText(candidate.text, 1)

        // Learn from user input (if not in privacy mode)
        if (!isPrivacyMode) {
            pinyinEngine.incrementFrequency(currentInput, candidate.text)
        }

        currentInput = ""
        updateCandidates()
    }

    private fun commitEnglishText(text: String) {
        currentInputConnection?.commitText(text, 1)

        // Learn from user input (if not in privacy mode)
        if (!isPrivacyMode && text.length > 1) {
            englishEngine.learn(text)
        }

        currentInput = ""
        updateCandidates()
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        feedbackManager.release()
        database.close()
    }
}
