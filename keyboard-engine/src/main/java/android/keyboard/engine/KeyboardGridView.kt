package android.keyboard.engine

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Region
import android.graphics.Typeface
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
    private val pressedFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.GRAY
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

    /** Font used to draw CHAR-role key labels here in the editor preview. The generated keyboard
     * app instead bakes each label into a bitmap via GlyphRenderer at export time and draws that
     * through [imageProvider], so it never needs this — this exists for the live preview only. */
    var typeface: Typeface?
        get() = textPaint.typeface
        set(value) {
            textPaint.typeface = value
            invalidate()
        }

    private var pressedKey: KeyDef? = null

    fun setPage(newPage: PageLayout) {
        page = newPage
        pressedKey = null
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
            // Union the owned cells into one Region first so the boundary path traces only the
            // outer outline of the (possibly non-rectangular) merge — no internal seams between
            // the merged cells, regardless of how many of them there are.
            val region = Region()
            for (cell in key.ownedCells) {
                val (r, c) = cell[0] to cell[1]
                region.op(
                    Rect((c * cellW).toInt(), (r * cellH).toInt(), ((c + 1) * cellW).toInt(), ((r + 1) * cellH).toInt()),
                    Region.Op.UNION,
                )
            }
            val path = Path()
            region.getBoundaryPath(path)
            canvas.drawPath(path, if (key == pressedKey) pressedFillPaint else fillPaint)
            canvas.drawPath(path, strokePaint)

            // Center the label/image on the topmost-then-leftmost owned cell, not the bounding
            // box of the whole merge: for an L-shape/staircase the bounding-box center can land
            // outside the actual filled area (e.g. in the notch of an L), clipping the preview.
            val anchor = key.ownedCells.minWith(compareBy({ it[0] }, { it[1] }))
            val anchorBounds = RectF(
                anchor[1] * cellW,
                anchor[0] * cellH,
                (anchor[1] + 1) * cellW,
                (anchor[0] + 1) * cellH,
            )
            val bitmap = key.image?.let { imageProvider?.getBitmap(it) }
            if (bitmap != null) {
                val dst = Rect(
                    anchorBounds.left.toInt(),
                    anchorBounds.top.toInt(),
                    anchorBounds.right.toInt(),
                    anchorBounds.bottom.toInt(),
                )
                canvas.drawBitmap(bitmap, null, dst, null)
            } else {
                canvas.drawText(
                    labelFor(key),
                    anchorBounds.centerX(),
                    anchorBounds.centerY() - (textPaint.ascent() + textPaint.descent()) / 2,
                    textPaint,
                )
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

    private fun resolveKeyAt(x: Float, y: Float): KeyDef? {
        val p = page ?: return null
        val (cellW, cellH) = cellSize(p)
        val col = (x / cellW).toInt().coerceIn(0, p.cols - 1)
        val row = (y / cellH).toInt().coerceIn(0, p.rows - 1)
        return p.keys.firstOrNull { k -> k.ownedCells.any { it[0] == row && it[1] == col } }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                val key = resolveKeyAt(event.x, event.y)
                if (key != pressedKey) {
                    pressedKey = key
                    invalidate()
                }
            }
            MotionEvent.ACTION_UP -> {
                val key = resolveKeyAt(event.x, event.y)
                pressedKey = null
                invalidate()
                if (key != null) onKeyTapped?.invoke(key)
            }
            MotionEvent.ACTION_CANCEL -> {
                pressedKey = null
                invalidate()
            }
        }
        return true
    }
}
