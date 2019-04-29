package danb.speedrunbrowser.api.objects;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.google.gson.reflect.TypeToken;

import java.io.Serializable;
import java.lang.reflect.Type;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Run implements Serializable {
    public String id;
    public String weblink;
    public Game game;
    public Level level;
    public Category category;
    public RunVideos videos;
    public String comment;

    public RunStatus status;
    public List<User> players;

    public String date;
    public String submitted;

    public Map<String, String> values;

    public RunTimes times;
    public MediaLink splits;

    public RunSystem system;

    public static class RunSystem {
        public String platform;
        public boolean emulated;
        public String region;
    }

    public static class JsonConverter implements JsonDeserializer<Run> {

        public Run deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            Run v = new Run();

            JsonObject obj = json.getAsJsonObject();

            v.id = obj.get("id").getAsString();
            if(obj.has("weblink"))
                v.weblink = obj.get("weblink").getAsString();
            v.videos = context.deserialize(obj.get("videos"), RunVideos.class);
            if(obj.has("comment") && obj.get("comment").isJsonPrimitive())
                v.comment = obj.get("comment").getAsString();

            v.status = context.deserialize(obj.get("status"), RunStatus.class);

            v.players = context.deserialize(obj.get("players"),
                    new TypeToken<List<User>>(){}.getType());

            if(obj.has("date") && obj.get("date").isJsonPrimitive())
                v.date = obj.get("date").getAsString();

            if(obj.has("submitted") && obj.get("submitted").isJsonPrimitive())
                v.submitted = obj.get("submitted").getAsString();

            v.values = context.deserialize(obj.get("values"),
                    new TypeToken<Map<String, String>>(){}.getType());

            v.times = context.deserialize(obj.get("times"), RunTimes.class);
            v.splits = context.deserialize(obj.get("splits"), MediaLink.class);

            v.system = context.deserialize(obj.get("system"), RunSystem.class);

            if(obj.has("game")) {
                if(obj.get("game").isJsonObject()) {
                    JsonObject gObj = obj.getAsJsonObject("game");
                    if(gObj.has("id"))
                        v.game = context.deserialize(gObj, Game.class);
                    else if(gObj.has("data"))
                        v.game = context.deserialize(gObj.get("data"), Game.class);
                }
                else {
                    Game g = new Game();
                    g.id = obj.get("game").getAsString();
                    v.game = g;
                }
            }

            if(obj.has("category")) {
                if(obj.get("category").isJsonObject()) {
                    JsonObject gObj = obj.getAsJsonObject("category");
                    if(gObj.has("id"))
                        v.category = context.deserialize(gObj, Category.class);
                    else if(gObj.has("data"))
                        v.category = context.deserialize(gObj.get("data"), Category.class);
                }
                else {
                    Category c = new Category();
                    c.id = obj.get("category").getAsString();
                    v.category = c;
                }
            }

            if(obj.get("level") != null) {
                if(obj.get("level").isJsonObject()) {
                    JsonObject gObj = obj.getAsJsonObject("level");
                    if(gObj.has("id"))
                        v.level = context.deserialize(gObj, Level.class);
                    else if(gObj.has("data") && gObj.get("data").isJsonObject())
                        v.level = context.deserialize(gObj.get("data"), Level.class);
                }
                else if(!obj.get("level").isJsonNull()) {
                    Level l = new Level();
                    l.id = obj.get("level").getAsString();
                    v.level = l;
                }
            }

            return v;
        }
    }
}
