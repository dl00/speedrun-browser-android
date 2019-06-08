package danb.speedrunbrowser.api.objects

import com.google.gson.*
import com.google.gson.reflect.TypeToken

import java.io.Serializable
import java.lang.reflect.Type
import java.net.MalformedURLException
import java.net.URL
import java.util.HashMap


fun JsonObject.getNotNull(prop: String): JsonElement? {
    val p = get(prop)
    return if(p == null || p.isJsonNull) null else p
}

data class RunSystem(
    val platform: String? = null,
    val emulated: Boolean = false,
    val region: String? = null
): Serializable

data class Run(
    val id: String,
    val weblink: String? = null,
    val game: Game? = null,
    val level: Level? = null,
    val category: Category? = null,
    val videos: RunVideos? = null,
    val comment: String? = null,

    val status: RunStatus? = null,
    val players: List<User>? = null,

    val date: String? = null,
    val submitted: String? = null,

    val values: Map<String, String>? = null,

    val times: RunTimes? = null,
    val splits: MediaLink? = null,

    val system: RunSystem? = null
) : Serializable {

    class JsonConverter : JsonDeserializer<Run> {

        @Throws(JsonParseException::class)
        override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): Run {
            val obj = json.asJsonObject

            return Run(
                obj.get("id").asString,
                weblink = obj.getNotNull("weblink")?.asString,
                videos = context.deserialize(obj.get("videos"), RunVideos::class.java),
                comment = obj.getNotNull("comment")?.asString,
                status = context.deserialize(obj.get("status"), RunStatus::class.java),
                players = context.deserialize(obj.get("players"), object : TypeToken<List<User>>() {}.type),
                date = obj.getNotNull("date")?.asString,
                submitted = obj.getNotNull("submitted")?.asString,
                values = context.deserialize(obj.get("values"), object : TypeToken<Map<String, String>>() {}.type),
                times = context.deserialize(obj.get("times"), RunTimes::class.java),
                splits = context.deserialize(obj.get("splits"), MediaLink::class.java),
                system = context.deserialize(obj.get("system"), RunSystem::class.java),

                game = when {
                    obj["game"]?.isJsonPrimitive == true -> Game(obj["game"].asString)
                    obj["game"] != null -> context.deserialize(obj["game"], Game::class.java)
                    else -> null
                },
                category = when {
                    obj["category"]?.isJsonPrimitive == true -> Category(obj["category"].asString, "")
                    obj["category"] != null -> context.deserialize(obj["category"], Category::class.java)
                    else -> null
                },
                level = when {
                    obj["level"]?.isJsonPrimitive == true -> Level(obj["level"].asString, "")
                    obj["level"] != null -> context.deserialize(obj["level"], Level::class.java)
                    else -> null
                }
            )
        }
    }
}
