package danb.speedrunbrowser.api.objects

import android.widget.TextView
import java.lang.reflect.Type
import java.io.Serializable
import java.net.URL
import java.util.*

import com.google.gson.*

data class GameMaker(
        val id: String,
        val name: String
) : Serializable {
    class JsonConverter : JsonDeserializer<GameMaker> {

        @Throws(JsonParseException::class)
        override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): GameMaker {

            if (json.isJsonObject) {
                val obj = json.asJsonObject
                return GameMaker(
                        id = obj.get("id").asString,
                        name = obj.get("name").asString
                )
            }
            else {
                return GameMaker(
                        id = json.asString,
                        name = ""
                )
            }
        }
    }
}

data class Game(
    val id: String,
    val names: Map<String, String>? = mapOf(),
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
    val developers: List<GameMaker>? = null,
    val publishers: List<GameMaker>? = null,
    val moderators: HashMap<String, String>? = null,
    val created: Date? = null,

    val categories: List<Category>? = null,
    val levels: List<Level>? = null,

    val assets: GameAssets = GameAssets()
) : Serializable, SearchResultItem {

    override val resolvedName: String
    get() = names?.get("international") ?: "? Unknown Name ?"

    override val type: String
            get() = "game"

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
