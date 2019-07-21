package danb.speedrunbrowser.api.objects

import java.io.Serializable
import java.text.DecimalFormat
import java.util.regex.Matcher
import java.util.regex.Pattern

data class RunTimes(
        val primary: String? = null,
        val primary_t: Int = 0,
        val realtime: String? = null,
        val realtimeNoloads: String? = null,
        val ingame: String? = null
) : Serializable {

    fun readTime(ts: String?): Float? {

        if(ts == null)
            return null

        val p = Pattern.compile("PT(([0-9.]+)H)?(([0-9.]+)M)?(([0-9.]+)S)?")
        val m = p.matcher(ts)

        return if (m.find()) {

            var t = 0f

            // hours
            if (m.group(2) != null)
                t += 3600 * java.lang.Float.parseFloat(m.group(2))
            // minutes
            if (m.group(4) != null)
                t += 60 * java.lang.Float.parseFloat(m.group(4))
            // seconds
            if (m.group(6) != null)
                t += java.lang.Float.parseFloat(m.group(6))

            return t
        } else {
            // could not parse time?
            return null
        }
    }

    val time
        get() = format(readTime(primary))

    fun formatRealtimeRuntime(): String? {
        return format(readTime(realtime))
    }

    fun formatRealtimeNoloadsRuntime(): String? {
        return format(readTime(realtimeNoloads))
    }

    fun formatIngameRuntime(): String? {
        return format(readTime(ingame))
    }

    companion object {
        fun format(t: Float?): String? {

            if(t == null)
                return null

            return (if (t >= 3600) (t.toInt() / 3600).toString() + "h " else "") + // hours
                    (if (t >= 60) ((t % 3600).toInt() / 60).toString() + "m " else "") + // minutes
                    DecimalFormat("0.##").format((t % 60).toDouble()) + "s" // seconds
        }
    }
}
