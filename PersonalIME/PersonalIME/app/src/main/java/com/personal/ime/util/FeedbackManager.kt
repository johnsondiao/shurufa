package com.personal.ime.util

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
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

    private val toneGenerator: ToneGenerator? = ToneGenerator(AudioManager.STREAM_SYSTEM, 50)

    fun vibrate(strength: Int) {
        if (strength <= 0) return

        val duration = (strength * 0.5).toLong().coerceIn(5, 50)
        vibrator?.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    fun playSound(volume: Int) {
        if (volume <= 0) return

        toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 50)
    }

    fun release() {
        toneGenerator?.release()
    }
}
