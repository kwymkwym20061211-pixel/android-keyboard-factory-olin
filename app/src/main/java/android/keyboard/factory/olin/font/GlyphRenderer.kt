package android.keyboard.factory.olin.font

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import java.io.ByteArrayOutputStream
import java.io.File

/** Rasterizes a CHAR key's text into a PNG at export time, so the generated keyboard app ships
 * a bitmap per key instead of bundling the whole font (per the size-budget decision in
 * docs/2026/06/keyboard-factory-architecture.md). Resolution strategy is fixed today but kept
 * behind this single entry point so a future multi-density pass doesn't have to touch callers. */
object GlyphRenderer {
    const val GLYPH_SIZE_PX = 128

    fun renderPng(text: String, fontFile: File?, sizePx: Int = GLYPH_SIZE_PX): ByteArray {
        val typeface = fontFile
            ?.takeIf { it.exists() }
            ?.let { runCatching { Typeface.createFromFile(it) }.getOrNull() }
            ?: Typeface.DEFAULT

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.typeface = typeface
            textSize = sizePx * 0.6f
            color = Color.BLACK
            textAlign = Paint.Align.CENTER
        }

        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).apply {
            val y = sizePx / 2f - (paint.ascent() + paint.descent()) / 2f
            drawText(text, sizePx / 2f, y, paint)
        }

        val bytes = ByteArrayOutputStream().apply {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, this)
        }.toByteArray()
        bitmap.recycle()
        return bytes
    }
}
