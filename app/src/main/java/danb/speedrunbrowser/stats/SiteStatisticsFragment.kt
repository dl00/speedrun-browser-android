package danb.speedrunbrowser.stats

import danb.speedrunbrowser.R
import danb.speedrunbrowser.api.SpeedrunMiddlewareAPI
import danb.speedrunbrowser.utils.Analytics

class SiteStatisticsFragment : StatisticsFragment() {
    override fun onStart() {
        super.onStart()

        Analytics.logItemView(context!!, "chart", "site")

        addChart(ChartOptions(
                name = getString(R.string.chart_title_count_over_time),
                description = getString(R.string.chart_title_count_over_time),
                identifier = "count_over_time",
                setLabels = { getString(R.string.chart_legend_volume) },
                xValueFormat = ::formatMonthYear
        ))

        addChart(ChartOptions(
                name = getString(R.string.chart_title_volume),
                description = getString(R.string.chart_desc_volume),
                identifier = "volume",
                setLabels = { getString(R.string.chart_legend_volume) },
                xValueFormat = ::formatMonthYear
        ))

        setDataSourceAPIResponse(
                SpeedrunMiddlewareAPI.make().getSiteMetrics()
        )
    }
}