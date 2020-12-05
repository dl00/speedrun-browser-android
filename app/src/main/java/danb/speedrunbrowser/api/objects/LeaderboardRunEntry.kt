package danb.speedrunbrowser.api.objects

import danb.speedrunbrowser.utils.Util
import java.io.Serializable

data class LeaderboardRunEntry(
        val run: Run,
        val place: Int? = null,
        val obsolete: Boolean = false
) : Serializable {
    val placeName: String
    get() = Util.formatRank(place)
}
