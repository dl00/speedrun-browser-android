package danb.speedrunbrowser.api.objects;

import android.widget.TextView;

import java.io.Serializable;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

public class Game implements Serializable, SearchResultItem {
    public String id;
    public HashMap<String, String> names;
    public String abbreviation;
    public String weblink;

    public int released;
    public String releaseDate;
    public GameRuleset ruleset;
    public boolean romhack;
    public List<String> gametypes;
    public List<Platform> platforms;
    public List<Region> regions;
    public List<String> genres;
    public List<String> engines;
    public List<String> developers;
    public List<String> publishers;
    public HashMap<String, String> moderators;
    public Date created;

    public List<Category> categories;
    public List<Level> levels;

    public GameAssets assets;

    public String getName() {
        String n = names.get("international");
        return n != null ? n : "? Unknown Name ?";
    }

    public String getTypeName() {
        return "game";
    }

    public void applyTextView(TextView tv) {
        tv.setText(getName());
    }
}
