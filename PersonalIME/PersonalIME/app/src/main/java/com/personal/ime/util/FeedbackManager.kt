package com.personal.ime.util

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

class FeedbackManager(private val context: Context) {

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        vibratorManager?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    fun vibrate(strength: Int) {
        if (strength <= 0) return

        val duration = (strength * 0.5).toLong().coerceIn(5, 50)
        vibrator?.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    fun release() {
        // 仅保留振动反馈（按键音已移除），无需释放资源；保留接口避免调用方改动
    }
}
