package danb.speedrunbrowser.stats

import danb.speedrunbrowser.api.SpeedrunMiddlewareAPI
import io.reactivex.Observable

data class TabOption(
        val id: String,
        val label: String,
        val dataSource: Observable<SpeedrunMiddlewareAPI.APIChartData>
)

data class TabbedSwitcherOptions(
        val name: String,
        val description: String,
        val identifier: String,
        val subcharts: List<ChartOptions>,

        val options: List<TabOption>

)