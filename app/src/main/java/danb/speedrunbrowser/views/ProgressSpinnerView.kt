package danb.speedrunbrowser.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Build
import danb.speedrunbrowser.R

import android.util.AttributeSet
import android.view.View
import java.lang.Math.abs
import java.lang.RuntimeException

class ProgressSpinnerView(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    /// The number of boxes which should be visible when boxes are stationary in one column
    private val mAnimBoxCount = 4

    /// The number of milliseconds during which one animation cycle occurs
    private val mAnimStepLength = 600

    /// The number of milliseconds during which the animation should occur
    private val mAnimMoveTime = 350

    /// The number of milliseconds to delay adjacent boxes in their animatin for a "yank" effect
    private val mYankDelay = 30

    /// The size in dp of one animation box
    private val mAnimBoxSize = 20

    /// Rounded corners radius for the boxes
    private val mAnimBoxRoundedCornerRadius = 5

    /// The amount of padding between each animation box
    private val mAnimBoxPadding = 10

    private var mDirection = Direction.UP

    /// Whether or not we should be animating
    private var mRunning = false

    /// When mRunning is true, mStartTime represents the number of milliseconds the animation was first started.
    /// When mRunning is false, mStartTime represents the number of milliseconds the animation has been running.
    private var mStartTime: Long = 0

    /// General paint used to draw everything
    private val mDrawPaint = Paint(Paint.ANTI_ALIAS_FLAG)


    /// The area in which all drawing should take place
    private val mDrawRegion = RectF()

    /// The last measured density of the display
    private var mDensity = 1f

    // configurable size
    private var mScale = 1f

    init {

        val a = context.theme.obtainStyledAttributes(
                attrs,
                R.styleable.ProgressSpinnerView,
                0, 0)

        try {
            mDirection = Direction.fromValue(a.getInteger(R.styleable.ProgressSpinnerView_direction, 0))
        } finally {
            a.recycle()
        }

        mStartTime = 0

        start()

        mDrawPaint.color = Color.WHITE
        mDrawPaint.style = Paint.Style.FILL

        recalculateDrawRect()
    }

    fun start() {
        if (!mRunning) {
            mStartTime = System.currentTimeMillis() - mStartTime
            mRunning = true
            postInvalidateOnAnimation()
        }
    }

    fun stop() {
        if (mRunning) {
            mStartTime = System.currentTimeMillis() - mStartTime
            mRunning = false
        }
    }

    private fun recalculateDrawRect() {

        mDensity = resources.displayMetrics.density

        var w = (mAnimBoxSize + mAnimBoxPadding * 2).toFloat()
        var h = (mAnimBoxSize * mAnimBoxCount + mAnimBoxPadding * (mAnimBoxCount + 1)).toFloat()

        minimumWidth = (w * mDensity).toInt()
        minimumHeight = (h * mDensity).toInt()

        val centerX = width.toFloat() / 2
        val centerY = height.toFloat() / 2

        w *= mDensity * mScale
        h *= mDensity * mScale

        mDrawRegion.left = centerX - w / 2
        mDrawRegion.right = centerX + w / 2
        mDrawRegion.top = centerY - h / 2
        mDrawRegion.bottom = centerY + h / 2
    }

    private fun ease(start: Float, end: Float, progress: Float): Float {
        // clamp
        val clampedProgress: Double = Math.min(1.0, Math.max(0.0, progress.toDouble()))

        // cubic easing
        return start + (3 * Math.pow(clampedProgress, 2.0).toFloat() - 2 * Math.pow(clampedProgress, 3.0).toFloat()) * (end - start)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        recalculateDrawRect()
    }

    override fun setVisibility(visibility: Int) {
        super.setVisibility(visibility)

        if (visibility == VISIBLE) start() else stop()
    }

    override fun onDraw(c: Canvas) {
        super.onDraw(c)

        if (!mRunning) {
            return
        }

        var t = (System.currentTimeMillis() - mStartTime).toFloat()

        for (i in 0..mAnimBoxCount) {

            // induce an artificial delay to give a "yank" effect
            t -= mYankDelay.toFloat()

            val animProgress = (t % mAnimStepLength - (mAnimStepLength - mAnimMoveTime)) / mAnimMoveTime

            var x0: Float
            var x1: Float
            var y0: Float
            var y1: Float

            val cx = mDrawRegion.centerX()
            val cy = mDrawRegion.centerY()

            val scale = mScale * mDensity

            // first and last boxes may have special
            if (i == 0) {
                y0 = mDrawRegion.top + mAnimBoxPadding * scale
                y1 = ease(y0 + mAnimBoxSize * scale, y0, animProgress)

                x0 = ease(mDrawRegion.left + mAnimBoxPadding * scale, cx, animProgress)
                x1 = ease(mDrawRegion.right - mAnimBoxPadding * scale, cx, animProgress)
            } else if (i == mAnimBoxCount) {
                y1 = mDrawRegion.bottom - mAnimBoxPadding * scale
                y0 = ease(y1, y1 - mAnimBoxSize * scale, animProgress)

                x0 = ease(cx, mDrawRegion.left + mAnimBoxPadding * scale, animProgress)
                x1 = ease(cx, mDrawRegion.right - mAnimBoxPadding * scale, animProgress)
            } else {
                x0 = mDrawRegion.left + mAnimBoxPadding * scale
                x1 = mDrawRegion.right - mAnimBoxPadding * scale


                val y0Base = mDrawRegion.top + (mAnimBoxPadding * (1 + i) + mAnimBoxSize * i) * scale
                y0 = ease(y0Base, y0Base - (mAnimBoxSize + mAnimBoxPadding) * scale, animProgress)
                y1 = y0 + mAnimBoxSize * scale
            }

            // transform rotate
            if (mDirection == Direction.DOWN || mDirection == Direction.RIGHT) {
                // rotate 180
                y0 = 2 * cy - y0
                y1 = 2 * cy - y1
            }

            if (mDirection == Direction.RIGHT || mDirection == Direction.LEFT) {
                val t0 = cx - (cy - y0)
                val t1 = cx - (cy - y1)

                y0 = cy - (cx - x0)
                y1 = cy - (cx - x1)
                x0 = t0
                x1 = t1
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                c.drawRoundRect(x0, y0, x1, y1, mAnimBoxRoundedCornerRadius * scale, mAnimBoxRoundedCornerRadius * scale, mDrawPaint)
            } else {
                c.drawRect(x0, y0, x1, y1, mDrawPaint)
            }
        }

        postInvalidateOnAnimation()
    }

    fun setDirection(direction: Direction) {
        mDirection = direction
    }

    fun setScale(scale: Float) {
        mScale = scale
    }

    enum class Direction {
        UP,
        DOWN,
        LEFT,
        RIGHT;


        companion object {

            fun fromString(dir: String): Direction? {
                return when (dir) {
                    "up" -> UP
                    "down" -> DOWN
                    "left" -> LEFT
                    "right" -> RIGHT
                    else -> null
                }
            }

            fun fromValue(dir: Int): Direction {
                return when (abs(dir) % 4) {
                    0 -> UP
                    1 -> RIGHT
                    2 -> DOWN
                    3 -> LEFT
                    else -> throw RuntimeException("Invalid direction after modulo")
                }
            }
        }
    }
}
