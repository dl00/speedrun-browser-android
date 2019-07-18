package danb.speedrunbrowser.utils

import android.os.Parcelable
import android.view.View
import android.view.ViewGroup
import android.widget.ListView
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager.widget.PagerAdapter

class ViewPagerAdapter : PagerAdapter() {

    val views = mutableListOf<Pair<String, View>>()

    override fun getCount(): Int = views.size

    override fun instantiateItem(container: ViewGroup, position: Int): Any {
        container.addView(views[position].second, 0)
        println("INSTANTIATING AN ITEM")

        return views[position].second
    }

    override fun destroyItem(container: ViewGroup, position: Int, obj: Any) {
        container.removeView(views[position].second)
    }

    override fun isViewFromObject(view: View, obj: Any): Boolean = view == obj

    override fun getPageTitle(position: Int): CharSequence? {
        return views[position].first
    }
}