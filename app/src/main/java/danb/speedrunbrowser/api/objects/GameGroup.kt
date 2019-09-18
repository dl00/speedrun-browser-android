package danb.speedrunbrowser.api.objects

import android.widget.TextView
import java.io.Serializable
import java.net.URL

data class GameGroup(
        val id: String,
        val name: String
) : SearchResultItem, Serializable {

    override val resolvedName: String
        get() = name

    override val iconUrl: URL?
        get() = null

    override val type: String = ""


    override fun applyTextView(tv: TextView) {
        tv.text = resolvedName
    }
}
