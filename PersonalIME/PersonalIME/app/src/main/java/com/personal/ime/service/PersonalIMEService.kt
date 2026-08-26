package com.personal.ime.service

import android.inputmethodservice.InputMethodService
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.personal.ime.data.ClipboardManager
import com.personal.ime.data.DictionaryDatabase
import com.personal.ime.data.PreferencesManager
import com.personal.ime.engine.PinyinEngine
import com.personal.ime.util.FeedbackManager
import kotlinx.coroutines.*

class PersonalIMEService : InputMethodService() {

    private lateinit var database: DictionaryDatabase
    private lateinit var pinyinEngine: PinyinEngine
    private lateinit var preferencesManager: PreferencesManager
    private lateinit var clipboardManager: ClipboardManager
    private lateinit var feedbackManager: FeedbackManager

    private var currentInput = ""
    private var isPrivacyMode = false
    private var vibrationStrength = 30
    private var soundVolume = 20

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        database = DictionaryDatabase(this)
        pinyinEngine = PinyinEngine(database)
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
        val keyboardContainer = keyboardView.findViewById<LinearLayout>(com.personal.ime.R.id.keyboardContainer)

        // Build T9 keyboard
        buildT9Keyboard(keyboardContainer)

        return keyboardView
    }

    private fun buildT9Keyboard(container: LinearLayout) {
        container.removeAllViews()

        // Row 1: 1 (.), 2 (abc), 3 (def)
        val row1 = createKeyboardRow()
        row1.addView(createT9Key("1", ".", listOf(".", ",", "!", "?")))
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

        // Row 4: 符号, 0 (+), 删除
        val row4 = createKeyboardRow()
        row4.addView(createSpecialKey("符号", ::showSymbols))
        row4.addView(createT9Key("0", "+", listOf('0')))
        row4.addView(createSpecialKey("⌫", ::handleDelete))
        container.addView(row4)

        // Row 5: 空格, 回车
        val row5 = createKeyboardRow()
        row5.addView(createSpecialKey("空格", { handleSpace() }, weight = 2f))
        row5.addView(createSpecialKey("↵", ::handleEnter))
        container.addView(row5)
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

    private fun createSpecialKey(label: String, onClick: () -> Unit, weight: Float = 1f): View {
        return Button(this).apply {
            text = label
            textSize = 14f
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, weight).apply {
                setMargins(2, 2, 2, 2)
            }
            setOnClickListener { onClick() }
        }
    }

    private fun handleT9Key(digit: String) {
        feedbackManager.vibrate(vibrationStrength)
        feedbackManager.playSound(soundVolume)

        currentInput += digit
        updateCandidates()
    }

    private fun handleDelete() {
        feedbackManager.vibrate(vibrationStrength)
        feedbackManager.playSound(soundVolume)

        if (currentInput.isNotEmpty()) {
            currentInput = currentInput.dropLast(1)
            updateCandidates()
        } else {
            // Delete last character from input connection
            currentInputConnection?.deleteSurroundingText(1, 0)
        }
    }

    private fun handleSpace() {
        feedbackManager.vibrate(vibrationStrength)
        feedbackManager.playSound(soundVolume)

        if (currentInput.isNotEmpty()) {
            // Commit first candidate or the input itself
            val candidates = pinyinEngine.inputT9(currentInput)
            if (candidates.isNotEmpty()) {
                commitCandidate(candidates[0])
            } else {
                currentInputConnection?.commitText(currentInput, 1)
            }
            currentInput = ""
            updateCandidates()
        } else {
            currentInputConnection?.commitText(" ", 1)
        }
    }

    private fun handleEnter() {
        feedbackManager.vibrate(vibrationStrength)
        feedbackManager.playSound(soundVolume)

        if (currentInput.isNotEmpty()) {
            currentInputConnection?.commitText(currentInput, 1)
            currentInput = ""
            updateCandidates()
        } else {
            sendKeyDownUpKey(android.view.KeyEvent.KEYCODE_ENTER)
        }
    }

    private fun showSymbols() {
        // TODO: Show symbol keyboard
    }

    private fun updateCandidates() {
        val candidatesView = window.window?.decorView?.findViewById<LinearLayout>(com.personal.ime.R.id.candidateLayout)
        candidatesView?.removeAllViews()

        if (currentInput.isEmpty()) return

        val candidates = pinyinEngine.inputT9(currentInput)
        candidates.take(10).forEach { candidate ->
            val candidateView = TextView(this).apply {
                text = candidate.text
                textSize = 16f
                setPadding(16, 8, 16, 8)
                setOnClickListener {
                    commitCandidate(candidate)
                }
            }
            candidatesView?.addView(candidateView)
        }
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

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        feedbackManager.release()
        database.close()
    }
}
