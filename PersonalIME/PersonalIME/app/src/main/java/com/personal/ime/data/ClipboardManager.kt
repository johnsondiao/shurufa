package com.personal.ime.data

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class ClipboardManager(context: Context) {

    private val prefs = context.getSharedPreferences("clipboard_history", Context.MODE_PRIVATE)
    private val _clipboardItems = MutableStateFlow(loadClipboardHistory())
    val clipboardItems: Flow<List<String>> = _clipboardItems.asStateFlow()

    companion object {
        private const val MAX_ITEMS = 20
        private const val KEY_CLIPBOARD_HISTORY = "clipboard_history"
    }

    private fun loadClipboardHistory(): List<String> {
        return prefs.getString(KEY_CLIPBOARD_HISTORY, null)
            ?.split("|||")
            ?.filter { it.isNotEmpty() }
            ?: emptyList()
    }

    private fun saveClipboardHistory(items: List<String>) {
        prefs.edit().putString(KEY_CLIPBOARD_HISTORY, items.joinToString("|||")).apply()
    }

    fun addItem(text: String) {
        val currentItems = _clipboardItems.value.toMutableList()
        currentItems.remove(text) // Remove duplicate if exists
        currentItems.add(0, text) // Add to top

        // Keep only MAX_ITEMS
        while (currentItems.size > MAX_ITEMS) {
            currentItems.removeAt(currentItems.size - 1)
        }

        _clipboardItems.value = currentItems
        saveClipboardHistory(currentItems)
    }

    fun removeItem(text: String) {
        val currentItems = _clipboardItems.value.toMutableList()
        currentItems.remove(text)
        _clipboardItems.value = currentItems
        saveClipboardHistory(currentItems)
    }

    fun clearAll() {
        _clipboardItems.value = emptyList()
        saveClipboardHistory(emptyList())
    }

    fun getAllItems(): List<String> = _clipboardItems.value
}
