package danb.speedrunbrowser.api.objects

import java.io.Serializable

data class RunStatus(
        val status: String,
        val reason: String? = null
): Serializable
