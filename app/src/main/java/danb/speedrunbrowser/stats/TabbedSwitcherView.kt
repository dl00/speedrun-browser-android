package danb.speedrunbrowser.stats

import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.view.View
import android.widget.LinearLayout
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentPagerAdapter
import androidx.viewpager.widget.ViewPager
import danb.speedrunbrowser.R
import danb.speedrunbrowser.views.SimpleTabStrip

class TabbedSwitcherView(ctx: Context, val fragmentManager: FragmentManager, val options: TabbedSwitcherOptions) : LinearLayout(ctx) {
    init {
        orientation = HORIZONTAL
        removeAllViews()

        View.inflate(context, R.layout.content_tabbed_switcher, this)
    }

    private val tabView = findViewById<SimpleTabStrip>(R.id.tabs)
    private val pagerView = findViewById<ViewPager>(R.id.pager)

    init {
        pagerView.adapter = MetricPagerAdapter()
        tabView.setup(pagerView)
    }

    private inner class MetricPagerAdapter() : FragmentPagerAdapter(fragmentManager, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT), SimpleTabStrip.IconPagerAdapter {
        override fun getItem(position: Int) = MetricPageFragment(position, options)
        override fun getPageTitle(position: Int) = options.tabs[position].label
        override fun getCount() = options.tabs.size

        override fun getPageIcon(position: Int): Drawable? {
            return ColorDrawable(resources.getColor(android.R.color.transparent))
        }
    }

    // must be a public static class for android
    class MetricPageFragment(private val idx: Int, private val options: TabbedSwitcherOptions) : StatisticsFragment() {
        override fun onStart() {
            super.onStart()

            for (chart in options.subcharts) {
                addChart(chart)
            }

            setDataSourceAPIResponse(options.tabs[idx].dataSource)
        }
    }
}