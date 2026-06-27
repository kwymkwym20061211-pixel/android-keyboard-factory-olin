package android.keyboard.template

import android.content.Context
import android.view.View
import android.widget.FrameLayout

/** InputMethodService's internal input-view container does not honor a child's own requested
 * LayoutParams height (it was observed forcing the keyboard to the full screen height
 * regardless). Forcing the measured height here, ignoring whatever heightMeasureSpec the system
 * passes down, is the robust fix used by most custom-keyboard implementations. */
class FixedHeightContainer(context: Context, private val fixedHeightPx: Int) : FrameLayout(context) {

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val exactHeightSpec = MeasureSpec.makeMeasureSpec(fixedHeightPx, MeasureSpec.EXACTLY)
        super.onMeasure(widthMeasureSpec, exactHeightSpec)
        setMeasuredDimension(measuredWidth, fixedHeightPx)
    }

    fun setContent(view: View) {
        removeAllViews()
        addView(view, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }
}
