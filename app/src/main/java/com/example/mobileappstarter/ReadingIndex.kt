package com.example.mobileappstarter

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.File

/** Each speech segment belongs to exactly one original PDF page. */
data class ReadingSegment(val page: Int, val text: String)

object ReadingIndex {
    fun extract(pdf: File, limit: Int = 1800): List<ReadingSegment> {
        val output = mutableListOf<ReadingSegment>()
        PDDocument.load(pdf).use { document ->
            val stripper = PDFTextStripper().apply { sortByPosition = true }
            for (page in 1..document.numberOfPages) {
                stripper.startPage = page
                stripper.endPage = page
                val text = stripper.getText(document)
                    .replace(Regex("\\s+"), " ").trim()
                if (text.isEmpty()) continue
                val words = text.split(" ")
                val buffer = StringBuilder()
                for (word in words) {
                    if (buffer.isNotEmpty() && buffer.length + word.length + 1 > limit) {
                        output.add(ReadingSegment(page - 1, buffer.toString()))
                        buffer.clear()
                    }
                    if (buffer.isNotEmpty()) buffer.append(' ')
                    buffer.append(word)
                }
                if (buffer.isNotEmpty()) output.add(ReadingSegment(page - 1, buffer.toString()))
            }
        }
        return output
    }
}
