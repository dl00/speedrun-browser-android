package danb.speedrunbrowser.stats

import danb.speedrunbrowser.R
import danb.speedrunbrowser.api.SpeedrunMiddlewareAPI
import danb.speedrunbrowser.api.objects.Game
import danb.speedrunbrowser.utils.Analytics

class PlayerStatisticsFragment : StatisticsFragment() {
    override fun onStart() {
        super.onStart()

        val playerId: String? = arguments!!.getString(ARG_PLAYER_ID)

        if(playerId != null) {

            Analytics.logItemView(context!!, "player_chart", playerId)

            onDataReadyListener = {
                activity!!.title = if(it.player?.names != null)
                    it.player.names["international"]
                else
                    it.player?.name
            }

            addChart(ChartOptions(
                    name = getString(R.string.chart_title_favorite_games),
                    description = getString(R.string.chart_desc_favorite_games),
                    identifier = "favorite_games",
                    pieLabels = {
                        (it as Game).resolvedName
                    },
                    setLabels = {""}
            ))

            addMetric(ChartOptions(
                    name = getString(R.string.metric_title_total_runs),
                    description = getString(R.string.metric_desc_total_runs),
                    identifier = "total_run_count",
                    xValueFormat = ::formatBigNumber,
                    setLabels = { "" }
            ))

            addChart(ChartOptions(
                    name = getString(R.string.chart_title_volume),
                    description = getString(R.string.chart_desc_volume),
                    identifier = "volume",
                    setLabels = { getString(R.string.chart_legend_volume) },
                    xValueFormat = ::formatMonthYear
            ))

            /*addMetric(ChartOptions(
                    name = getString(R.string.metric_title_total_leaderboards),
                    description = getString(R.string.metric_desc_total_leaderboards),
                    identifier = "total_leaderboards",
                    xValueFormat = ::formatBigNumber,
                    setLabels = { "" }
            ))*/

            addMetric(ChartOptions(
                    name = getString(R.string.metric_title_total_run_time),
                    description = getString(R.string.metric_desc_total_run_time),
                    identifier = "total_run_time",
                    xValueFormat = ::formatTime,
                    setLabels = { "" }
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
                    SpeedrunMiddlewareAPI.make(context!!).getUserMetrics(playerId)
            )
        }
    }

    companion object {
        const val ARG_PLAYER_ID = "player_id"
    }
}