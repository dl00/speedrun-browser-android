package danb.speedrunbrowser.api.objects

import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.data.LineData
import com.google.gson.*
import com.google.gson.reflect.TypeToken
import java.lang.reflect.Type
import java.text.ParseException
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.*

fun fromISO8601UTC(dateStr: String): Date? {
    val df = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.mmm'Z'", Locale.US)
    df.timeZone = TimeZone.getTimeZone("UTC")

    try {
        return df.parse(dateStr)
    } catch (e: ParseException) {
        e.printStackTrace()
    }

    return null
}

data class Chart(
        val item_id: String,
        val item_type: String,
        val chart_type: String,
        val timestamp: Date?,
        val data: Map<String, List<ChartData>>
) {
    // need custom serializer/deserializer due to trophy links
    class JsonConverter : JsonSerializer<Chart>, JsonDeserializer<Chart> {
        override fun serialize(src: Chart, typeOfSrc: Type, context: JsonSerializationContext): JsonElement {
            val obj = JsonObject()

            return obj
        }

        @Throws(JsonParseException::class)
        override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): Chart {

            val obj = json.asJsonObject

            val chartType = obj.getNotNull("chart_type")!!.asString

            val chartDataType = when(chartType) {
                "line" -> object : TypeToken<Map<String, List<LineChartData>>>() {}.type
                "bar" -> object : TypeToken<Map<String, List<BarChartData>>>() {}.type
                "pie" -> object : TypeToken<Map<String, List<PieChartData>>>() {}.type
                else -> null
            }

            return Chart(
                item_id = obj.getNotNull("item_id")!!.asString,
                item_type = obj.getNotNull("item_type")!!.asString,
                chart_type = chartType,
                timestamp = fromISO8601UTC(obj.getNotNull("timestamp")!!.asString),
                data = context.deserialize(obj.get("data"), chartDataType)
            )
        }
    }
}

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

data class PieChartData(
        val x: String,
        val y: Float
)