package com.example.util

import android.media.AudioManager
import android.media.ToneGenerator

object FeedbackSoundManager {
    fun play(correct: Boolean) {
        val tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 70)
        tone.startTone(
            if (correct) ToneGenerator.TONE_PROP_ACK else ToneGenerator.TONE_PROP_NACK,
            180
        )
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ tone.release() }, 260L)
    }
}
