package danb.speedrunbrowser.utils

import android.os.Parcelable
import android.view.View
import android.view.ViewGroup
import android.widget.ListView
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager.widget.PagerAdapter

class ViewPagerAdapter : PagerAdapter() {

    val views = mutableListOf<View>()

    override fun getCount(): Int = views.size

    override fun instantiateItem(container: ViewGroup, position: Int): Any {
        container.addView(views[position], 0)
        println("INSTANTIATING AN ITEM")

        return views[position]
    }

    override fun destroyItem(container: ViewGroup, position: Int, obj: Any) {
        container.removeView(views[position])
    }

    override fun isViewFromObject(view: View, obj: Any): Boolean = view == obj

    override fun saveState(): Parcelable? {
        return null
    }

    override fun restoreState(state: Parcelable?, loader: ClassLoader?) {

    }
}