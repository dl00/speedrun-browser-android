package danb.speedrunbrowser.api.objects

import android.widget.TextView

import java.io.Serializable
import java.net.URL
import java.util.Date

data class Game(
    val id: String,
    val names: Map<String, String> = mapOf(),
    val abbreviation: String = "",
    val weblink: String = "",

    val released: Int = 0,
    val releaseDate: String? = null,
    val ruleset: GameRuleset? = null,
    val romhack: Boolean = false,
    val gametypes: List<String>? = null,
    val platforms: List<Platform>? = null,
    val regions: List<Region>? = null,
    val genres: List<Genre>? = null,
    val engines: List<String>? = null,
    val developers: List<String>? = null,
    val publishers: List<String>? = null,
    val moderators: HashMap<String, String>? = null,
    val created: Date? = null,

    val categories: List<Category>? = null,
    val levels: List<Level>? = null,

    val assets: GameAssets = GameAssets()
) : Serializable, SearchResultItem {

    override val resolvedName: String
    get() = names["international"] ?: "? Unknown Name ?"

    override val type = "game"

    override val iconUrl: URL?
        get() = assets.coverLarge?.uri ?: assets.icon!!.uri

    override fun applyTextView(tv: TextView) {
        tv.text = resolvedName
    }

    fun shouldShowPlatformFilter(): Boolean {
        return platforms != null && platforms.size > 1
    }

    fun shouldShowRegionFilter(): Boolean {
        return regions != null && regions.size > 1
    }
}
