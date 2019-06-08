package danb.speedrunbrowser.utils

import android.content.Context
import android.content.res.TypedArray
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout

import danb.speedrunbrowser.R

// From: https://stackoverflow.com/questions/7058507/fixed-aspect-ratio-view
class FixedAspectRatioFrameLayout(context: Context, attrs: AttributeSet, defStyle: Int) : FrameLayout(context, attrs, defStyle) {
    private var mAspectRatioWidth: Int = 0
    private var mAspectRatioHeight: Int = 0

    init {
        val a = context.obtainStyledAttributes(attrs, R.styleable.FixedAspectRatioFrameLayout)

        mAspectRatioWidth = a.getInt(R.styleable.FixedAspectRatioFrameLayout_aspectRatioWidth, 4)
        mAspectRatioHeight = a.getInt(R.styleable.FixedAspectRatioFrameLayout_aspectRatioHeight, 3)

        a.recycle()
    }

    constructor(context: Context, attrs: AttributeSet): this(context, attrs, 0)

    // **overrides**
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val originalWidth = MeasureSpec.getSize(widthMeasureSpec)

        val originalHeight = MeasureSpec.getSize(heightMeasureSpec)

        val calculatedHeight = originalWidth * mAspectRatioHeight / mAspectRatioWidth

        val finalWidth: Int
        val finalHeight: Int

        if (calculatedHeight > originalHeight) {
            finalWidth = originalHeight * mAspectRatioWidth / mAspectRatioHeight
            finalHeight = originalHeight
        } else {
            finalWidth = originalWidth
            finalHeight = calculatedHeight
        }

        super.onMeasure(
                MeasureSpec.makeMeasureSpec(finalWidth, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(finalHeight, MeasureSpec.EXACTLY))
    }
}