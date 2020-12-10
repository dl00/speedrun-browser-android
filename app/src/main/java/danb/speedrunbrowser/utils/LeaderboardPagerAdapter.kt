package danb.speedrunbrowser.utils

import android.os.Bundle
import android.view.ViewGroup

import java.util.ArrayList
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentPagerAdapter
import androidx.viewpager.widget.ViewPager
import danb.speedrunbrowser.BuildConfig

import danb.speedrunbrowser.LeaderboardFragment
import danb.speedrunbrowser.api.objects.Category
import danb.speedrunbrowser.api.objects.Game
import danb.speedrunbrowser.api.objects.Level
import danb.speedrunbrowser.api.objects.Variable

class LeaderboardPagerAdapter(fm: FragmentManager, private val game: Game, private val filterSelections: Variable.VariableSelections?, vp: ViewPager) : FragmentPagerAdapter(fm, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT), ViewPager.OnPageChangeListener {

    private val perGameCategories: MutableList<Category>
    private val perLevelCategories: MutableList<Category>
    private val levels: List<Level>?

    private val existingFragments: Array<LeaderboardFragment?>

    private var selectedFragment = 0

    // used for detecting when the page has actually changed (not for what the target fragment is)
    private var curItem = -1

    val perGameCategorySize: Int
        get() = perGameCategories.size

    val sortedCategories: List<Category>
        get() {
            val sortedCategories = ArrayList(perGameCategories)
            sortedCategories.addAll(perLevelCategories)

            return sortedCategories
        }

    init {

        perGameCategories = ArrayList()
        perLevelCategories = ArrayList()

        if (game.categories != null) {
            for (c in game.categories) {
                if (c.type == "per-game")
                    perGameCategories.add(c)
                else if (c.type == "per-level")
                    perLevelCategories.add(c)

                // TODO: Log or something if per-game or per-level are not the str
            }
        }

        if (perLevelCategories.isNotEmpty()) {
            this.levels = game.levels
        } else {
            this.levels = ArrayList(0)
        }

        existingFragments = arrayOfNulls(count)

        vp.addOnPageChangeListener(this)
    }

    fun getCategoryOfIndex(position: Int): Category {
        if (position < perGameCategories.size) {
            return perGameCategories[position]
        }

        val mpos = position - perGameCategories.size

        return perLevelCategories[mpos / levels!!.size]
    }

    fun getLevelOfIndex(position: Int): Level? {
        if (position >= perGameCategories.size) {
            val mpos = position - perGameCategories.size
            return levels!![mpos % levels.size]
        }

        return null
    }

    fun indexOf(category: Category, level: Level?): Int {
        var index: Int = perGameCategories.indexOf(category)
        if (index!= -1)
            return index

        index = levels!!.indexOf(level)
        if (index < 0)
            return -1

        index += levels.size * perLevelCategories.indexOf(category)
        return if (index < 0) -1 else perGameCategorySize + index

    }

    override fun getItem(position: Int): Fragment {
        if (existingFragments[position] != null)
            return existingFragments[position]!!

        val frag = LeaderboardFragment()
        val args = Bundle()

        args.putSerializable(LeaderboardFragment.ARG_GAME, game)

        args.putSerializable(LeaderboardFragment.ARG_CATEGORY, getCategoryOfIndex(position))
        args.putSerializable(LeaderboardFragment.ARG_LEVEL, getLevelOfIndex(position))

        frag.arguments = args

        if (filterSelections != null)
            frag.filter = filterSelections

        existingFragments[position] = frag

        return frag
    }

    override fun setPrimaryItem(container: ViewGroup, position: Int, `object`: Any) {
        if (`object` is LeaderboardFragment) {

            if (curItem != position) {
                `object`.doFocus = true
            }

            curItem = position

            if (`object`.filter == null) {
                `object`.filter = filterSelections
                super.setPrimaryItem(container, position, `object`)
            }
        }
    }

    override fun getPageTitle(position: Int): CharSequence? {
        if (position < perGameCategories.size)
            return perGameCategories[position].name
        else {
            val mPos = position - perGameCategories.size

            return perLevelCategories[mPos / levels!!.size].name + '('.toString() + levels[mPos % levels.size].name + ')'.toString()
        }
    }

    override fun getCount(): Int {
        return perGameCategories.size + perLevelCategories.size * levels!!.size
    }

    fun notifyFilterChanged() {
        existingFragments[selectedFragment]?.notifyFilterChanged()
    }

    override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {}

    override fun onPageSelected(position: Int) {
        selectedFragment = position
        notifyFilterChanged()
    }

    override fun onPageScrollStateChanged(state: Int) {}
}
