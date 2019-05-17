package danb.speedrunbrowser.api

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement

import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.util.ArrayList

class NestedListDeserializer : JsonDeserializer<List<*>> {
    override fun deserialize(json: JsonElement, typeOfT: Type, ctx: JsonDeserializationContext): List<*> {
        var json = json
        val valueType = (typeOfT as ParameterizedType).actualTypeArguments[0]

        val list = ArrayList<Any>()

        if (json.isJsonObject) {
            json = json.asJsonObject.get("data")
        }

        for (item in json.asJsonArray) {
            list.add(ctx.deserialize(item, valueType))
        }

        return list
    }
}
