package danb.speedrunbrowser.api.objects

import com.github.mikephil.charting.data.LineData
import com.google.gson.JsonObject
import java.util.*

data class Chart(
        val item_id: String,
        val item_type: String,
        val chart_type: String,
        val timestamp: Date,
        val data: Map<String, List<ChartData>>
)

sealed class ChartData

data class LineChartData(
        val x: Float,
        val y: Float,
        val obj: JsonObject
): ChartData()

data class BarChartData(
        val x: Float,
        val y: Float
): ChartData()

