package danb.speedrunbrowser.api.objects

import java.io.Serializable
import java.util.HashMap

data class Leaderboard(
        val game: String,
        val category: String,
        val level: String? = null,

        val weblink: String? = null,

        val platform: String? = null,
        val region: String? = null,
        val emulators: String? = null,
        val videoOnly: Boolean = false,
        val timing: String? = null,

        val runs: List<LeaderboardRunEntry>? = null,
        val players: Map<String, User>? = null
) : Serializable {
    companion object {
        val EMPTY_LEADERBOARD = Leaderboard(
                game = "",
                category =  "",
                runs = listOf()
        )
    }
}
