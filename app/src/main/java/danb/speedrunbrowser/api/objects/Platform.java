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

public class Platform implements Serializable {

    public String id;
    public String name;
    public int released;

    public String getName() {
        return name != null ? name : id;
    }

    // need custom serializer/deserializer due to occasionally the object is just a string
    public static class JsonConverter implements JsonSerializer<Platform>, JsonDeserializer<Platform> {
        public JsonElement serialize(Platform src, Type typeOfSrc, JsonSerializationContext context) {
            JsonObject obj = new JsonObject();

            obj.add("id", context.serialize(src.id));
            obj.add("name", context.serialize(src.name));
            obj.add("released", context.serialize(src.released));

            return obj;
        }

        public Platform deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            Platform v = new Platform();

            if(json.isJsonObject()) {
                JsonObject obj = json.getAsJsonObject();

                v.id = obj.get("id").getAsString();
                v.name = obj.get("name").getAsString();
                v.released = obj.get("released").getAsInt();
            }
            else {
                v.id = json.getAsString();
            }


            return v;
        }
    }
}
