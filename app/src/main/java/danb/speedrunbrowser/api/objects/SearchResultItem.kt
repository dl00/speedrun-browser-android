package danb.speedrunbrowser.api.objects

import android.widget.TextView

import java.net.MalformedURLException
import java.net.URL

interface SearchResultItem {
    val resolvedName: String
    val type: String

    val iconUrl: URL?

    fun applyTextView(tv: TextView)
}
