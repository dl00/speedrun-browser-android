package danb.speedrunbrowser.views

import android.widget.HorizontalScrollView

import android.content.Context
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.View

class IgnorantHorizontalScrollView(context: Context, attrs: AttributeSet? = null) : HorizontalScrollView(context, attrs) {
    init {
        isFocusable = false
    }

    override fun executeKeyEvent(event: KeyEvent): Boolean {
        return false
    }
}