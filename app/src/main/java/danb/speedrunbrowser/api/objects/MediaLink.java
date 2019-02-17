package danb.speedrunbrowser.api.objects;

import java.io.Serializable;
import java.net.URL;
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
            Pattern p = Pattern.compile("v=(.+)?&?");
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
            return f;
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
}