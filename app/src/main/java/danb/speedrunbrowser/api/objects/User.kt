package danb.speedrunbrowser.api.objects

import android.graphics.Rect
import android.graphics.Typeface
import android.widget.TextView

import java.io.Serializable
import java.net.MalformedURLException
import java.net.URL
import java.util.Date
import java.util.HashMap

import danb.speedrunbrowser.utils.Constants

data class UserLocation(
    val code: String,
    val names: Map<String, String>? = null
) : Serializable

data class User(
    val id: String,
    val names: Map<String, String>? = null,
    // guests just have a simple "name" field
    val name: String? = null,
    val weblink: String? = null,
    val nameStyle: UserNameStyle? = null,
    val role: String? = null,
    val signup: Date? = null,
    val location: UserLocation? = null,
    val region: UserLocation? = null,

    val twitch: MediaLink? = null,
    val hitbox: MediaLink? = null,
    val youtube: MediaLink? = null,
    val twitter: MediaLink? = null,
    val speedrunslive: MediaLink? = null,

    val bests: Map<String, UserGameBests>? = null
) : Serializable, SearchResultItem {

    override val type: String
        get() = "runner"

    override val iconUrl: URL
        @Throws(MalformedURLException::class)
        get() = URL(String.format(Constants.AVATAR_IMG_LOCATION, names!!["international"]))

    val isGuest: Boolean
        get() = name != null

    override val resolvedName
        get() = names?.get("international") ?: name ?: id

    // creates a text view with name and appropriate formatting
    override fun applyTextView(tv: TextView) {
        tv.text = resolvedName
        tv.typeface = Typeface.MONOSPACE

        if (nameStyle != null) {
            val bounds = Rect()
            tv.paint.getTextBounds(resolvedName, 0, resolvedName.length, bounds)
            tv.paint.shader = nameStyle.getTextShader(bounds.width().toFloat(), false)
        }
    }

    data class UserGameBests(
        val id: String? = null,
        val names: Map<String, String>? = null,
        val assets: GameAssets? = null,

        val categories: Map<String, UserCategoryBest>? = null
    ) : Serializable {

        val newestRun: LeaderboardRunEntry?
            get() = categories?.maxBy { it.value.newestRun.run.date ?: "" }!!.value.run
    }

    data class UserCategoryBest(
        val id: String,
        val name: String,
        val type: String? = null,

        val levels: HashMap<String, UserLevelBest>? = null,

        val run: LeaderboardRunEntry? = null
    ) : Serializable {
        val newestRun: LeaderboardRunEntry
            get() = run ?: levels?.maxBy { it.value.run.run.date ?: "" }!!.value.run
    }

    data class UserLevelBest(
            val id: String,
            val name: String,

            val run: LeaderboardRunEntry
    ) : Serializable

    companion object {

        fun printPlayerNames(players: List<User>): String {
            val playerNames = StringBuilder()

            playerNames.append(players[0].resolvedName)

            if (players.size == 2)
                playerNames.append(" and ").append(players[1].resolvedName)
            else if (players.size >= 3) {
                for (i in 1 until players.size - 1)
                    playerNames.append(", ").append(players[i].resolvedName)

                playerNames.append(", and ").append(players[players.size - 1].resolvedName)
            }

            return playerNames.toString()
        }
    }
}