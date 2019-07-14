package danb.speedrunbrowser.stats

import android.os.Bundle
import android.os.PersistableBundle
import danb.speedrunbrowser.R
import danb.speedrunbrowser.api.SpeedrunMiddlewareAPI

class GameStatisticsActivity : StatisticsActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        /*val gameId: String? = intent.getStringExtra(EXTRA_GAME_ID)

        println("Game statistics started")

        if(gameId != null) {

            println("SETTING DATA")

            addChart(ChartOptions(
                    name = getString(R.string.chart_title_volume),
                    description = getString(R.string.chart_desc_volume),
                    identifier = "volume"
            ))

            setDataSourceAPIResponse(
                    SpeedrunMiddlewareAPI.make().getGameMetrics(gameId)
            )
        }*/
    }

    override fun onStart() {
        super.onStart()

        val gameId: String? = intent.getStringExtra(EXTRA_GAME_ID)

        println("Game statistics started")

        if(gameId != null) {

            println("SETTING DATA")

            addChart(ChartOptions(
                    name = getString(R.string.chart_title_volume),
                    description = getString(R.string.chart_desc_volume),
                    identifier = "volume"
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