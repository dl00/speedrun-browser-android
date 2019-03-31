package danb.speedrunbrowser.api;

import com.google.gson.Gson;

import java.util.Map;

import danb.speedrunbrowser.api.objects.Category;
import danb.speedrunbrowser.api.objects.Game;
import danb.speedrunbrowser.api.objects.LeaderboardRunEntry;
import danb.speedrunbrowser.api.objects.Level;

public class PushNotificationData {
    public LeaderboardRunEntry old_run;
    public LeaderboardRunEntry new_run;

    public Game game;
    public Category category;
    public Level level;

    public PushNotificationData(Map<String, String> data) {

        Gson gson = SpeedrunMiddlewareAPI.getGson();

        old_run = gson.fromJson(data.get("old_run"), LeaderboardRunEntry.class);
        new_run = gson.fromJson(data.get("new_run"), LeaderboardRunEntry.class);

        game = gson.fromJson(data.get("game"), Game.class);
        category = gson.fromJson(data.get("category"), Category.class);
        level = gson.fromJson(data.get("level"), Level.class);
    }
}
