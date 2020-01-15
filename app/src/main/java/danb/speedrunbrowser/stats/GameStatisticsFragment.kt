package danb.speedrunbrowser.stats

import danb.speedrunbrowser.R
import danb.speedrunbrowser.api.SpeedrunMiddlewareAPI
import danb.speedrunbrowser.utils.Analytics

class GameStatisticsFragment : StatisticsFragment() {
    override fun onStart() {
        super.onStart()

        val gameId: String? = arguments!!.getString(EXTRA_GAME_ID)

        if(gameId != null) {

            Analytics.logItemView(context!!, "game_chart", gameId)

            onDataReadyListener = {
                activity!!.title = it.game!!.resolvedName
            }

            addMetric(ChartOptions(
                    name = getString(R.string.metric_title_total_runs),
                    description = getString(R.string.metric_desc_total_runs),
                    identifier = "total_run_count",
                    xValueFormat = ::formatBigNumber,
                    setLabels = { "" }
            ))

            addMetric(ChartOptions(
                    name = getString(R.string.metric_title_total_players),
                    description = getString(R.string.metric_desc_total_players),
                    identifier = "total_players",
                    xValueFormat = ::formatBigNumber,
                    setLabels = { "" }
            ))

            addMetric(ChartOptions(
                    name = getString(R.string.metric_title_total_leaderboards),
                    description = getString(R.string.metric_desc_total_leaderboards),
                    identifier = "total_leaderboards",
                    xValueFormat = ::formatBigNumber,
                    setLabels = { "" }
            ))

            addMetric(ChartOptions(
                    name = getString(R.string.metric_title_total_run_time),
                    description = getString(R.string.metric_desc_total_run_time),
                    identifier = "total_run_time",
                    xValueFormat = ::formatTime,
                    setLabels = { "" }
            ))

            addChart(ChartOptions(
                    name = getString(R.string.chart_title_volume),
                    description = getString(R.string.chart_desc_volume),
                    identifier = "volume",
                    setLabels = { getString(R.string.chart_legend_volume) },
                    xValueFormat = ::formatMonthYear
            ))

            addMetric(ChartOptions(
                    name = getString(R.string.metric_title_total_runs_full_game),
                    description = getString(R.string.metric_desc_total_runs),
                    identifier = "full_game_run_count",
                    xValueFormat = ::formatBigNumber,
                    setLabels = { "" }
            ))

            addMetric(ChartOptions(
                    name = getString(R.string.metric_title_total_runs_level),
                    description = getString(R.string.metric_desc_total_runs),
                    identifier = "level_run_count",
                    xValueFormat = ::formatBigNumber,
                    setLabels = { "" }
            ))

            setDataSourceAPIResponse(
                    SpeedrunMiddlewareAPI.make(context!!).getGameMetrics(gameId)
            )
        }
    }

    companion object {
        const val EXTRA_GAME_ID = "game_id"
    }
}