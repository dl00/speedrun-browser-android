package danb.speedrunbrowser.stats

import danb.speedrunbrowser.R
import danb.speedrunbrowser.api.SpeedrunMiddlewareAPI
import danb.speedrunbrowser.api.objects.makeCategoryNameText

class GameStatisticsActivity : StatisticsActivity() {
    override fun onStart() {
        super.onStart()

        val gameId: String? = intent.getStringExtra(EXTRA_GAME_ID)

        if(gameId != null) {

            onDataReadyListener = {
                title = it.game!!.resolvedName
            }

            addChart(ChartOptions(
                    name = getString(R.string.chart_title_volume),
                    description = getString(R.string.chart_desc_volume),
                    identifier = "volume",
                    setLabels = { getString(R.string.chart_legend_volume) },
                    xValueFormat = ::formatMonthYear
            ))

            setDataSourceAPIResponse(
                    SpeedrunMiddlewareAPI.make().getGameMetrics(gameId)
            )
        }
    }

    companion object {
        const val EXTRA_GAME_ID = "game_id"
    }
}