package danb.speedrunbrowser.api.objects;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

import java.io.Serializable;
import java.lang.reflect.Type;

public class Region implements Serializable {
    public String id;
    public String name;

    // need custom serializer/deserializer due to trophy links
    public static class JsonConverter implements JsonSerializer<Region>, JsonDeserializer<Region> {
        public JsonElement serialize(Region src, Type typeOfSrc, JsonSerializationContext context) {
            JsonObject obj = new JsonObject();

            obj.add("id", context.serialize(src.id));
            obj.add("name", context.serialize(src.name));

            return obj;
        }

        public Region deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            Region v = new Region();

            if(json.isJsonObject()) {
                JsonObject obj = json.getAsJsonObject();

                v.id = obj.get("id").getAsString();
                v.name = obj.get("name").getAsString();
            }
            else {
                v.id = json.getAsString();
            }


            return v;
        }
    }
}
