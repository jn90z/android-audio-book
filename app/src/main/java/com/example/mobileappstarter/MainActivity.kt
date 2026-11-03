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
import android.view.View
import android.graphics.drawable.GradientDrawable
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.graphics.Bitmap
import android.text.SpannableString
import android.text.style.BackgroundColorSpan
import android.text.Spanned
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
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
    private lateinit var libraryPanel: LinearLayout
    private lateinit var readerPanel: LinearLayout
    private lateinit var readerHeader: TextView
    private lateinit var readerScroll: ScrollView
    private lateinit var bottomControls: LinearLayout
    private lateinit var voiceButton: Button
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
    private var chapters = emptyList<BookChapter>()
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
        val navy = 0xff171b27.toInt()
        val mint = 0xff63d7cb.toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(navy)
        }
        fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
        fun label(value: String, size: Float = 16f, color: Int = Color.WHITE): TextView =
            TextView(this).apply {
                text = value
                textSize = size
                setTextColor(color)
                setPadding(dp(8), dp(8), dp(8), dp(8))
            }
        fun button(value: String, action: () -> Unit): Button =
            Button(this).apply {
                text = value
                isAllCaps = false
                setOnClickListener { action() }
            }

        libraryPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        libraryPanel.addView(label("MY LIBRARY", 13f, mint))
        libraryPanel.addView(label("Audio Book", 30f))
        libraryPanel.addView(label("Your books, always offline", 14f, 0xffb9c2d3.toInt()))
        libraryPanel.addView(button("＋  Import PDF") { openPdf.launch(arrayOf("application/pdf")) })
        library = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        libraryPanel.addView(library)
        val libraryScroll = ScrollView(this).apply {
            isFillViewport = true
            addView(libraryPanel)
        }
        root.addView(libraryScroll, LinearLayout.LayoutParams(-1, 0, 1f))

        readerPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }
        header.addView(button("‹  Library") { showLibrary() })
        readerHeader = label("Book Reader", 17f).apply {
            gravity = Gravity.CENTER
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        header.addView(readerHeader, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(button("☰") { showContents() })
        readerPanel.addView(header)

        readerScroll = ScrollView(this).apply {
            isFillViewport = false
            setBackgroundColor(0xfff6f2e9.toInt())
        }
        val readingContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(18))
        }
        pdfPage = HighlightPdfView(this).apply {
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
            setBackgroundColor(Color.WHITE)
        }
        readingContent.addView(pdfPage, LinearLayout.LayoutParams(-1, -2))
        val pageControls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        pageControls.addView(button("‹ Page") { showPage(pageNumber - 1) }, LinearLayout.LayoutParams(0, -2, 1f))
        pageIndicator = label("No page", 13f, 0xff30313a.toInt()).apply { gravity = Gravity.CENTER }
        pageControls.addView(pageIndicator)
        pageControls.addView(button("Page ›") { showPage(pageNumber + 1) }, LinearLayout.LayoutParams(0, -2, 1f))
        readingContent.addView(pageControls)
        spokenText = label("Narration text will appear here.", 15f, 0xff30313a.toInt())
        readingContent.addView(spokenText)
        readerScroll.addView(readingContent)
        readerPanel.addView(readerScroll, LinearLayout.LayoutParams(-1, 0, 1f))

        bottomControls = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(6), dp(12), dp(8))
            setBackgroundColor(navy)
        }
        bookTitle = label("No book selected", 16f)
        bookTitle.maxLines = 1
        bookTitle.ellipsize = android.text.TextUtils.TruncateAt.END
        bottomControls.addView(bookTitle)
        status = label("Choose a text-based PDF to start.", 12f, 0xffb9c2d3.toInt())
        progress = label("", 12f, 0xffb9c2d3.toInt())
        bottomControls.addView(status)
        bottomControls.addView(progress)
        overall = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 1000
            progress = 0
            progressTintList = android.content.res.ColorStateList.valueOf(mint)
        }
        bottomControls.addView(overall, LinearLayout.LayoutParams(-1, dp(4)))
        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        controls.addView(button("⏮") { jump(-1) }, LinearLayout.LayoutParams(0, -2, 1f))
        play = button("Play") { if (reading) pause() else start() }
        controls.addView(play, LinearLayout.LayoutParams(0, -2, 1f))
        controls.addView(button("⏭") { jump(1) }, LinearLayout.LayoutParams(0, -2, 1f))
        bottomControls.addView(controls)
        val settingsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        speedLabel = label("1.0× Speed", 13f)
        settingsRow.addView(speedLabel)
        val seek = SeekBar(this).apply {
            max = 15
            progress = 5
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar?, value: Int, fromUser: Boolean) {
                    if (fromUser) {
                        speed = 0.5f + value / 10f
                        speedLabel.text = "%.1f×".format(Locale.US, speed)
                        if (initialized) tts.setSpeechRate(speed)
                        if (reading) speak()
                    }
                }
                override fun onStartTrackingTouch(bar: SeekBar?) {}
                override fun onStopTrackingTouch(bar: SeekBar?) {}
            })
        }
        settingsRow.addView(seek, LinearLayout.LayoutParams(0, dp(40), 1f))
        voiceButton = button("Voice ▾") { chooseVoice() }
        settingsRow.addView(voiceButton)
        bottomControls.addView(settingsRow)
        readerPanel.addView(bottomControls)
        root.addView(readerPanel, LinearLayout.LayoutParams(-1, 0, 1f))

        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        setContentView(root)
        ViewCompat.requestApplyInsets(root)
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

    private fun showReader() {
        readerPanel.visibility = View.VISIBLE
        (libraryPanel.parent?.parent as? View)?.visibility = View.GONE
    }

    private fun showLibrary() {
        pause()
        readerPanel.visibility = View.GONE
        (libraryPanel.parent?.parent as? View)?.visibility = View.VISIBLE
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
        readerHeader.text = book.title
        showReader()
        chunks = emptyList()
        segments = emptyList()
        chapters = emptyList()
        position = 0
        status.text = "Loading PDF…"
        updateProgress()
        lifecycleScope.launch {
            try {
                val (extracted, outline) = withContext(Dispatchers.IO) {
                    Pair(ReadingIndex.extract(book.pdf), runCatching { ChapterIndex.extract(book.pdf) }.getOrDefault(emptyList()))
                }
                if (request != generation) return@launch
                segments = extracted
                chapters = outline
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
                            chapters = emptyList()
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

    private fun jumpToPage(page: Int) {
        val target = page.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
        val next = segments.indexOfFirst { it.page >= target }
        val wasReading = reading
        if (wasReading) pause()
        if (next >= 0) {
            position = next
            utteranceStart = 0
            utteranceEnd = 0
            persist()
            updateProgress()
        }
        showPage(target)
        if (wasReading && next >= 0) start()
    }

    private fun showContents() {
        if (activeBook == null) {
            status.text = "Open a book first."
            return
        }
        val items = chapters.map { "  ".repeat(it.depth) + it.title + "  ·  p. ${it.page + 1}" }
        val labels = (listOf("Jump to page…") + items).toTypedArray()
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(if (chapters.isEmpty()) "Pages" else "Table of contents")
            .setItems(labels) { _, selected ->
                if (selected == 0) {
                    val input = EditText(this).apply {
                        inputType = android.text.InputType.TYPE_CLASS_NUMBER
                        hint = "1–$pageCount"
                    }
                    androidx.appcompat.app.AlertDialog.Builder(this)
                        .setTitle("Jump to page")
                        .setView(input)
                        .setNegativeButton("Cancel", null)
                        .setPositiveButton("Go") { _, _ ->
                            val page = input.text.toString().toIntOrNull()
                            if (page != null && page in 1..pageCount) jumpToPage(page - 1)
                            else status.text = "Enter a page from 1 to $pageCount."
                        }.show()
                } else jumpToPage(chapters[selected - 1].page)
            }
            .setNegativeButton("Close", null)
            .show()
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
        val voiceNumbers = mutableMapOf<String, Int>()
        val names = voices.map { voice ->
            val localeKey = voice.locale.toLanguageTag()
            val number = (voiceNumbers[localeKey] ?: 0) + 1
            voiceNumbers[localeKey] = number
            "${voice.locale.displayName} · Voice $number · " +
                (if (voice.quality >= Voice.QUALITY_HIGH) "High quality" else "Standard") +
                " · Offline"
        }.toTypedArray()
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Offline narration voice")
            .setSingleChoiceItems(names, -1) { dialog, selected ->
                dialog.dismiss()
                selectVoice(voices[selected])
            }
            .setNeutralButton("Preview current") { _, _ ->
                tts.speak("This is how your audiobook will sound.", TextToSpeech.QUEUE_FLUSH, null, "voice-preview")
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun selectVoice(voice: Voice) {
        if (reading) pause()
        val result = tts.setVoice(voice)
        status.text = if (result == TextToSpeech.SUCCESS) "Voice selected: ${voice.locale.displayName}"
                      else "Could not select voice."
        if (result == TextToSpeech.SUCCESS) {
            getSharedPreferences("narration_settings", MODE_PRIVATE).edit().putString("voice_name", voice.name).apply()
            tts.speak("This is how your audiobook will sound.", TextToSpeech.QUEUE_FLUSH, null, "voice-preview")
        }
        // Selection does not resume automatically: preview and playback must not overlap.
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
        if (!initialized) return
        val savedName = getSharedPreferences("narration_settings", MODE_PRIVATE)
            .getString("voice_name", null)
        val savedVoice = tts.voices.orEmpty().firstOrNull {
            it.name == savedName && !it.isNetworkConnectionRequired
        }
        if (savedVoice != null && tts.setVoice(savedVoice) == TextToSpeech.SUCCESS) {
            if (::status.isInitialized) status.text = "Offline voice restored"
            return
        }
        val result = tts.setLanguage(Locale.getDefault())
        initialized = result != TextToSpeech.LANG_MISSING_DATA &&
            result != TextToSpeech.LANG_NOT_SUPPORTED
        if (savedName != null && ::status.isInitialized) {
            status.text = "Saved voice unavailable; using device default."
        }
    }
    @Deprecated("Legacy Android back callback")
    override fun onBackPressed() {
        if (readerPanel.visibility == View.VISIBLE) showLibrary()
        else super.onBackPressed()
    }

    override fun onDestroy() {
        pause()
        tts.shutdown()
        super.onDestroy()
    }
}
