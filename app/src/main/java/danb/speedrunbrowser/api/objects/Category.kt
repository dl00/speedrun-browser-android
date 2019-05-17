package danb.speedrunbrowser.api.objects

import java.io.Serializable

data class Category(
    val id: String,
    val name: String,
    val weblink: String? = null,
    val type: String? = null,
    val rules: String? = null,

    val variables: List<Variable>? = null,

    val miscellaneous: Boolean = false
) : Serializable
