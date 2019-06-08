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

data class Platform(
    val id: String,
    val name: String? = null,
    val released: Int? = null
) : Serializable {

    // need custom serializer/deserializer due to occasionally the object is just a string
    class JsonConverter : JsonSerializer<Platform>, JsonDeserializer<Platform> {
        override fun serialize(src: Platform, typeOfSrc: Type, context: JsonSerializationContext): JsonElement {
            val obj = JsonObject()

            obj.add("id", context.serialize(src.id))
            obj.add("name", context.serialize(src.name))
            obj.add("released", context.serialize(src.released))

            return obj
        }

        @Throws(JsonParseException::class)
        override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): Platform {
            return if (json.isJsonObject) {
                val obj = json.asJsonObject

                Platform(
                    id = obj.get("id").asString,
                    name = obj.get("name").asString,
                    released = obj.get("released").asInt
                )
            } else {
                Platform(json.asString)
            }
        }
    }
}
