package danb.speedrunbrowser.api.objects

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer

import java.io.Serializable
import java.lang.reflect.Type

data class GameAssets(
    val logo: MediaLink? = null,
    val coverTiny: MediaLink? = null,
    val coverSmall: MediaLink? = null,
    val coverMedium: MediaLink? = null,
    val coverLarge: MediaLink? = null,
    val icon: MediaLink? = null,
    val trophy1st: MediaLink? = null,
    val trophy2nd: MediaLink? = null,
    val trophy3rd: MediaLink? = null,
    val trophy4th: MediaLink? = null,
    val background: MediaLink? = null,
    val foreground: MediaLink? = null
) : Serializable {

    // need custom serializer/deserializer due to trophy links
    class JsonConverter : JsonSerializer<GameAssets>, JsonDeserializer<GameAssets> {
        override fun serialize(src: GameAssets, typeOfSrc: Type, context: JsonSerializationContext): JsonElement {
            val obj = JsonObject()

            obj.add("logo", context.serialize(src.logo))
            obj.add("cover-tiny", context.serialize(src.coverTiny))
            obj.add("cover-small", context.serialize(src.coverSmall))
            obj.add("cover-medium", context.serialize(src.coverMedium))
            obj.add("cover-large", context.serialize(src.coverLarge))
            obj.add("icon", context.serialize(src.icon))
            obj.add("trophy-1st", context.serialize(src.trophy1st))
            obj.add("trophy-2nd", context.serialize(src.trophy2nd))
            obj.add("trophy-3rd", context.serialize(src.trophy3rd))
            obj.add("trophy-4th", context.serialize(src.trophy4th))
            obj.add("background", context.serialize(src.background))
            obj.add("foreground", context.serialize(src.foreground))

            return obj
        }

        @Throws(JsonParseException::class)
        override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): GameAssets {

            val obj = json.asJsonObject

            return GameAssets(
                logo = context.deserialize(obj.get("logo"), MediaLink::class.java),
                coverTiny = context.deserialize(obj.get("cover-tiny"), MediaLink::class.java),
                coverSmall = context.deserialize(obj.get("cover-small"), MediaLink::class.java),
                coverMedium = context.deserialize(obj.get("cover-medium"), MediaLink::class.java),
                coverLarge = context.deserialize(obj.get("cover-large"), MediaLink::class.java),
                icon = context.deserialize(obj.get("icon"), MediaLink::class.java),
                trophy1st = context.deserialize(obj.get("trophy-1st"), MediaLink::class.java),
                trophy2nd = context.deserialize(obj.get("trophy-2nd"), MediaLink::class.java),
                trophy3rd = context.deserialize(obj.get("trophy-3rd"), MediaLink::class.java),
                trophy4th = context.deserialize(obj.get("trophy-4th"), MediaLink::class.java),
                background = context.deserialize(obj.get("background"), MediaLink::class.java),
                foreground = context.deserialize(obj.get("foreground"), MediaLink::class.java)
            )
        }
    }
}
