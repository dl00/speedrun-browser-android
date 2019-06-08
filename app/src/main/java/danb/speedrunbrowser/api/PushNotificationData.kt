package danb.speedrunbrowser.api

import com.google.gson.Gson

import danb.speedrunbrowser.api.objects.Category
import danb.speedrunbrowser.api.objects.Game
import danb.speedrunbrowser.api.objects.LeaderboardRunEntry
import danb.speedrunbrowser.api.objects.Level

class PushNotificationData(data: Map<String, String>) {
    val oldRun: LeaderboardRunEntry?
    val newRun: LeaderboardRunEntry

    val game: Game
    val category: Category
    val level: Level?

    init {

        val gson = SpeedrunMiddlewareAPI.gson

        oldRun = gson.fromJson(data["old_run"], LeaderboardRunEntry::class.java)
        newRun = gson.fromJson(data["new_run"], LeaderboardRunEntry::class.java)

        game = gson.fromJson(data["game"], Game::class.java)
        category = gson.fromJson(data["category"], Category::class.java)
        level = gson.fromJson(data["level"], Level::class.java)
    }
}
