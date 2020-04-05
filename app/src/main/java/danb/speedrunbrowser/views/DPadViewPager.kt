package danb.speedrunbrowser.views

import android.content.Context
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.View
import androidx.viewpager.widget.ViewPager

class DPadViewPager(context: Context, attrs: AttributeSet? = null) : ViewPager(context, attrs) {

    init {
        focusable = View.FOCUSABLE
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        println("DPAD VIEW PAGER EVENT " + keyCode)
        var ret = false
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> this.currentItem = this.currentItem - 1
            KeyEvent.KEYCODE_DPAD_RIGHT -> this.currentItem = this.currentItem + 1
            else -> ret = super.onKeyDown(keyCode, event)
        }

        return ret
    }
}