package com.example.mobileappstarter

import android.content.Intent
import android.provider.OpenableColumns
import org.json.JSONArray
import android.os.Bundle
import android.speech.tts.Voice
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.net.Uri
import android.widget.*
import android.graphics.BitmapFactory
import android.view.Gravity
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.graphics.Bitmap
import android.text.SpannableString
import android.text.style.BackgroundColorSpan
import android.text.Spanned
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
    private lateinit var pdfPage: HighlightPdfView
    private lateinit var spokenText: TextView
    private lateinit var pageIndicator: TextView
    private var activeBook: LocalBook? = null
    private var pageNumber = 0
    private var pageCount = 0
    private var utteranceStart = 0
    private var utteranceEnd = 0
    private var segments = emptyList<ReadingSegment>()
    private var chunks = emptyList<String>()
    private var pageRenderRequest = 0
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
        pdfPage = HighlightPdfView(this).apply {
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
            setBackgroundColor(Color.WHITE)
        }
        root.addView(pdfPage, LinearLayout.LayoutParams(-1, -2))
        val pageControls = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        pageControls.addView(button("◀ Page") { showPage(pageNumber - 1) }, LinearLayout.LayoutParams(0, -2, 1f))
        pageIndicator = label("No page", 13f)
        pageControls.addView(pageIndicator)
        pageControls.addView(button("Page ▶") { showPage(pageNumber + 1) }, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(pageControls)
        root.addView(label("LIVE NARRATION", 13f))
        spokenText = label("The spoken passage will appear here.", 16f)
        root.addView(spokenText)
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
        root.addView(button("Choose offline voice") { chooseVoice() })
        val scroll = ScrollView(this).apply { addView(root) }
        setContentView(scroll)
        refreshLibrary()
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) {}
            override fun onRangeStart(id: String?, start: Int, end: Int, frame: Int) {
                runOnUiThread {
                    if (reading && id == currentId()) {
                        utteranceStart = start
                        utteranceEnd = end
                        highlightPassage()
                        updatePdfHighlight()
                    }
                }
            }
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
        activeBook = book
        documentId = book.id
        pageNumber = 0
        showPage(0)
        bookTitle.text = book.title
        chunks = emptyList()
        segments = emptyList()
        position = 0
        status.text = "Loading PDF…"
        updateProgress()
        lifecycleScope.launch {
            try {
                val extracted = withContext(Dispatchers.IO) { ReadingIndex.extract(book.pdf) }
                if (request != generation) return@launch
                segments = extracted
                chunks = extracted.map { it.text }
                position = getPreferences(MODE_PRIVATE).getInt(documentId, 0)
                    .coerceIn(0, (chunks.size - 1).coerceAtLeast(0))
                status.text = if (chunks.isEmpty()) "No selectable text. OCR is planned."
                              else "Ready to listen • ${chunks.size} sections"
                updateProgress()
                highlightPassage()
                followNarrationPage()
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
                            segments = emptyList()
                            activeBook = null
                            pdfPage.setImageDrawable(null)
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

    private data class RenderedPage(val bitmap: Bitmap, val page: Int, val count: Int, val width: Float, val height: Float)

    private fun showPage(requested: Int) {
        val book = activeBook ?: return
        val currentGeneration = generation
        val renderRequest = ++pageRenderRequest
        lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    ParcelFileDescriptor.open(book.pdf, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                        PdfRenderer(descriptor).use { renderer ->
                            val count = renderer.pageCount
                            val page = requested.coerceIn(0, (count - 1).coerceAtLeast(0))
                            if (count == 0) error("PDF has no pages")
                            renderer.openPage(page).use { p ->
                                val width = 900
                                val height = (width.toFloat() * p.height / p.width).toInt().coerceAtLeast(1)
                                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                                bitmap.eraseColor(Color.WHITE)
                                p.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                RenderedPage(bitmap, page, count, p.width.toFloat(), p.height.toFloat())
                            }
                        }
                    }
                }
                if (currentGeneration == generation && renderRequest == pageRenderRequest) {
                    pdfPage.setImageBitmap(result.bitmap)
                    pdfPage.bitmapWidth = result.bitmap.width.toFloat()
                    pdfPage.bitmapHeight = result.bitmap.height.toFloat()
                    pdfPage.pdfWidth = result.width
                    pdfPage.pdfHeight = result.height
                    pageNumber = result.page
                    pageCount = result.count
                    updatePdfHighlight()
                    pageIndicator.text = " ${pageNumber + 1} / $pageCount "
                } else result.bitmap.recycle()
            } catch (e: Exception) {
                if (currentGeneration == generation && renderRequest == pageRenderRequest) pageIndicator.text = "PDF preview unavailable"
            }
        }
    }

    private fun followNarrationPage() {
        val target = segments.getOrNull(position)?.page ?: return
        if (target != pageNumber) showPage(target)
    }

    private fun updatePdfHighlight() {
        val segment = segments.getOrNull(position)
        pdfPage.activeWord = if (segment != null && segment.page == pageNumber && reading)
            ReadingIndex.wordAt(segment, utteranceStart)?.rect
        else null
    }

    private fun highlightPassage() {
        if (position !in chunks.indices) {
            spokenText.text = "Choose a book to begin."
            return
        }
        val content = chunks[position]
        val span = SpannableString(content)
        val from = utteranceStart.coerceIn(0, content.length)
        val to = utteranceEnd.coerceIn(from, content.length)
        if (to > from) {
            span.setSpan(BackgroundColorSpan(0xffe8c66a.toInt()), from, to, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        spokenText.text = span
    }

    private fun chooseVoice() {
        if (!initialized) {
            status.text = "Speech engine is not ready."
            return
        }
        val voices = tts.voices.orEmpty()
            .filter { !it.isNetworkConnectionRequired && it.locale.language == Locale.getDefault().language }
            .sortedWith(compareBy<Voice> { it.locale.displayName }.thenBy { it.name })
        if (voices.isEmpty()) {
            status.text = "No installed offline voices found. Install voice data in Android settings."
            return
        }
        val names = voices.map { "${it.locale.displayName} • ${it.name}" }.toTypedArray()
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Offline narration voice")
            .setItems(names) { _, selected ->
                val wasReading = reading
                if (wasReading) pause()
                val result = tts.setVoice(voices[selected])
                status.text = if (result == TextToSpeech.SUCCESS) "Voice: ${voices[selected].name}"
                              else "Could not select voice."
                if (wasReading && result == TextToSpeech.SUCCESS) start()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun currentId() = "$generation:$position"
    private fun speak() {
        if (!initialized || position !in chunks.indices) return
        utteranceStart = 0
        utteranceEnd = 0
        highlightPassage()
        followNarrationPage()
        updatePdfHighlight()
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
        if (::pdfPage.isInitialized) pdfPage.activeWord = null
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
        if (::spokenText.isInitialized) highlightPassage()
        if (segments.isNotEmpty()) followNarrationPage()
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
