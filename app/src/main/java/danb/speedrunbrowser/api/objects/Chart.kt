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

    val datasets: List<String>
        get() = data.keys.sorted().reversed()

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
            val itemType = obj.getNotNull("item_type")!!.asString

            var dataMap = mutableMapOf<String, List<ChartData>>()

            val chartData = obj.getNotNull("data")!!.asJsonObject

            for(entry in chartData.entrySet()) {
                dataMap[entry.key] =
                        entry.value.asJsonArray.map { ChartData.deserialize(it, context, itemType) }
            }

            return Chart(
                item_id = obj.getNotNull("item_id")!!.asString,
                item_type = obj.getNotNull("item_type")!!.asString,
                chart_type = chartType,
                timestamp = fromISO8601UTC(obj.getNotNull("timestamp")!!.asString),
                data = dataMap
            )
        }
    }
}

data class ChartData(
        val x: Float,
        val y: Float,
        val obj: Any?
) {
    companion object {
        fun deserialize(json: JsonElement, context: JsonDeserializationContext, itemType: String): ChartData {

            val obj = json.asJsonObject

            val t = when(itemType) {
                "games" -> Game::class.java
                "users" -> User::class.java
                "runs" -> Run::class.java
                else -> null
            }

            return ChartData(
                x = context.deserialize(obj.get("x"), Float::class.java),
                y = context.deserialize(obj.get("y"), Float::class.java),
                obj = if(t == null || obj.get("obj")?.isJsonNull != false) null else context.deserialize(obj.getNotNull("obj"), t) as Any
            )
        }
    }
}