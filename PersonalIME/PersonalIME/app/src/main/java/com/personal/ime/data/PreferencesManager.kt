package com.personal.ime.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class PreferencesManager(private val context: Context) {

    companion object {
        val KEYBOARD_HEIGHT = intPreferencesKey("keyboard_height")
        val KEY_SIZE = intPreferencesKey("key_size")
        val KEYBOARD_OFFSET = intPreferencesKey("keyboard_offset")
        val VIBRATION_STRENGTH = intPreferencesKey("vibration_strength")
        val SOUND_VOLUME = intPreferencesKey("sound_volume")
        val THEME = stringPreferencesKey("theme")
        val PRIVACY_MODE = booleanPreferencesKey("privacy_mode")
        val CUSTOM_BG_PATH = stringPreferencesKey("custom_bg_path")
    }

    val keyboardHeight: Flow<Int> = context.dataStore.data.map { it[KEYBOARD_HEIGHT] ?: 50 }
    val keySize: Flow<Int> = context.dataStore.data.map { it[KEY_SIZE] ?: 50 }
    val keyboardOffset: Flow<Int> = context.dataStore.data.map { it[KEYBOARD_OFFSET] ?: 50 }
    val vibrationStrength: Flow<Int> = context.dataStore.data.map { it[VIBRATION_STRENGTH] ?: 30 }
    val soundVolume: Flow<Int> = context.dataStore.data.map { it[SOUND_VOLUME] ?: 20 }
    val theme: Flow<String> = context.dataStore.data.map { it[THEME] ?: "light" }
    val privacyMode: Flow<Boolean> = context.dataStore.data.map { it[PRIVACY_MODE] ?: false }
    val customBgPath: Flow<String?> = context.dataStore.data.map { it[CUSTOM_BG_PATH] }

    suspend fun setKeyboardHeight(value: Int) {
        context.dataStore.edit { it[KEYBOARD_HEIGHT] = value }
    }

    suspend fun setKeySize(value: Int) {
        context.dataStore.edit { it[KEY_SIZE] = value }
    }

    suspend fun setKeyboardOffset(value: Int) {
        context.dataStore.edit { it[KEYBOARD_OFFSET] = value }
    }

    suspend fun setVibrationStrength(value: Int) {
        context.dataStore.edit { it[VIBRATION_STRENGTH] = value }
    }

    suspend fun setSoundVolume(value: Int) {
        context.dataStore.edit { it[SOUND_VOLUME] = value }
    }

    suspend fun setTheme(value: String) {
        context.dataStore.edit { it[THEME] = value }
    }

    suspend fun setPrivacyMode(value: Boolean) {
        context.dataStore.edit { it[PRIVACY_MODE] = value }
    }

    suspend fun setCustomBgPath(value: String?) {
        context.dataStore.edit {
            if (value != null) it[CUSTOM_BG_PATH] = value
            else it.remove(CUSTOM_BG_PATH)
        }
    }
}
