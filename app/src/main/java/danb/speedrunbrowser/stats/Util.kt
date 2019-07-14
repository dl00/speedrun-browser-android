package danb.speedrunbrowser.stats

import java.text.SimpleDateFormat
import java.util.*

fun formatMonthYear(v: Float): String {
    val d = Date(v.toLong() * 1000)

    return SimpleDateFormat("MMM YYYY", Locale.getDefault()).format(d)
}