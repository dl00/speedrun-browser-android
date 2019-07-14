package danb.speedrunbrowser.stats

import danb.speedrunbrowser.R
import danb.speedrunbrowser.api.SpeedrunMiddlewareAPI

class LeaderboardStatisticsActivity : StatisticsActivity() {
    override fun onStart() {
        super.onStart()

        val leaderboardId: String? = intent.getStringExtra(EXTRA_LEADERBOARD_ID)

        if(leaderboardId != null) {


            addChart(ChartOptions(
                    name = getString(R.string.chart_title_wrs),
                    description = getString(R.string.chart_desc_wrs),
                    identifier = "wrs",
                    setLabels = { v -> chartData!!.category?.variables?.find { it.id == v }?.name ?: v },
                    xValueFormat = ::formatMonthYear
            ))

            addChart(ChartOptions(
                    name = getString(R.string.chart_title_volume),
                    description = getString(R.string.chart_desc_volume),
                    identifier = "distribution",
                    setLabels = { v -> chartData!!.category?.variables?.find { it.id == v }?.name ?: v },
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
                    SpeedrunMiddlewareAPI.make().getLeaderboardMetrics(leaderboardId)
            )
        }
    }

    companion object {
        const val EXTRA_LEADERBOARD_ID = "game_id"
    }
}