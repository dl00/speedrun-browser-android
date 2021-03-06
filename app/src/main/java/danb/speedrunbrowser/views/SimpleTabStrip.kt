package danb.speedrunbrowser.views

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat

import androidx.viewpager.widget.ViewPager
import danb.speedrunbrowser.R
import kotlin.math.floor

class SimpleTabStrip(context: Context, attrs: AttributeSet) : FrameLayout(context, attrs), ViewPager.OnPageChangeListener {

    private val mHsv = HorizontalScrollView(context)
    private val mLayout = LinearLayout(context)

    private var mPager: ViewPager? = null

    private var mHighlightTab = 0

    init {

        foregroundGravity = Gravity.CENTER

        mHsv.isHorizontalScrollBarEnabled = false
        mHsv.isFocusable = false

        val lp = LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        )
        lp.gravity = Gravity.CENTER
        mHsv.layoutParams = lp

        mLayout.orientation = LinearLayout.HORIZONTAL

        mHsv.addView(mLayout)

        addView(mHsv)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            mHsv.isFocusable = false
        }
    }

    fun setup(vp: ViewPager) {
        mPager = vp
        mPager!!.addOnPageChangeListener(this)

        applyTabs()

        onPageSelected(0)
    }

    private fun styleTab(tv: TextView) {
        tv.setTypeface(tv.typeface, Typeface.BOLD)
        tv.isAllCaps = true

        tv.height = resources.getDimensionPixelSize(R.dimen.tab_height)
        tv.gravity = Gravity.CENTER_VERTICAL

        tv.setPadding(resources.getDimensionPixelSize(R.dimen.half_fab_margin), 0, resources.getDimensionPixelSize(R.dimen.half_fab_margin), 0)

        val lp = LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.leftMargin = resources.getDimensionPixelSize(R.dimen.half_fab_margin)
        lp.rightMargin = resources.getDimensionPixelSize(R.dimen.half_fab_margin)
        tv.layoutParams = lp
    }

    private fun applyTabs() {
        mLayout.removeAllViews()

        val adapter = mPager!!.adapter ?: return

        for (i in 0 until adapter.count) {
            val tv = TextView(context)

            tv.text = adapter.getPageTitle(i)
            styleTab(tv)

            if (adapter is IconPagerAdapter) {
                val icon = (adapter as IconPagerAdapter).getPageIcon(i)

                val iv = ImageView(context)
                iv.setImageDrawable(icon)

                val lp = LinearLayout.LayoutParams(
                        context.resources.getDimensionPixelSize(R.dimen.tab_icon_width),
                        context.resources.getDimensionPixelSize(R.dimen.tab_icon_height)
                )

                iv.layoutParams = lp

                iv.scaleType = ImageView.ScaleType.FIT_CENTER

                val verticalLayout = LinearLayout(context)
                verticalLayout.orientation = LinearLayout.VERTICAL
                verticalLayout.gravity = Gravity.CENTER
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    verticalLayout.foreground = resources.getDrawable(R.drawable.clickable_item)
                }

                verticalLayout.addView(iv)
                verticalLayout.addView(tv)

                verticalLayout.setOnClickListener { mPager!!.currentItem = i }

                mLayout.addView(verticalLayout)
            } else {
                tv.setOnClickListener { mPager!!.currentItem = i }

                mLayout.addView(tv)
            }
        }
    }

    private fun getCenterScrollPosition(pos: Int): Int {
        val child = mLayout.getChildAt(pos)

        return child.left + child.width / 2 - mHsv.width / 2
    }

    private fun setScroll(pos: Int, offset: Float) {
        // we want the tab to be as center aligned as possible.
        val x1 = getCenterScrollPosition(pos)

        var x2 = x1
        if (mLayout.childCount > pos + 1)
            x2 = getCenterScrollPosition(pos + 1)

        mHsv.scrollTo(x1 + floor((x2 - x1).toFloat() * offset).toInt(), 0)
    }

    override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {
        setScroll(position, positionOffset)
    }

    override fun onPageSelected(position: Int) {
        mLayout.getChildAt(mHighlightTab).background = ColorDrawable(ContextCompat.getColor(context, android.R.color.transparent))
        mHighlightTab = position
        mLayout.getChildAt(mHighlightTab).background = ColorDrawable(ContextCompat.getColor(context, R.color.colorAccent))
    }

    override fun onPageScrollStateChanged(state: Int) {}

    interface IconPagerAdapter {
        fun getPageIcon(position: Int): Drawable?
    }
}
