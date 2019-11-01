package danb.speedrunbrowser.views

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.webkit.WebChromeClient
import danb.speedrunbrowser.R

class CustomWebChromeClient(val context: Context) : WebChromeClient() {
    override fun getDefaultVideoPoster(): Bitmap? {
        return if (super.getDefaultVideoPoster() == null) {
            BitmapFactory.decodeResource(context.resources,
                    R.drawable.speedrun_com_trophy)
        } else {
            super.getDefaultVideoPoster()
        }
    }
}
