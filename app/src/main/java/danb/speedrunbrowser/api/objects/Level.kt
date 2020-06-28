package danb.speedrunbrowser.api.objects

import java.io.Serializable

data class Level(
    var id: String,
    var name: String?,

    val variables: List<Variable>? = null
) : Serializable
