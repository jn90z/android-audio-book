package com.example.mobileappstarter

import android.content.Intent
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.net.Uri
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {
    private lateinit var tts: TextToSpeech
    private lateinit var status: TextView
    private lateinit var progress: TextView
    private lateinit var play: Button
    private lateinit var speedLabel: TextView
    private var chunks = emptyList<String>()
    private var position = 0
    private var reading = false
    private var initialized = false
    private var speed = 1f
    private var documentId = ""
    private var generation = 0

    private val openPdf = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) importPdf(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PDFBoxResourceLoader.init(applicationContext)
        tts = TextToSpeech(this, this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 36, 28, 24)
            setBackgroundColor(0xff151820.toInt())
        }
        fun label(value: String, size: Float = 16f): TextView =
            TextView(this).apply { text = value; textSize = size; setTextColor(0xfff2f3f7.toInt()); setPadding(0, 8, 0, 8) }
        fun button(value: String, action: () -> Unit): Button =
            Button(this).apply { text = value; setOnClickListener { action() } }
        root.addView(label("Audio Book", 30f))
        root.addView(label("Free • Offline • No account", 14f))
        root.addView(button("Import PDF") { openPdf.launch(arrayOf("application/pdf")) })
        status = label("Choose a text-based PDF to start.")
        progress = label("")
        root.addView(status)
        root.addView(progress)
        val controls = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        controls.addView(button("Previous") { jump(-1) }, LinearLayout.LayoutParams(0, -2, 1f))
        play = button("Play") { if (reading) pause() else start() }
        controls.addView(play, LinearLayout.LayoutParams(0, -2, 1f))
        controls.addView(button("Next") { jump(1) }, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(controls)
        speedLabel = label("Speed: 1.0×")
        root.addView(speedLabel)
        val seek = SeekBar(this).apply {
            max = 15
            progress = 5
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar?, value: Int, fromUser: Boolean) {
                    if (fromUser) {
                        speed = 0.5f + value / 10f
                        speedLabel.text = "Speed: %.1f×".format(Locale.US, speed)
                        tts.setSpeechRate(speed)
                        if (reading) speak()
                    }
                }
                override fun onStartTrackingTouch(bar: SeekBar?) {}
                override fun onStopTrackingTouch(bar: SeekBar?) {}
            })
        }
        root.addView(seek)
        val scroll = ScrollView(this).apply { addView(root) }
        setContentView(scroll)
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) {}
            override fun onError(id: String?) { runOnUiThread { if (id == currentId()) { pause(); status.text = "Speech playback failed." } } }
            override fun onDone(id: String?) {
                runOnUiThread {
                    if (reading && id == currentId()) {
                        position++
                        persist()
                        if (position < chunks.size) speak() else {
                            reading = false
                            play.text = "Play"
                            status.text = "Finished."
                            updateProgress()
                        }
                    }
                }
            }
        })
    }

    private fun importPdf(uri: Uri) {
        pause()
        val request = ++generation
        documentId = uri.toString()
        chunks = emptyList()
        position = 0
        status.text = "Extracting PDF text…"
        updateProgress()
        lifecycleScope.launch {
            try {
                val extracted = withContext(Dispatchers.IO) {
                    contentResolver.openInputStream(uri)?.use { input ->
                        PDDocument.load(input).use { PDFTextStripper().getText(it) }
                    } ?: error("Could not open file")
                }
                if (request != generation) return@launch
                chunks = chunk(extracted)
                position = getPreferences(MODE_PRIVATE).getInt(documentId, 0).coerceIn(0, (chunks.size - 1).coerceAtLeast(0))
                status.text = if (chunks.isEmpty()) "No selectable text found. Scanned PDFs will require OCR."
                              else "Ready to listen • ${chunks.size} sections"
                updateProgress()
            } catch (e: Exception) {
                if (request == generation) status.text = "PDF import failed: ${e.localizedMessage}"
            }
        }
    }

    private fun chunk(text: String): List<String> {
        val words = text.replace(Regex("\\s+"), " ").trim().split(" ").filter { it.isNotBlank() }
        val result = mutableListOf<String>()
        val buffer = StringBuilder()
        for (word in words) {
            if (buffer.isNotEmpty() && buffer.length + word.length + 1 > 1800) {
                result.add(buffer.toString())
                buffer.clear()
            }
            if (buffer.isNotEmpty()) buffer.append(' ')
            buffer.append(word)
        }
        if (buffer.isNotEmpty()) result.add(buffer.toString())
        return result
    }

    private fun currentId() = "$generation:$position"
    private fun speak() {
        if (!initialized || position !in chunks.indices) return
        tts.setSpeechRate(speed)
        tts.speak(chunks[position], TextToSpeech.QUEUE_FLUSH, null, currentId())
        updateProgress()
    }
    private fun start() {
        if (!initialized) { status.text = "Speech engine unavailable. Check installed voice data."; return }
        if (chunks.isEmpty()) return
        if (position >= chunks.size) position = 0
        reading = true
        play.text = "Pause"
        speak()
    }
    private fun pause() {
        reading = false
        if (::tts.isInitialized) tts.stop()
        if (::play.isInitialized) play.text = "Play"
        persist()
    }
    private fun jump(amount: Int) {
        if (chunks.isEmpty()) return
        position = (position + amount).coerceIn(0, chunks.lastIndex)
        persist()
        updateProgress()
        if (reading) speak()
    }
    private fun persist() {
        if (documentId.isNotEmpty()) getPreferences(MODE_PRIVATE).edit().putInt(documentId, position).apply()
    }
    private fun updateProgress() {
        progress.text = if (chunks.isEmpty()) "" else "Section ${position.coerceAtMost(chunks.size - 1) + 1} of ${chunks.size}"
    }
    override fun onInit(code: Int) {
        initialized = code == TextToSpeech.SUCCESS
        if (initialized) {
            val result = tts.setLanguage(Locale.getDefault())
            initialized = result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED
        }
    }
    override fun onDestroy() {
        pause()
        tts.shutdown()
        super.onDestroy()
    }
}
