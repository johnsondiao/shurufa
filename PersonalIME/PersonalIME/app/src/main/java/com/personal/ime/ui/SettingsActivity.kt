package com.personal.ime.ui

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.personal.ime.R
import com.personal.ime.data.PreferencesManager
import com.personal.ime.databinding.ActivitySettingsBinding
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var preferencesManager: PreferencesManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        title = getString(R.string.settings_title)
        preferencesManager = PreferencesManager(this)

        setupButtons()
        setupSeekBars()
        setupThemeSpinner()
        setupPrivacySwitch()
        loadPreferences()
    }

    private fun setupButtons() {
        binding.btnEnableIME.setOnClickListener {
            val intent = Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)
            startActivity(intent)
        }

        binding.btnSelectIME.setOnClickListener {
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showInputMethodPicker()
        }

        binding.btnCustomBg.setOnClickListener {
            // TODO: Implement image picker for custom background
        }
    }

    private fun setupSeekBars() {
        binding.seekbarHeight.setOnSeekBarChangeListener(
            object : android.widget.SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        lifecycleScope.launch {
                            preferencesManager.setKeyboardHeight(progress)
                        }
                    }
                }
                override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {}
            }
        )

        binding.seekbarKeySize.setOnSeekBarChangeListener(
            object : android.widget.SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        lifecycleScope.launch {
                            preferencesManager.setKeySize(progress)
                        }
                    }
                }
                override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {}
            }
        )

        binding.seekbarOffset.setOnSeekBarChangeListener(
            object : android.widget.SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        lifecycleScope.launch {
                            preferencesManager.setKeyboardOffset(progress)
                        }
                    }
                }
                override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {}
            }
        )

        binding.seekbarVibration.setOnSeekBarChangeListener(
            object : android.widget.SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        lifecycleScope.launch {
                            preferencesManager.setVibrationStrength(progress)
                        }
                    }
                }
                override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {}
            }
        )

    }

    private fun setupThemeSpinner() {
        val themes = arrayOf(
            getString(R.string.theme_light),
            getString(R.string.theme_dark),
            getString(R.string.theme_blue),
            getString(R.string.theme_green),
            getString(R.string.theme_purple)
        )
        val themeValues = arrayOf("light", "dark", "blue", "green", "purple")

        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, themes)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerTheme.adapter = adapter

        binding.spinnerTheme.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                lifecycleScope.launch {
                    preferencesManager.setTheme(themeValues[position])
                }
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }
    }

    private fun setupPrivacySwitch() {
        binding.switchPrivacy.setOnCheckedChangeListener { _, isChecked ->
            lifecycleScope.launch {
                preferencesManager.setPrivacyMode(isChecked)
            }
        }
    }

    private fun loadPreferences() {
        lifecycleScope.launch {
            binding.seekbarHeight.progress = preferencesManager.keyboardHeight.first()
            binding.seekbarKeySize.progress = preferencesManager.keySize.first()
            binding.seekbarOffset.progress = preferencesManager.keyboardOffset.first()
            binding.seekbarVibration.progress = preferencesManager.vibrationStrength.first()
            binding.switchPrivacy.isChecked = preferencesManager.privacyMode.first()

            val theme = preferencesManager.theme.first()
            val themeIndex = when (theme) {
                "light" -> 0
                "dark" -> 1
                "blue" -> 2
                "green" -> 3
                "purple" -> 4
                else -> 0
            }
            binding.spinnerTheme.setSelection(themeIndex)
        }
    }
}
