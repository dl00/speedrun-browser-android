package danb.speedrunbrowser.stats

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

        val setLabels: Map<String, String> = mapOf(),

        val xValueFormat: ((v: Float) -> String)? = null,
        val yValueFormat: ((v: Float) -> String)? = null,

        val chartListViewHolderSource: ViewHolderSource? = null,

        val orientation: ChartOrientation = ChartOrientation.HORIZONTAL
)