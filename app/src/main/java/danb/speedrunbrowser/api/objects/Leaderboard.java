package danb.speedrunbrowser.api.objects;

import java.io.Serializable;
import java.util.HashMap;
import java.util.List;

public class Leaderboard implements Serializable {
    public String weblink;
    public String game;
    public String category;
    public String level;

    public String platform;
    public String region;
    public String emulators;
    public boolean videoOnly;
    public String timing;

    public List<LeaderboardRunEntry> runs;
    public HashMap<String, User> players;
}
