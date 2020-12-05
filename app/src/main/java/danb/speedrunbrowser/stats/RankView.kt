package danb.speedrunbrowser.stats

import android.content.Context
import android.view.View
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.marginTop
import androidx.viewpager.widget.ViewPager
import com.google.android.flexbox.FlexboxLayout
import danb.speedrunbrowser.R
import danb.speedrunbrowser.api.SpeedrunMiddlewareAPI
import danb.speedrunbrowser.api.objects.Chart
import danb.speedrunbrowser.api.objects.Ranking
import danb.speedrunbrowser.utils.Util
import danb.speedrunbrowser.utils.ViewPagerAdapter
import danb.speedrunbrowser.views.SimpleTabStrip

class RankView(ctx: Context, val options: RankOptions) : LinearLayout(ctx) {

    private var chartContext: SpeedrunMiddlewareAPI.APIChartDataContext? = null
    private var rankData: Ranking? = null

    init {
        orientation = HORIZONTAL
        
        removeAllViews()

        View.inflate(context, R.layout.content_rank_view, this)

        findViewById<TextView>(R.id.txtLabel).text = options.name
    }

    private val gridPager = findViewById<ViewPager>(R.id.rankPager)

    init {
        applyData()
    }

    fun setData(chartContext: SpeedrunMiddlewareAPI.APIChartDataContext, rankData: Ranking) {
        this.rankData = rankData
        this.chartContext = chartContext
        applyData()
    }

    private fun applyData() {
        val r = rankData ?: return

        val adapter = ViewPagerAdapter()

        // set rows in grid
        for (rank in r.data) {

            val gridView = GridLayout(context)
            gridView.columnCount = 3

            for ((i,row) in rank.value.withIndex()) {
                val basicLp = GridLayout.LayoutParams()
                basicLp.rowSpec = GridLayout.spec(GridLayout.UNDEFINED, GridLayout.CENTER)

                val rankText = TextView(context)
                rankText.layoutParams = basicLp
                rankText.text = Util.formatRank(i + 1)
                rankText.setPadding(0, resources.getDimensionPixelSize(R.dimen.fab_margin), 0, resources.getDimensionPixelSize(R.dimen.fab_margin))

                gridView.addView(rankText)

                val centerLp = GridLayout.LayoutParams(basicLp)
                centerLp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, GridLayout.CENTER, 3.0f)

                val contentView = FlexboxLayout(context)
                contentView.layoutParams = centerLp

                for (v in options.objRender(row.obj))
                    contentView.addView(v)
                gridView.addView(contentView)

                val scoreText = TextView(context)
                scoreText.layoutParams = GridLayout.LayoutParams(basicLp)
                scoreText.text = options.scoreFormat(row.score)
                gridView.addView(scoreText)

                contentView.setOnClickListener {
                    options.onSelected?.invoke(row.obj)
                }
            }

            adapter.views.add(options.setLabels(chartContext!!, rank.key) to gridView)
        }

        gridPager.adapter = adapter

        val tabStrip = findViewById<SimpleTabStrip>(R.id.rankTabStrip)
        if (r.data.size > 1) {
            tabStrip.setup(gridPager)
        }
        else {
            tabStrip.visibility = View.GONE
        }
    }
}