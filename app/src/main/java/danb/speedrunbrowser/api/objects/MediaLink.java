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
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MediaLink implements Serializable {
    public String rel;
    public URL uri;
    public int width;
    public int height;

    public boolean isYoutube() {
        return uri.getHost().contains("youtu.be") || uri.getHost().contains("youtube.com");
    }

    public String getYoutubeVideoID() {
        if(!isYoutube())
            return null;

        String f = uri.getFile().substring(1);

        if(f.indexOf("watch?") == 0) {
            Pattern p = Pattern.compile("v=(.+?)(&|$)");
            Matcher m = p.matcher(f);

            if(m.find()) {
                return m.group(1);
            }
            else {
                // should not happen if the video url was real
                return "";
            }
        }
        else {
            return f.split("\\?")[0];
        }
    }

    public boolean isTwitch() {
        return uri.getHost().contains("twitch.tv");
    }

    public String getTwitchVideoID() {
        if(!isTwitch())
            return null;

        // video is always 9 numbers
        Pattern p = Pattern.compile("\\d{9}");
        Matcher m = p.matcher(uri.toString());

        if(!m.find())
            // this should not happen unless the patterm match is wrong
            return null;

        return "v" + m.group();
    }

    // need custom serializer/deserializer due to occasionally the object is just a string
    public static class JsonConverter implements JsonSerializer<MediaLink>, JsonDeserializer<MediaLink> {
        public JsonElement serialize(MediaLink src, Type typeOfSrc, JsonSerializationContext context) {
            JsonObject obj = new JsonObject();

            obj.addProperty("uri", src.uri.toString());

            return obj;
        }

        public MediaLink deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            MediaLink v = new MediaLink();

            try {
                if(json.isJsonNull())
                    return null;
                if(json.isJsonObject()) {
                    JsonObject obj = json.getAsJsonObject();

                    v.uri = new URL(obj.get("uri").getAsString());
                    if(obj.has("rel") && !obj.get("rel").isJsonNull())
                        v.rel = obj.get("rel").getAsString();
                    if(obj.has("width") && !obj.get("width").isJsonNull())
                        v.width = obj.get("width").getAsInt();
                    if(obj.has("height") && !obj.get("height").isJsonNull())
                        v.height = obj.get("height").getAsInt();

                }
                else if(json.isJsonPrimitive()) {
                    v.uri = new URL(json.getAsString());
                }
            }
            catch(MalformedURLException e) {
                return null;
            }

            return v;
        }
    }
}