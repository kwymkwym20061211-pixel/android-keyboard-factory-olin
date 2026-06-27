package android.keyboard.template

import android.content.Context
import android.view.View
import android.widget.FrameLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/** InputMethodService's internal input-view container does not honor a child's own requested
 * LayoutParams height (it was observed forcing the keyboard to the full screen height
 * regardless). Forcing the measured height here, ignoring whatever heightMeasureSpec the system
 * passes down, is the robust fix used by most custom-keyboard implementations.
 *
 * Targeting API 35+ also makes the window draw edge-to-edge by default, so without accounting
 * for the navigation bar inset ourselves the bottom row of keys ends up underneath (and
 * untappable behind) the nav bar/gesture bar. The content view is kept at [fixedHeightPx] minus
 * that inset and pinned to the top of this container, leaving the inset strip empty at the
 * bottom instead of overlapping it. */
class FixedHeightContainer(context: Context, private val fixedHeightPx: Int) : FrameLayout(context) {

    private var bottomInsetPx = 0

    init {
        ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
            val newInset = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            if (newInset != bottomInsetPx) {
                bottomInsetPx = newInset
                requestLayout()
            }
            insets
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val contentHeightPx = (fixedHeightPx - bottomInsetPx).coerceAtLeast(0)
        val contentHeightSpec = MeasureSpec.makeMeasureSpec(contentHeightPx, MeasureSpec.EXACTLY)
        for (i in 0 until childCount) {
            getChildAt(i).measure(widthMeasureSpec, contentHeightSpec)
        }
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), fixedHeightPx)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            child.layout(0, 0, child.measuredWidth, child.measuredHeight)
        }
    }

    fun setContent(view: View) {
        removeAllViews()
        addView(view)
    }
}
