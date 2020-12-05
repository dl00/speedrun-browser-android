package danb.speedrunbrowser.stats

import android.view.View
import danb.speedrunbrowser.api.SpeedrunMiddlewareAPI
import danb.speedrunbrowser.api.objects.Chart
import danb.speedrunbrowser.utils.ItemType
import danb.speedrunbrowser.utils.ViewHolderSource

enum class ChartOrientation {
    VERTICAL,
    HORIZONTAL
}

data class ChartOptions(
        val identifier: String,

        val name: String,
        val description: String,

        val setLabels: (ctx: SpeedrunMiddlewareAPI.APIChartDataContext, v: String) -> String,

        val pieLabels: ((v: Any) -> String)? = null,

        val xValueFormat: ((v: Float) -> String) = { it.toString() },
        val yValueFormat: ((v: Float) -> String) = { it.toString() },

        val chartListViewHolderSource: ViewHolderSource? = null,

        val chartListReverse: Boolean = false,

        val chartListOnSelected: ((obj: Any) -> Unit)? = null,

        val orientation: ChartOrientation = ChartOrientation.HORIZONTAL
)

data class RankOptions(
        val identifier: String,

        val name: String,
        val description: String,

        val setLabels: (ctx: SpeedrunMiddlewareAPI.APIChartDataContext, v: String) -> String,

        val objRender: ((obj: Any?) -> List<View>),

        val scoreFormat: ((v: Float) -> String) = { it.toString() },

        val onSelected: ((obj: Any?) -> Unit)? = null
)