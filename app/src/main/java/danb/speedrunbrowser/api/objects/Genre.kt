package danb.speedrunbrowser.api.objects

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParseException

import java.io.Serializable
import java.lang.reflect.Type

data class Genre(
    val id: String,
    val name: String = "",

    val count: Int = 0
) : Serializable {

    class JsonConverter : JsonDeserializer<Genre> {

        @Throws(JsonParseException::class)
        override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): Genre {
            if (json.isJsonPrimitive) {
                return Genre(json.asString)
            } else {

                val obj = json.asJsonObject

                return Genre(
                    id = obj.get("id").asString,
                    name = obj.get("name").asString,

                    count = if(obj.has("count"))
                        obj.get("count").asNumber.toInt()
                    else
                        0
                )
            }
        }
    }
}
