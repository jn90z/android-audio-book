package com.example.mobileappstarter

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

data class LocalBook(val id: String, val title: String, val pdf: File, val cover: File)

class BookLibrary(private val context: Context) {
    private val root = File(context.filesDir, "library").apply { mkdirs() }
    private val prefs = context.getSharedPreferences("local_library", Context.MODE_PRIVATE)
    fun all(): List<LocalBook> {
        val arr = JSONArray(prefs.getString("items", "[]"))
        return (0 until arr.length()).mapNotNull { i ->
            val item = arr.optJSONObject(i) ?: return@mapNotNull null
            val id = item.optString("id")
            val pdf = File(root, "$id.pdf")
            if (!pdf.exists()) null else LocalBook(id, item.optString("title"), pdf, File(root, "$id.png"))
        }
    }
    fun import(uri: Uri): LocalBook {
        val digest = MessageDigest.getInstance("SHA-256")
        val staging = File.createTempFile("import-", ".pdf", root)
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                staging.outputStream().use { output ->
                    val bytes = ByteArray(8192)
                    while (true) {
                        val n = input.read(bytes)
                        if (n < 0) break
                        digest.update(bytes, 0, n)
                        output.write(bytes, 0, n)
                    }
                }
            } ?: error("Cannot open PDF")
            val id = digest.digest().joinToString("") { "%02x".format(it) }
            val pdf = File(root, "$id.pdf")
            if (!pdf.exists()) {
                if (!staging.renameTo(pdf)) { staging.copyTo(pdf); staging.delete() }
            }
            val title = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0) else null
            } ?: "Untitled PDF"
            val book = LocalBook(id, title.removeSuffix(".pdf").removeSuffix(".PDF"), pdf, File(root, "$id.png"))
            if (!book.cover.exists()) {
                try {
                    ParcelFileDescriptor.open(pdf, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                        PdfRenderer(fd).use { renderer ->
                            if (renderer.pageCount > 0) renderer.openPage(0).use { page ->
                                val width = 360
                                val height = (width.toFloat() * page.height / page.width).toInt().coerceIn(1, 800)
                                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                                bitmap.eraseColor(Color.WHITE)
                                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                book.cover.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 90, it) }
                                bitmap.recycle()
                            }
                        }
                    }
                } catch (_: Exception) { /* Keep book even if cover rendering fails */ }
            }
            val items = JSONArray(prefs.getString("items", "[]"))
            if ((0 until items.length()).none { items.optJSONObject(it)?.optString("id") == id }) {
                items.put(JSONObject().put("id", id).put("title", book.title))
                prefs.edit().putString("items", items.toString()).apply()
            }
            return book
        } finally { staging.delete() }
    }
    fun remove(book: LocalBook) {
        val old = JSONArray(prefs.getString("items", "[]"))
        val next = JSONArray()
        for (i in 0 until old.length()) {
            val entry = old.optJSONObject(i) ?: continue
            if (entry.optString("id") != book.id) next.put(entry)
        }
        prefs.edit().putString("items", next.toString()).apply()
        book.pdf.delete()
        book.cover.delete()
    }
}
