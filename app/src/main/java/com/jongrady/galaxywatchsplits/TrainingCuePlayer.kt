package com.jongrady.traincue

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import java.util.Locale

internal class TrainingCuePlayer(context: Context) {
    private val appContext = context.applicationContext
    private val tone = ToneGenerator(AudioManager.STREAM_MUSIC, 80)
    private var ttsReady = false
    private val tts = TextToSpeech(appContext) { status -> ttsReady = status == TextToSpeech.SUCCESS }

    init {
        tts.language = Locale.getDefault()
        tts.setSpeechRate(1.0f)
    }

    fun mark(spokenText: String) {
        vibrate(longArrayOf(0, 130, 70, 130))
        tone.startTone(ToneGenerator.TONE_PROP_BEEP, 140)
        speak(spokenText)
    }

    fun done(spokenText: String) {
        vibrate(longArrayOf(0, 220, 80, 220, 80, 320))
        tone.startTone(ToneGenerator.TONE_PROP_ACK, 350)
        speak(spokenText)
    }

    fun speak(text: String) {
        if (ttsReady) tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "traincue-${System.currentTimeMillis()}")
    }

    fun release() {
        tone.release()
        tts.stop()
        tts.shutdown()
    }

    private fun vibrate(pattern: LongArray) {
        val vibrator = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            val manager = appContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            manager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            appContext.getSystemService(Activity.VIBRATOR_SERVICE) as Vibrator
        }
        vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
    }
}
