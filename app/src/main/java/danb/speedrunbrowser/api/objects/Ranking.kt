package danb.speedrunbrowser.api.objects

import com.google.gson.*
import java.lang.reflect.Type
import java.util.*

data class Ranking(
        val item_id: String,
        val item_type: String,
        val timestamp: Date?,
        val data: Map<String, List<RankData>>
) {
    // need custom serializer/deserializer due to trophy links
    class JsonConverter : JsonSerializer<Ranking>, JsonDeserializer<Ranking> {
        override fun serialize(src: Ranking, typeOfSrc: Type, context: JsonSerializationContext): JsonElement {
            return JsonObject()
        }

        @Throws(JsonParseException::class)
        override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): Ranking {

            val obj = json.asJsonObject

            val itemType = obj.getNotNull("item_type")!!.asString

            val dataMap = mutableMapOf<String, List<RankData>>()

            val rankData = obj.getNotNull("data")!!.asJsonObject

            for(entry in rankData.entrySet()) {
                dataMap[entry.key] =
                        entry.value.asJsonArray.map {
                            RankData.deserialize(it, context, itemType)
                        }
            }

            return Ranking(
                    item_id = obj.getNotNull("item_id")!!.asString,
                    item_type = obj.getNotNull("item_type")!!.asString,
                    timestamp = fromISO8601UTC(obj.getNotNull("timestamp")!!.asString),
                    data = dataMap
            )
        }
    }
}

data class RankData(
        val score: Float,
        val obj: Any?
) {
    companion object {
        fun deserialize(json: JsonElement, context: JsonDeserializationContext, itemType: String): RankData {

            val obj = json.asJsonObject

            val t = when(itemType) {
                "games" -> Game::class.java
                "users" -> User::class.java
                "runs" -> Run::class.java
                "leaderboards" -> Run::class.java
                else -> null
            }

            return RankData(
                    score = context.deserialize(obj.get("score"), Float::class.java),
                    obj = if(t == null || obj.get("obj")?.isJsonNull != false) null else context.deserialize(obj.getNotNull("obj"), t) as Any
            )
        }
    }
}