package danb.speedrunbrowser.api;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class NestedListDeserializer implements JsonDeserializer<List<?>> {
    public List deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext ctx) {
        Type valueType = ((ParameterizedType) typeOfT).getActualTypeArguments()[0];

        List<Object> list = new ArrayList<Object>();

        if(json.isJsonObject()) {
            json = json.getAsJsonObject().get("data");
        }

        for (JsonElement item : json.getAsJsonArray()) {
            list.add(ctx.deserialize(item, valueType));
        }

        return list;
    }
}
