package danb.speedrunbrowser.utils

import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.view.View
import android.widget.ImageView

import io.reactivex.functions.Consumer

// article: https://ruibm.com/2009/06/16/rounded-corner-bitmaps-on-android/
fun getRoundedCornerBitmap(bitmap: Bitmap, pixelsToRound: Float): Bitmap {
    val output = Bitmap.createBitmap(bitmap.width,
            bitmap.height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(output)
    val color = -0xbdbdbe
    val paint = Paint()
    val rect = Rect(0, 0, bitmap.width, bitmap.height)
    val rectF = RectF(rect)

    paint.isAntiAlias = true
    canvas.drawARGB(0, 0, 0, 0)

    paint.color = color
    canvas.drawRoundRect(rectF, pixelsToRound, pixelsToRound, paint)

    paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
    canvas.drawBitmap(bitmap, rect, rect, paint)

    return output
}

class ImageViewPlacerConsumer(private val view: ImageView) : Consumer<Bitmap> {

    var roundedCorners = 0.0f

    init {
        view.tag = this
        view.setImageDrawable(ColorDrawable(Color.TRANSPARENT))
    }

    @Throws(Exception::class)
    override fun accept(bitmap: Bitmap) {

        var finalBitmap = bitmap

        if(roundedCorners > 0)
            finalBitmap = getRoundedCornerBitmap(finalBitmap, roundedCorners)

        if (view.tag === this) {
            view.setImageDrawable(BitmapDrawable(finalBitmap))

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
