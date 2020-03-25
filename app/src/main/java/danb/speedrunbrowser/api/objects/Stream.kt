package danb.speedrunbrowser.api.objects

import java.io.Serializable

data class Stream(
        val user: User,
        val user_name: String,
        val game: Game?,
        val gg_ids: List<String>,
        val title: String,
        val viewer_count: Int,
        val language: String,
        val thumbnail_url: String,
        val startedAt: String
) : Serializable