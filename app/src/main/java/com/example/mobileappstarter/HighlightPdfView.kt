package com.example.mobileappstarter

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatImageView

/** Draws a translucent overlay in bitmap coordinates using ImageView's image matrix. */
class HighlightPdfView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : AppCompatImageView(context, attrs) {
    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x88ffd34d.toInt()
        style = Paint.Style.FILL
    }
    var activeWord: RectF? = null
        set(value) { field = value; invalidate() }
    var pdfWidth: Float = 1f
    var pdfHeight: Float = 1f
    var bitmapWidth: Float = 1f
    var bitmapHeight: Float = 1f

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val rect = activeWord ?: return
        if (drawable == null || pdfWidth <= 0f || pdfHeight <= 0f) return
        val saved = canvas.save()
        canvas.translate(paddingLeft.toFloat(), paddingTop.toFloat())
        canvas.concat(imageMatrix)
        val scaled = RectF(
            rect.left * bitmapWidth / pdfWidth,
            rect.top * bitmapHeight / pdfHeight,
            rect.right * bitmapWidth / pdfWidth,
            rect.bottom * bitmapHeight / pdfHeight
        )
        canvas.drawRoundRect(scaled, 4f, 4f, highlightPaint)
        canvas.restoreToCount(saved)
    }
}
