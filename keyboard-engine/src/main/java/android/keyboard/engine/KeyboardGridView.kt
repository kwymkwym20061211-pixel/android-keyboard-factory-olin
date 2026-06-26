package android.keyboard.engine

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

/** Renders one [PageLayout] as a grid, drawing each [KeyDef]'s shape as the union of the unit
 * cells it owns (supports non-rectangular / staircase merges), and resolves taps back to the
 * owning key. Used both by the in-app editor preview and by the generated keyboard at runtime. */
class KeyboardGridView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    var imageProvider: KeyImageProvider? = null
    var onKeyTapped: ((KeyDef) -> Unit)? = null

    private var page: PageLayout? = null

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.LTGRAY
    }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = Color.DKGRAY
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textAlign = Paint.Align.CENTER
    }

    fun setPage(newPage: PageLayout) {
        page = newPage
        invalidate()
    }

    private fun cellSize(p: PageLayout): Pair<Float, Float> =
        (width.toFloat() / p.cols) to (height.toFloat() / p.rows)

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val p = page ?: return
        if (p.cols <= 0 || p.rows <= 0) return
        val (cellW, cellH) = cellSize(p)
        textPaint.textSize = minOf(cellW, cellH) * 0.4f

        for (key in p.keys) {
            val path = Path()
            for (cell in key.ownedCells) {
                val (r, c) = cell[0] to cell[1]
                path.addRect(
                    c * cellW,
                    r * cellH,
                    (c + 1) * cellW,
                    (r + 1) * cellH,
                    Path.Direction.CW,
                )
            }
            canvas.drawPath(path, fillPaint)
            canvas.drawPath(path, strokePaint)

            val bounds = RectF()
            path.computeBounds(bounds, true)
            val bitmap = key.image?.let { imageProvider?.getBitmap(it) }
            if (bitmap != null) {
                val dst = Rect(
                    bounds.left.toInt(),
                    bounds.top.toInt(),
                    bounds.right.toInt(),
                    bounds.bottom.toInt(),
                )
                canvas.drawBitmap(bitmap, null, dst, null)
            } else {
                canvas.drawText(labelFor(key), bounds.centerX(), bounds.centerY() - (textPaint.ascent() + textPaint.descent()) / 2, textPaint)
            }
        }
    }

    private fun labelFor(key: KeyDef): String = when (key.role) {
        KeyRole.CHAR -> key.text.orEmpty()
        KeyRole.ENTER -> "⏎"
        KeyRole.DELETE -> "⌫"
        KeyRole.PAGE_NEXT -> "▶"
        KeyRole.PAGE_PREV -> "◀"
        KeyRole.NONE -> ""
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action != MotionEvent.ACTION_UP) return true
        val p = page ?: return true
        val (cellW, cellH) = cellSize(p)
        val col = (event.x / cellW).toInt().coerceIn(0, p.cols - 1)
        val row = (event.y / cellH).toInt().coerceIn(0, p.rows - 1)
        val key = p.keys.firstOrNull { k -> k.ownedCells.any { it[0] == row && it[1] == col } }
        if (key != null) onKeyTapped?.invoke(key)
        return true
    }
}
