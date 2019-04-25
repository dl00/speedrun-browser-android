package danb.speedrunbrowser.api.objects;

import android.graphics.Rect;
import android.graphics.Typeface;
import android.widget.TextView;

import java.io.Serializable;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

import danb.speedrunbrowser.utils.Constants;

class UserLocation implements Serializable {
    public String code;
    public HashMap<String, String> names;
}

public class User implements Serializable, SearchResultItem {
    public String id;
    public HashMap<String, String> names;
    // guests just have a simple "name" field
    public String name;
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

    public HashMap<String, UserGameBests> bests;

    public String getName() {
        if(names != null) {
            return names.get("international");
        }
        else if(name != null) {
            return name;
        }
        else {
            return id;
        }
    }

    public static String printPlayerNames(List<User> players) {
        StringBuilder playerNames = new StringBuilder();

        playerNames.append(players.get(0).getName());

        if(players.size() == 2)
            playerNames.append(" and ").append(players.get(1).getName());

        else if(players.size() >= 3) {
            for(int i = 1;i < players.size() - 1;i++)
                playerNames.append(", ").append(players.get(i).getName());

            playerNames.append(", and ").append(players.get(players.size() - 1).getName());
        }

        return playerNames.toString();
    }

    public String getTypeName() {
        return "runner";
    }

    public URL getIconUrl() throws MalformedURLException {
        return new URL(String.format(Constants.AVATAR_IMG_LOCATION, names.get("international")));
    }

    // creates a text view with name and appropriate formatting
    public void applyTextView(TextView tv) {
        String pname = getName();

        tv.setText(pname);
        tv.setTypeface(Typeface.MONOSPACE);

        //tv.setShadowLayer(5, 0, 0, Color.BLACK);

        if(nameStyle != null) {
            Rect bounds = new Rect();
            tv.getPaint().getTextBounds(getName(), 0, pname.length(), bounds);

            tv.getPaint().setShader(nameStyle.getTextShader(bounds.width(), false));
        }
    }

    public boolean isGuest() {
        return name != null;
    }

    public class UserGameBests implements Serializable {
        public String id;
        public HashMap<String, String> names;
        public GameAssets assets;

        public HashMap<String, UserCategoryBest> categories;

        public LeaderboardRunEntry getNewestRun() {
            LeaderboardRunEntry newest = null;

            for(UserCategoryBest bc : categories.values()) {
                if(bc.levels != null) {
                    for(UserLevelBest bl : bc.levels.values()) {
                        if(bl.run.run.date != null && (newest == null || bl.run.run.date.compareTo(newest.run.date) > 0))
                            newest = bl.run;
                    }
                }
                else if(bc.run.run.date != null && (newest == null || bc.run.run.date.compareTo(newest.run.date) > 0))
                    newest = bc.run;
            }

            return newest;
        }
    }

    public class UserCategoryBest implements Serializable {
        public String id;
        public String name;
        public String type;

        public HashMap<String, UserLevelBest> levels;

        public LeaderboardRunEntry run;
    }

    public class UserLevelBest implements Serializable {
        public String id;
        public String name;

        public LeaderboardRunEntry run;
    }
}