package danb.speedrunbrowser.stats

import java.text.DecimalFormat
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*


fun formatMonthYear(v: Float): String {
    val d = Date(v.toLong() * 1000)

    return SimpleDateFormat("MMM yyyy", Locale.getDefault()).format(d)
}

fun formatBigNumber(v: Float): String = NumberFormat.getInstance().format(v)

fun formatTime(t: Float): String {
    return (if (t >= 3600) (t.toInt() / 3600).toString() + "h " else "") + // hours
            (if (t >= 60) ((t % 3600).toInt() / 60).toString() + "m " else "") + // minutes
            DecimalFormat("0.##").format((t % 60).toDouble()) + "s" // seconds
}