package danb.speedrunbrowser.api.objects

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer

import java.io.Serializable
import java.lang.reflect.Type

data class Region(
    val id: String,
    val name: String? = null
) : Serializable {

    // need custom serializer/deserializer due to trophy links
    class JsonConverter : JsonSerializer<Region>, JsonDeserializer<Region> {
        override fun serialize(src: Region, typeOfSrc: Type, context: JsonSerializationContext): JsonElement {
            val obj = JsonObject()

            obj.add("id", context.serialize(src.id))
            obj.add("name", context.serialize(src.name))

            return obj
        }

        @Throws(JsonParseException::class)
        override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): Region {
            return if (json.isJsonObject) {
                val obj = json.asJsonObject

                Region(
                    id = obj.get("id").asString,
                    name = obj.get("name").asString
                )
            } else {
                Region(json.asString)
            }
        }
    }
}
