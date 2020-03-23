package danb.speedrunbrowser.api.objects

import java.io.Serializable

data class Stream(
    val userId: String,
    val gameId: String,
    val ggIds: List<String>,
    val title: String,
    val viewerCount: Int,
    val language: String,
    val thumbnailUrl: String,
    val startedAt: String

) : Serializable