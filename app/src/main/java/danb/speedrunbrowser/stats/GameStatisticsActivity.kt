package danb.speedrunbrowser.stats

import android.os.Bundle
import android.os.PersistableBundle
import danb.speedrunbrowser.R

class GameStatisticsActivity : StatisticsActivity() {
    override fun onCreate(savedInstanceState: Bundle?, persistentState: PersistableBundle?) {
        super.onCreate(savedInstanceState, persistentState)

        addChart(ChartOptions(
            name = getString(R.string.chart_title_volume),
            description = getString(R.string.chart_desc_volume),
            identifier = "volume"
        ))
    }


}