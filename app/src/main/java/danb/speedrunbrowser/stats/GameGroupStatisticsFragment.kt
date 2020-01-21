package danb.speedrunbrowser.stats

import danb.speedrunbrowser.R
import danb.speedrunbrowser.api.SpeedrunMiddlewareAPI
import danb.speedrunbrowser.utils.Analytics

class GameGroupStatisticsFragment : StatisticsFragment() {
    override fun onStart() {
        super.onStart()

        val gameGroupId = arguments?.getString(EXTRA_GAME_GROUP_ID) ?: "site"

        Analytics.logItemView(context!!, "game_group_chart", gameGroupId)

        addChart(ChartOptions(
                name = getString(R.string.chart_title_count_over_time),
                description = getString(R.string.chart_title_count_over_time),
                identifier = "count_over_time",
                setLabels = { getString(R.string.chart_legend_volume) },
                xValueFormat = ::formatMonthYear
        ))

        addMetrics(listOf(
            ChartOptions(
                name = getString(R.string.metric_title_total_runs),
                description = getString(R.string.metric_desc_total_runs),
                identifier = "total_run_count",
                xValueFormat = ::formatBigNumber,
                setLabels = { "" }
            ),
            ChartOptions(
                    name = getString(R.string.metric_title_total_players),
                    description = getString(R.string.metric_desc_total_players),
                    identifier = "total_players",
                    xValueFormat = ::formatBigNumber,
                    setLabels = { "" }
            )
        ))

        if (gameGroupId != "site") {
            addMetrics(listOf(
                    ChartOptions(
                            name = getString(R.string.metric_title_total_games),
                            description = getString(R.string.metric_desc_total_games),
                            identifier = "total_players",
                            xValueFormat = ::formatBigNumber,
                            setLabels = { "" }
                    ),
                    ChartOptions(
                            name = getString(R.string.metric_title_total_leaderboards),
                            description = getString(R.string.metric_desc_total_leaderboards),
                            identifier = "total_players",
                            xValueFormat = ::formatBigNumber,
                            setLabels = { "" }
                    )
            ))
        }

        addChart(ChartOptions(
                name = getString(R.string.chart_title_volume),
                description = getString(R.string.chart_desc_volume),
                identifier = "volume",
                setLabels = { getString(R.string.chart_legend_volume) },
                xValueFormat = ::formatMonthYear
        ))

        setDataSourceAPIResponse(
                SpeedrunMiddlewareAPI.make(context!!).getGameGroupMetrics(gameGroupId)
        )
    }

    companion object {
        const val EXTRA_GAME_GROUP_ID = "gg_id"
    }
}