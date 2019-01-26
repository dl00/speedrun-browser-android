package danb.speedrunbrowser.api.objects;

import java.util.List;

public class Leaderboard {
    public String weblink;
    public Game game;
    public Category category;
    public Level level;

    public String platform;
    public String region;
    public String emulators;
    public boolean videoOnly;
    public String timing;

    public List<LeaderboardRunEntry> runs;
}
