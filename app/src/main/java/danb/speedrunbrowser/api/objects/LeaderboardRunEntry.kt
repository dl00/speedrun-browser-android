package danb.speedrunbrowser.api.objects

import java.io.Serializable

data class LeaderboardRunEntry(
        val run: Run,
        val place: Int? = null
) : Serializable {
    val placeName: String
        get() {
            if(place == null)
                return "-"

            if (place / 10 % 10 == 1)
                return place.toString() + "th"

            return when (place % 10) {
                1 -> place.toString() + "st"
                2 -> place.toString() + "nd"
                3 -> place.toString() + "rd"
                else -> place.toString() + "th"
            }
        }
}
