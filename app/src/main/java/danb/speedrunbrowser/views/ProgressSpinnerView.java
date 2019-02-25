package danb.speedrunbrowser.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Build;
import androidx.annotation.Nullable;
import android.util.AttributeSet;
import android.view.View;

public class ProgressSpinnerView extends View {

    /// The number of boxes which shuold be visible when boxes are stationary in one column
    private int mAnimBoxCount = 4;

    /// The number of milliseconds during which one animation cycle occurs
    private int mAnimStepLength = 600;

    /// The number of milliseconds during which the animation should occur
    private int mAnimMoveTime = 350;

    /// The number of milliseconds to delay adjacent boxes in their animatin for a "yank" effect
    private int mYankDelay = 30;

    /// The size in dp of one animation box
    private int mAnimBoxSize = 20;

    /// Rounded corners radius for the boxes
    private int mAnimBoxRoundedCornerRadius = 15;

    /// The amount of padding between each animation box
    private int mAnimBoxPadding = 10;

    private Direction mDirection = Direction.UP;

    /// The number of animation exp columns to show same animation, shrunken, off to the side
    private int mAnimExp = 0;

    /// Whether or not we shuold be animating
    private boolean mRunning = false;

    /// When mRunning is true, mStartTime represents the number of milliseconds the animation was first started.
    /// When mRunning is false, mStartTime represents the number of milliseconds the animation has been running.
    private long mStartTime;

    /// General paint used to draw everything
    private Paint mDrawPaint;


    /// The area in which all drawing should take place
    private RectF mDrawRegion = new RectF();

    /// The last measured density of the display
    private float mDensity = 1;

    public ProgressSpinnerView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);

        mStartTime = 0;

        start();

        mDrawPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mDrawPaint.setColor(Color.WHITE);
        mDrawPaint.setStyle(Paint.Style.FILL);

        recalculateDrawRect();
    }

    public void start() {
        mStartTime = System.currentTimeMillis() - mStartTime;
        mRunning = true;
        postInvalidateOnAnimation();
    }

    public void stop() {
        mStartTime = System.currentTimeMillis() - mStartTime;
        mRunning = false;
    }

    private void recalculateDrawRect() {
        float centerX = (float)getWidth() / 2;
        float centerY = (float)getHeight() / 2;

        mDensity = getResources().getDisplayMetrics().density;

        float w = mAnimBoxSize + mAnimBoxPadding * 2;
        float h = mAnimBoxSize * mAnimBoxCount + mAnimBoxPadding * (mAnimBoxCount + 1);

        w *= mDensity;
        h *= mDensity;

        mDrawRegion.left = centerX - w / 2;
        mDrawRegion.right = centerX + w / 2;
        mDrawRegion.top = centerY - h / 2;
        mDrawRegion.bottom = centerY + h / 2;
    }

    private float ease(float start, float end, float progress) {
        // clamp
        progress = Math.min(1.0f, Math.max(0.0f, progress));

        // cubic easing
        return start + (3 * (float)Math.pow(progress, 2) - 2 * (float)Math.pow(progress, 3)) * (end - start);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        recalculateDrawRect();
    }

    @Override
    protected void onDraw(Canvas c) {
        super.onDraw(c);

        if(!mRunning) {
            return;
        }

        float t = System.currentTimeMillis() - mStartTime;

        for(int i = 0;i <= mAnimBoxCount;i++) {

            // induce an artificial delay to give a "yank" effect
            t -= mYankDelay;

            float animProgress = ((t % mAnimStepLength) - (mAnimStepLength - mAnimMoveTime)) / mAnimMoveTime;

            float x0, x1, y0, y1;

            float cx = mDrawRegion.centerX();
            float cy = mDrawRegion.centerY();

            // first and last boxes may have special
            if(i == 0) {
                y0 = mDrawRegion.top + mAnimBoxPadding * mDensity;
                y1 = ease(y0 + mAnimBoxSize * mDensity, y0, animProgress);

                x0 = ease(mDrawRegion.left + mAnimBoxPadding * mDensity, cx, animProgress);
                x1 = ease(mDrawRegion.right - mAnimBoxPadding * mDensity, cx, animProgress);
            }
            else if(i == mAnimBoxCount) {
                y1 = mDrawRegion.bottom - mAnimBoxPadding * mDensity;
                y0 = ease(y1, y1 - mAnimBoxSize * mDensity, animProgress);

                x0 = ease(cx, mDrawRegion.left + mAnimBoxPadding * mDensity, animProgress);
                x1 = ease(cx, mDrawRegion.right - mAnimBoxPadding * mDensity, animProgress);
            }
            else {
                x0 = mDrawRegion.left + mAnimBoxPadding * mDensity;
                x1 = mDrawRegion.right - mAnimBoxPadding * mDensity;


                float y0_base = mDrawRegion.top + (mAnimBoxPadding * (1 + i) + mAnimBoxSize * i) * mDensity;
                y0 = ease(y0_base, y0_base - (mAnimBoxSize + mAnimBoxPadding) * mDensity, animProgress);
                y1 = y0 + mAnimBoxSize * mDensity;
            }

            // transform rotate
            if(mDirection.equals(Direction.DOWN) || mDirection.equals(Direction.LEFT)) {
                // rotate 180
                y0 = 2 * cy - y0;
                y1 = 2 * cy - y1;
            }

            if(mDirection.equals(Direction.RIGHT) || mDirection.equals(Direction.LEFT)) {
                float t0 = cx - (cy - y0);
                float t1 = cx - (cy - y1);

                y0 = cy - (cx - x0);
                y1 = cy - (cx - x1);
                x0 = t0;
                x1 = t1;
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                c.drawRoundRect(x0, y0, x1, y1, mAnimBoxRoundedCornerRadius, mAnimBoxRoundedCornerRadius, mDrawPaint);
            }
            else {
                c.drawRect(x0, y0, x1, y1, mDrawPaint);
            }
        }

        postInvalidateOnAnimation();
    }

    public enum Direction {
        UP,
        DOWN,
        LEFT,
        RIGHT
    };
}
