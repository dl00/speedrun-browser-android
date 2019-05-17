package danb.speedrunbrowser.api.objects

import java.io.Serializable
import java.util.HashMap

data class Leaderboard(
        val weblink: String? = null,
        val game: String? = null,
        val category: String? = null,
        val level: String? = null,

        val platform: String? = null,
        val region: String? = null,
        val emulators: String? = null,
        val videoOnly: Boolean = false,
        val timing: String? = null,

        val runs: List<LeaderboardRunEntry>? = null,
        val players: HashMap<String, User>? = null
) : Serializable
