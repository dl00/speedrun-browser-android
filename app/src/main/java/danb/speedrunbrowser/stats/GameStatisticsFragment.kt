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

            addChart(ChartOptions(
                    name = getString(R.string.chart_title_volume),
                    description = getString(R.string.chart_desc_volume),
                    identifier = "volume",
                    setLabels = { getString(R.string.chart_legend_volume) },
                    xValueFormat = ::formatMonthYear
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