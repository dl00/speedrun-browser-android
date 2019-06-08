package danb.speedrunbrowser.utils

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.view.View
import android.widget.ImageView

import io.reactivex.functions.Consumer

class ImageViewPlacerConsumer(private val view: ImageView) : Consumer<Bitmap> {

    init {
        view.tag = this
        view.setImageDrawable(ColorDrawable(Color.TRANSPARENT))
    }

    @Throws(Exception::class)
    override fun accept(bitmap: Bitmap) {

        if (view.tag === this) {
            view.setImageDrawable(BitmapDrawable(bitmap))

            // fade in gracefully
            val animTime = view.resources.getInteger(
                    android.R.integer.config_shortAnimTime)

            view.alpha = 0.0f
            view.visibility = View.VISIBLE

            view.animate()
                    .alpha(1.0f)
                    .setDuration(animTime.toLong())
                    .setListener(null)
        }
    }
}
