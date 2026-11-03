package com.example.mobileappstarter

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem
import java.io.File

data class BookChapter(val title: String, val page: Int, val depth: Int)

/** Read the document's own outline; never guess chapter locations. */
object ChapterIndex {
    fun extract(file: File): List<BookChapter> {
        val chapters = mutableListOf<BookChapter>()
        PDDocument.load(file).use { document ->
            fun visit(item: PDOutlineItem?, depth: Int) {
                var current = item
                while (current != null && chapters.size < 500) {
                    val page = try {
                        val target = current.findDestinationPage(document)
                        if (target == null) -1 else document.pages.indexOf(target)
                    } catch (_: Exception) { -1 }
                    if (page >= 0) chapters.add(BookChapter(current.title ?: "Untitled", page, depth))
                    if (depth < 8) visit(current.firstChild, depth + 1)
                    current = current.nextSibling
                }
            }
            visit(document.documentCatalog.documentOutline?.firstChild, 0)
        }
        return chapters
    }
}
