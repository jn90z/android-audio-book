package com.example.mobileappstarter

import android.app.Activity
import android.content.Intent
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.os.SystemClock
import android.widget.Toast
import androidx.appcompat.app.AlertDialog

/**
 * Test installed offline Android speech engines, including third-party neural engines.
 * This is an experiment harness, not an embedded Kokoro model.
 */
object TtsExperiment {
    private const val SAMPLE = "The evening light fell softly across the valley. A gentle breeze stirred the trees, and the road ahead seemed full of possibility."

    fun show(activity: Activity, currentTts: TextToSpeech, onReinitialize: () -> Unit) {
        val engines = currentTts.engines.orEmpty()
        val names = engines.map { "${it.label} (${it.name})" }
        val message = buildString {
            append("Install a neural TTS engine, such as HayaiTTS, then select it in Android's Text-to-speech settings. Return here to test it.\n\n")
            append("Detected engines:\n")
            if (names.isEmpty()) append("None reported") else names.forEach { append("• $it\n") }
            append("\nThis experiment uses the installed Android TTS interface. No cloud API or paid service is used by this app; individual engines may have different requirements.")
        }
        AlertDialog.Builder(activity)
            .setTitle("Neural voice laboratory")
            .setMessage(message)
            .setPositiveButton("Android TTS settings") { _, _ ->
                try {
                    activity.startActivity(Intent("com.android.settings.TTS_SETTINGS"))
                } catch (_: Exception) {
                    try { activity.startActivity(Intent(Settings.ACTION_SETTINGS)) }
                    catch (_: Exception) { Toast.makeText(activity, "Settings unavailable", Toast.LENGTH_LONG).show() }
                }
            }
            .setNeutralButton("Run speech test") { _, _ ->
                runBenchmark(activity, currentTts, onReinitialize)
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun runBenchmark(activity: Activity, tts: TextToSpeech, onReinitialize: () -> Unit) {
        val started = SystemClock.elapsedRealtime()
        val id = "tts-lab-${SystemClock.elapsedRealtime()}"
        // Keep the experiment isolated: restore the reader's listener after the test.
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                if (utteranceId != id) return
                val latency = SystemClock.elapsedRealtime() - started
                activity.runOnUiThread {
                    Toast.makeText(activity, "Speech start latency: ${latency} ms", Toast.LENGTH_LONG).show()
                }
            }
            override fun onDone(utteranceId: String?) {
                if (utteranceId == id) activity.runOnUiThread { onReinitialize() }
            }
            override fun onError(utteranceId: String?) {
                if (utteranceId == id) activity.runOnUiThread {
                    Toast.makeText(activity, "Speech test failed", Toast.LENGTH_LONG).show()
                    onReinitialize()
                }
            }
        })
        val result = tts.speak(SAMPLE, TextToSpeech.QUEUE_FLUSH, null, id)
        if (result != TextToSpeech.SUCCESS) {
            onReinitialize()
            Toast.makeText(activity, "Speech engine rejected test", Toast.LENGTH_LONG).show()
        }
    }
}
