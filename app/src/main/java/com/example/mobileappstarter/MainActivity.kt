package com.example.mobileappstarter

import android.content.Intent
import android.provider.OpenableColumns
import org.json.JSONArray
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.net.Uri
import android.widget.*
import android.graphics.BitmapFactory
import android.view.Gravity
import android.graphics.Color
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
    private lateinit var library: LinearLayout
    private lateinit var bookTitle: TextView
    private lateinit var overall: ProgressBar
    private var chunks = emptyList<String>()
    private var position = 0
    private var reading = false
    private var initialized = false
    private var speed = 1f
    private var documentId = ""
    private lateinit var bookLibrary: BookLibrary
    private var generation = 0

    private val openPdf = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) importPdf(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PDFBoxResourceLoader.init(applicationContext)
        bookLibrary = BookLibrary(this)
        tts = TextToSpeech(this, this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 28, 24, 32)
            setBackgroundColor(0xff151820.toInt())
        }
        fun label(value: String, size: Float = 16f): TextView =
            TextView(this).apply { text = value; textSize = size; setTextColor(0xfff2f3f7.toInt()); setPadding(0, 8, 0, 8) }
        fun button(value: String, action: () -> Unit): Button =
            Button(this).apply { text = value; setOnClickListener { action() } }
        root.addView(label("MY LIBRARY", 13f))
        root.addView(label("Audio Book", 30f))
        root.addView(label("Free • Offline • No account", 14f))
        root.addView(button("＋  Add a PDF") { openPdf.launch(arrayOf("application/pdf")) })
        library = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(library)
        root.addView(label("NOW READING", 13f))
        bookTitle = label("No book selected", 22f)
        root.addView(bookTitle)
        status = label("Choose a text-based PDF to start.")
        progress = label("")
        root.addView(status)
        root.addView(progress)
        overall = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { max = 1000; progress = 0 }
        root.addView(overall)
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
        refreshLibrary()
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
        status.text = "Saving PDF to local library…"
        lifecycleScope.launch {
            try {
                val book = withContext(Dispatchers.IO) { bookLibrary.import(uri) }
                if (request != generation) return@launch
                refreshLibrary()
                openBook(book)
            } catch (e: Exception) {
                if (request == generation) status.text = "Import failed: ${e.localizedMessage}"
            }
        }
    }

    private fun openBook(book: LocalBook) {
        pause()
        val request = ++generation
        documentId = book.id
        bookTitle.text = book.title
        chunks = emptyList()
        position = 0
        status.text = "Loading PDF…"
        updateProgress()
        lifecycleScope.launch {
            try {
                val text = withContext(Dispatchers.IO) {
                    PDDocument.load(book.pdf).use { PDFTextStripper().getText(it) }
                }
                if (request != generation) return@launch
                chunks = chunk(text)
                position = getPreferences(MODE_PRIVATE).getInt(documentId, 0)
                    .coerceIn(0, (chunks.size - 1).coerceAtLeast(0))
                status.text = if (chunks.isEmpty()) "No selectable text. OCR is planned."
                              else "Ready to listen • ${chunks.size} sections"
                updateProgress()
            } catch (e: Exception) {
                if (request == generation) status.text = "Load failed: ${e.localizedMessage}"
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

    private fun refreshLibrary() {
        library.removeAllViews()
        val books = bookLibrary.all()
        if (books.isEmpty()) {
            library.addView(TextView(this).apply {
                text = "No books yet. Import a PDF to get started."
                setTextColor(0xffadb7c9.toInt())
                setPadding(0, 16, 0, 24)
            })
        }
        for (book in books.reversed()) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(8, 12, 8, 12)
                setBackgroundColor(0xff252a36.toInt())
            }
            val image = ImageView(this).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                if (book.cover.exists()) setImageBitmap(BitmapFactory.decodeFile(book.cover.absolutePath))
                else setImageResource(android.R.drawable.ic_menu_gallery)
            }
            row.addView(image, LinearLayout.LayoutParams(88, 120))
            row.addView(TextView(this).apply {
                text = book.title
                textSize = 17f
                setTextColor(Color.WHITE)
                setPadding(16, 4, 8, 4)
                maxLines = 3
            }, LinearLayout.LayoutParams(0, -2, 1f))
            row.setOnClickListener { openBook(book) }
            row.setOnLongClickListener {
                androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("Remove book?")
                    .setMessage("Delete this book from local storage?")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Remove") { _, _ ->
                        if (documentId == book.id) {
                            pause()
                            ++generation
                            documentId = ""
                            chunks = emptyList()
                            bookTitle.text = "No book selected"
                            updateProgress()
                        }
                        bookLibrary.remove(book)
                        refreshLibrary()
                    }.show()
                true
            }
            library.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 10 })
        }
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
        overall.progress = if (chunks.isEmpty()) 0 else ((position.coerceAtMost(chunks.size) * 1000L) / chunks.size).toInt()
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
