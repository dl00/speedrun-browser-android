package danb.speedrunbrowser.api

import com.google.gson.Gson

import danb.speedrunbrowser.api.objects.Category
import danb.speedrunbrowser.api.objects.Game
import danb.speedrunbrowser.api.objects.LeaderboardRunEntry
import danb.speedrunbrowser.api.objects.Level

class PushNotificationData(data: Map<String, String>) {
    var old_run: LeaderboardRunEntry
    var new_run: LeaderboardRunEntry

    var game: Game
    var category: Category
    var level: Level

    init {

        val gson = SpeedrunMiddlewareAPI.gson

        old_run = gson.fromJson(data["old_run"], LeaderboardRunEntry::class.java)
        new_run = gson.fromJson(data["new_run"], LeaderboardRunEntry::class.java)

        game = gson.fromJson(data["game"], Game::class.java)
        category = gson.fromJson(data["category"], Category::class.java)
        level = gson.fromJson(data["level"], Level::class.java)
    }
}
