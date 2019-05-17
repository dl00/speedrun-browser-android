package danb.speedrunbrowser.api.objects

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import com.google.gson.reflect.TypeToken

import java.io.Serializable
import java.lang.reflect.Type
import java.net.MalformedURLException
import java.net.URL
import java.util.regex.Matcher
import java.util.regex.Pattern

data class MediaLink(
        val uri: URL,
        val rel: String? = null,
        val width: Int? = null,
        val height: Int? = null
) : Serializable {

    // need custom serializer/deserializer due to occasionally the object is just a string
    class JsonConverter : JsonSerializer<MediaLink>, JsonDeserializer<MediaLink> {
        override fun serialize(src: MediaLink, typeOfSrc: Type, context: JsonSerializationContext): JsonElement {
            val obj = JsonObject()

            obj.addProperty("uri", src.uri.toString())

            return obj
        }

        @Throws(JsonParseException::class)
        override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): MediaLink? {
            try {
                if (json.isJsonObject) {
                    val obj = json.asJsonObject
                    return MediaLink(
                        uri = URL(obj.get("uri").asString),
                        rel = if (obj.has("rel") && !obj.get("rel").isJsonNull) obj.get("rel").asString else null,
                        width = if (obj.has("width") && !obj.get("width").isJsonNull) obj.get("width").asInt else null,
                        height = if (obj.has("height") && !obj.get("height").isJsonNull) obj.get("height").asInt else null
                    )
                }
                else if (json.isJsonPrimitive)
                    return MediaLink(URL(json.asString))

            } catch (e: MalformedURLException) {
                return null
            }

            return null
        }
    }


    // video is always numbers
    // TODO: this is very naive
    // this should not happen unless the pattern match is wrong
    val twitchVideoID: String?
        get() {
            if(!uri.isTwitch)
                return null

            val p = Pattern.compile("\\d{9}|\\d{8}")
            val m = p.matcher(uri.toString())

            return if (!m.find()) null else "v" + m.group()

        }

    // should not happen if the video url was real
    val youtubeVideoID: String?
        get() {
            if(!uri.isYoutube)
                return null

            val f = uri.file.substring(1)

            if (f.indexOf("watch?") == 0) {
                val p = Pattern.compile("v=(.+?)(&|$)")
                val m = p.matcher(f)

                return if (m.find()) {
                    m.group(1)
                } else {
                    null
                }
            } else {
                return f.split("\\?".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()[0]
            }
        }
}

private val URL.isYoutube: Boolean
    get() = (host.contains("youtu.be") || host.contains("youtube.com"))

private val URL.isTwitch: Boolean
    get() = host.contains("twitch.tv")
