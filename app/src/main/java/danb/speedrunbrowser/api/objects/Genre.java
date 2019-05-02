package danb.speedrunbrowser.api.objects;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import java.io.Serializable;
import java.lang.reflect.Type;

public class Genre implements Serializable {
    public String id;
    public String name;

    public int count;

    public static class JsonConverter implements JsonDeserializer<Genre> {

        public Genre deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            Genre g = new Genre();

            if(json.isJsonPrimitive()) {
                g.id = json.getAsString();
            }
            else {

                JsonObject obj = json.getAsJsonObject();

                g.id = obj.get("id").getAsString();
                g.name = obj.get("name").getAsString();

                if(obj.has("count"))
                    g.count = obj.get("count").getAsNumber().intValue();
            }

            return g;
        }
    }
}
