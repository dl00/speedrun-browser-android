package danb.speedrunbrowser.api.objects;

import java.util.List;

public class Leaderboard {
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
}
