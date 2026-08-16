package com.example.util

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

class TTSManager(context: Context) : TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = TextToSpeech(context.applicationContext, this)
    private var isInitialized = false
    private var currentRate = 1.0f

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.CHINA)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts?.setLanguage(Locale.CHINESE)
            }
            tts?.setSpeechRate(currentRate)
            isInitialized = true
        } else {
            Log.e("TTSManager", "TextToSpeech Initialization failed")
        }
    }

    fun setSpeechRate(rate: Int) {
        // rate ranges from 100 to 200 (100 = 1.0f, 200 = 2.0f)
        val floatRate = (rate / 150f).coerceIn(0.5f, 2.0f)
        currentRate = floatRate
        if (isInitialized) {
            tts?.setSpeechRate(floatRate)
        }
    }

    fun speak(text: String) {
        if (isInitialized && text.isNotBlank()) {
            tts?.stop()
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "MathAdventureTTS")
        }
    }

    fun stopSpeaking() {
        stop()
    }

    fun stop() {
        if (isInitialized) {
            tts?.stop()
        }
    }

    fun isSpeaking(): Boolean {
        return tts?.isSpeaking == true
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}
