package com.example.mobileappstarter

import android.graphics.RectF
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import java.io.File

/** Coordinates are in PDFBox's top-left-adjusted page units. */
data class WordBox(val word: String, val rect: RectF)
data class ReadingSegment(val page: Int, val text: String, val words: List<WordBox> = emptyList())

object ReadingIndex {
    private fun normalize(s: String) = s.lowercase().replace(Regex("[^\\p{L}\\p{N}]"), "")

    fun extract(pdf: File, limit: Int = 1800): List<ReadingSegment> {
        val output = mutableListOf<ReadingSegment>()
        PDDocument.load(pdf).use { document ->
            for (page in 1..document.numberOfPages) {
                val boxes = mutableListOf<WordBox>()
                val stripper = object : PDFTextStripper() {
                    override fun writeString(text: String, positions: MutableList<TextPosition>) {
                        val word = StringBuilder()
                        var rect: RectF? = null
                        fun flush() {
                            if (word.isNotEmpty() && rect != null) {
                                boxes.add(WordBox(word.toString(), RectF(rect!!)))
                                word.clear()
                                rect = null
                            }
                        }
                        for (pos in positions) {
                            val glyph = pos.unicode ?: continue
                            val glyphRect = RectF(
                                pos.xDirAdj, pos.yDirAdj - pos.heightDir,
                                pos.xDirAdj + pos.widthDirAdj, pos.yDirAdj
                            )
                            for (character in glyph) {
                                if (character.isWhitespace()) flush()
                                else {
                                    word.append(character)
                                    if (rect == null) rect = RectF(glyphRect) else rect!!.union(glyphRect)
                                }
                            }
                        }
                        flush()
                    }
                }.apply {
                    sortByPosition = true
                    startPage = page
                    endPage = page
                }
                // TextPosition coordinates and speech text come from the same extraction pass.
                stripper.getText(document)
                if (boxes.isEmpty()) continue
                val current = mutableListOf<WordBox>()
                var length = 0
                fun flushSegment() {
                    if (current.isNotEmpty()) {
                        output.add(ReadingSegment(page - 1, current.joinToString(" ") { it.word }, current.toList()))
                        current.clear()
                        length = 0
                    }
                }
                for (box in boxes) {
                    if (current.isNotEmpty() && length + box.word.length + 1 > limit) flushSegment()
                    current.add(box)
                    length += box.word.length + 1
                }
                flushSegment()
            }
        }
        return output
    }

    /** Match the speech engine's range to a word from the same segment. */
    fun wordAt(segment: ReadingSegment, offset: Int): WordBox? {
        var cursor = 0
        for (word in segment.words) {
            val end = cursor + word.word.length
            if (offset < end) return word
            cursor = end + 1
        }
        return segment.words.lastOrNull()
    }
}
