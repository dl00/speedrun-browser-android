package danb.speedrunbrowser.stats

import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentPagerAdapter
import androidx.viewpager.widget.PagerAdapter
import androidx.viewpager.widget.ViewPager
import danb.speedrunbrowser.R
import danb.speedrunbrowser.views.SimpleTabStrip
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers

class TabbedSwitcherView(ctx: Context, val options: TabbedSwitcherOptions) : LinearLayout(ctx), Disposable {

    private val disposables = CompositeDisposable()

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

    override fun isDisposed() = disposables.isDisposed
    override fun dispose() = disposables.dispose()

    private inner class MetricPagerAdapter() : PagerAdapter(), SimpleTabStrip.IconPagerAdapter {

        override fun instantiateItem(container: ViewGroup, position: Int): Any {
            val v = StatisticsView(context)

            for (chart in options.subcharts) {
                v.addChart(chart)
            }

            disposables.add(options.tabs[position].dataSource
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribeOn(Schedulers.io())
                    .subscribe {
                        v.applyData(it.data)
                    })

            container.addView(v)

            return v
        }

        override fun destroyItem(container: ViewGroup, position: Int, obj: Any) {
            container.removeView(obj as StatisticsView)
        }

        override fun isViewFromObject(view: View, obj: Any): Boolean {
            return obj == view
        }

        override fun getCount() = options.tabs.size

        override fun getPageTitle(position: Int) = options.tabs[position].label
        override fun getPageIcon(position: Int): Drawable? {
            return ColorDrawable(resources.getColor(android.R.color.transparent))
        }
    }
}