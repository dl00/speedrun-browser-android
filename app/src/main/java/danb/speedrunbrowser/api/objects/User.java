package danb.speedrunbrowser.api.objects;

import java.util.Date;
import java.util.HashMap;

class UserNameStyleColor {
    public String light;
    public String dark;
}

class UserNameStyle {
    public String style;
    public UserNameStyleColor color;
    public UserNameStyleColor colorFrom;
    public UserNameStyleColor colorTo;
}

class UserLocation {
    public String code;
    public HashMap<String, String> names;
}

public class User {
    public String id;
    public HashMap<String, String> names;
    public String weblink;
    public UserNameStyle nameStyle;
    public String role;
    public Date signup;
    public UserLocation location;
    public UserLocation region;

    public MediaLink twitch;
    public MediaLink hitbox;
    public MediaLink youtube;
    public MediaLink twitter;
    public MediaLink speedrunslive;

    public String getName() {
        String n = names.get("international");
        return n != null ? n : "? Unknown Name ?";
    }
}