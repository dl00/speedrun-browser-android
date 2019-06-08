package danb.speedrunbrowser.api.objects


import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Shader

import java.io.Serializable

data class UserNameStyleColor(
    val light: String? = null,
    val dark: String? = null
) : Serializable

data class UserNameStyle(
    val style: String, // "solid" or "gradient"
    val color: UserNameStyleColor? = null,
    val colorFrom: UserNameStyleColor? = null,
    val colorTo: UserNameStyleColor? = null
) : Serializable {

    fun getTextShader(size: Float, light: Boolean): Shader {
        val c1: Int
        val c2: Int
        if (style == "solid") {
            c1 = Color.parseColor(if (light) color!!.light else color!!.dark)
            c2 = c1
        } else if (style == "gradient") {
            c1 = Color.parseColor(if (light) colorFrom!!.light else colorFrom!!.dark)
            c2 = Color.parseColor(if (light) colorTo!!.light else colorTo!!.dark)
        } else {
            c1 = Color.WHITE
            c2 = Color.WHITE
        }

        return LinearGradient(0f, 0f, size, 0f, c1, c2, Shader.TileMode.CLAMP)
    }
}
