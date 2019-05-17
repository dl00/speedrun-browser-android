package danb.speedrunbrowser.utils

import android.content.Context
import android.util.AttributeSet

import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

const val DEFAULT_EXTRA_LAYOUT_SPACE = 600

class PreCachingGridLayoutManager(context: Context, attrs: AttributeSet, spanCount: Int, t2: Int) : GridLayoutManager(context, attrs, spanCount, t2) {
    var extraLayoutSpace = DEFAULT_EXTRA_LAYOUT_SPACE

    override fun getExtraLayoutSpace(state: RecyclerView.State): Int {
        return if (extraLayoutSpace > 0) {
            extraLayoutSpace
        } else DEFAULT_EXTRA_LAYOUT_SPACE
    }
}