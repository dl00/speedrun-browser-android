package danb.speedrunbrowser.api.objects;

import java.util.List;

public class Run {
    public String id;
    public String weblink;
    public String game;
    public Level level;
    public Category category;
    public RunVideos videos;
    public String comment;

    public RunStatus status;
    public List<User> players;

    public String date;
    public String submitted;

    public RunTimes times;
    public MediaLink splits;
}
