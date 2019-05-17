package danb.speedrunbrowser.api.objects

import java.io.Serializable

data class WhatIsEntry(
    val type: String? = null,
    val id: String? = null
): Serializable
