package danb.speedrunbrowser.stats

import danb.speedrunbrowser.R
import danb.speedrunbrowser.api.SpeedrunMiddlewareAPI
import danb.speedrunbrowser.api.objects.Game

class PlayerStatisticsActivity : StatisticsActivity() {
    override fun onStart() {
        super.onStart()

        val playerId: String? = intent.getStringExtra(EXTRA_PLAYER_ID)

        if(playerId != null) {

            onDataReadyListener = {
                title = if(it.player?.names != null)
                    it.player.names["international"]
                else
                    it.player?.name
            }

            addChart(ChartOptions(
                    name = getString(R.string.chart_title_favorite_games),
                    description = getString(R.string.chart_desc_favorite_games),
                    identifier = "favorite_games",
                    pieLabels = {
                        (it as Game).names.getValue("international")
                    },
                    setLabels = {""}
            ))

            addChart(ChartOptions(
                    name = getString(R.string.chart_title_volume),
                    description = getString(R.string.chart_desc_volume),
                    identifier = "volume",
                    setLabels = { getString(R.string.chart_legend_volume) },
                    xValueFormat = ::formatMonthYear
            ))

            setDataSourceAPIResponse(
                    SpeedrunMiddlewareAPI.make().getUserMetrics(playerId)
            )
        }
    }

    companion object {
        const val EXTRA_PLAYER_ID = "player_id"
    }
}