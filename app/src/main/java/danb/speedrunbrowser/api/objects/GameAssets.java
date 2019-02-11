package danb.speedrunbrowser.api.objects;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

import java.io.Serializable;
import java.lang.reflect.Type;

public class GameAssets implements Serializable {
    public MediaLink logo;
    public MediaLink coverTiny;
    public MediaLink coverSmall;
    public MediaLink coverMedium;
    public MediaLink coverLarge;
    public MediaLink icon;
    public MediaLink trophy1st;
    public MediaLink trophy2nd;
    public MediaLink trophy3rd;
    public MediaLink trophy4th;
    public MediaLink background;
    public MediaLink foreground;

    // need custom serializer/deserializer due to trophy links
    public static class JsonConverter implements JsonSerializer<GameAssets>, JsonDeserializer<GameAssets> {
        public JsonElement serialize(GameAssets src, Type typeOfSrc, JsonSerializationContext context) {
            JsonObject obj = new JsonObject();

            obj.add("logo", context.serialize(src.logo));
            obj.add("cover-tiny", context.serialize(src.coverTiny));
            obj.add("cover-small", context.serialize(src.coverSmall));
            obj.add("cover-medium", context.serialize(src.coverMedium));
            obj.add("cover-large", context.serialize(src.coverLarge));
            obj.add("icon", context.serialize(src.icon));
            obj.add("trophy-1st", context.serialize(src.trophy1st));
            obj.add("trophy-2nd", context.serialize(src.trophy2nd));
            obj.add("trophy-3rd", context.serialize(src.trophy3rd));
            obj.add("trophy-4th", context.serialize(src.trophy4th));
            obj.add("background", context.serialize(src.background));
            obj.add("foreground", context.serialize(src.foreground));

            return obj;
        }

        public GameAssets deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            GameAssets v = new GameAssets();

            JsonObject obj = json.getAsJsonObject();
            v.logo = context.deserialize(obj.get("logo"), MediaLink.class);
            v.coverTiny = context.deserialize(obj.get("cover-tiny"), MediaLink.class);
            v.coverSmall = context.deserialize(obj.get("cover-small"), MediaLink.class);
            v.coverMedium = context.deserialize(obj.get("cover-medium"), MediaLink.class);
            v.coverLarge = context.deserialize(obj.get("cover-large"), MediaLink.class);
            v.icon = context.deserialize(obj.get("icon"), MediaLink.class);
            v.trophy1st = context.deserialize(obj.get("trophy-1st"), MediaLink.class);
            v.trophy2nd = context.deserialize(obj.get("trophy-2nd"), MediaLink.class);
            v.trophy3rd = context.deserialize(obj.get("trophy-3rd"), MediaLink.class);
            v.trophy4th = context.deserialize(obj.get("trophy-4th"), MediaLink.class);
            v.background = context.deserialize(obj.get("background"), MediaLink.class);
            v.foreground = context.deserialize(obj.get("foreground"), MediaLink.class);

            return v;
        }
    }
}
