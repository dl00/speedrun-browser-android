package danb.speedrunbrowser.api.objects

import java.io.Serializable

data class Level(
    var id: String,
    var name: String? = null
) : Serializable
