package danb.speedrunbrowser.api.objects;

import java.io.Serializable;
import java.util.List;

public class Run implements Serializable {
    public String id;
    public String weblink;
    public String game;
    public String level;
    public String category;
    public RunVideos videos;
    public String comment;

    public RunStatus status;
    public List<User> players;

    public String date;
    public String submitted;

    public RunTimes times;
    public MediaLink splits;
}
