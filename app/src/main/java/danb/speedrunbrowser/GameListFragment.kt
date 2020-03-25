@file:Suppress("DEPRECATION")

package danb.speedrunbrowser

import android.app.ActivityOptions
import android.content.Intent
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.*
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentPagerAdapter
import androidx.viewpager.widget.ViewPager
import com.google.android.gms.security.ProviderInstaller
import com.google.firebase.crash.FirebaseCrash
import danb.speedrunbrowser.api.SpeedrunMiddlewareAPI
import danb.speedrunbrowser.api.objects.GameGroup
import danb.speedrunbrowser.stats.GameGroupStatisticsFragment
import danb.speedrunbrowser.utils.Analytics
import danb.speedrunbrowser.utils.AppDatabase
import danb.speedrunbrowser.utils.ItemType
import danb.speedrunbrowser.utils.Util
import danb.speedrunbrowser.views.SimpleTabStrip
import io.reactivex.Observable
import io.reactivex.ObservableSource
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.functions.Function
import io.reactivex.schedulers.Schedulers
import java.net.URL
import java.util.*

/**
 * An activity representing a list of Games. This activity
 * has different presentations for handset and tablet-size devices. On
 * handsets, the activity presents a list of items, which when touched,
 * lead to a [SpeedrunBrowserActivity] representing
 * item details. On tablets, the activity presents the list of items and
 * item details side-by-side using two vertical panes.
 */
class GameListFragment : Fragment(), ItemListFragment.OnFragmentInteractionListener {

    private var mDB: AppDatabase? = null

    private var mGameGroup: GameGroup? = null

    private var mTabs: SimpleTabStrip? = null
    private var mViewPager: ViewPager? = null

    private var mDisposables: CompositeDisposable? = null

    private var mMainView: View? = null

    // The detail container view will be present only in the
    // large-screen layouts (res/values-w900dp).
    // If this view is present, then the
    // activity should be in two-pane mode.
    private val isTwoPane: Boolean
        get() = mMainView?.findViewById<View>(R.id.detail_container)?.visibility == View.VISIBLE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if(arguments?.containsKey(ARG_GAME_GROUP) == true)
            mGameGroup = arguments!!.getSerializable(ARG_GAME_GROUP) as GameGroup

        mDisposables = CompositeDisposable()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        super.onCreateView(inflater, container, savedInstanceState)

        setHasOptionsMenu(true)

        if(mMainView != null)
            return mMainView

        mMainView = inflater.inflate(R.layout.fragment_game_list, container, false)

        mDB = AppDatabase.make(context!!)

        @Suppress("DEPRECATION")
        FirebaseCrash.setCrashCollectionEnabled(!BuildConfig.DEBUG)

        Util.showNewFeaturesDialog(context!!)

        // might need to update certificates/connection modes on older android versions
        // TODO: this is the synchronous call, may block user interation when installing provider. Consider using async
        try {
            ProviderInstaller.installIfNeeded(context!!.applicationContext)
        } catch (e: Exception) {
            Log.w(TAG, "Could not install latest certificates using Google Play Services")
        }

        mViewPager = mMainView!!.findViewById(R.id.pager)

        val pagerAdapter = PagerAdapter(childFragmentManager)

        mViewPager!!.adapter = pagerAdapter

        mTabs = mMainView!!.findViewById(R.id.tabsType)
        mTabs!!.setup(mViewPager!!)

        if(savedInstanceState != null)
            mViewPager!!.onRestoreInstanceState(savedInstanceState.getBundle(SAVED_MAIN_PAGER))

        return mMainView
    }

    override fun onResume() {
        super.onResume()

        activity!!.title = if (mGameGroup != null)
            "${mGameGroup!!.type.capitalize()}: ${mGameGroup!!.name}"
        else
            getString(R.string.app_name)
    }

    override fun onDestroy() {
        super.onDestroy()
        mDisposables!!.dispose()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        if(mViewPager != null)
            outState.putParcelable(SAVED_MAIN_PAGER, mViewPager!!.onSaveInstanceState())
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.game_group, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return if(item.itemId == R.id.menu_site_stats) {
            viewStats()
            true
        }
        else if(item.itemId == R.id.menu_about) {
            showAbout()
            true
        }
        else super.onOptionsItemSelected(item)
    }

    private fun showGame(id: String) {
        if (isTwoPane) {
            val arguments = Bundle()
            arguments.putString(GameDetailFragment.ARG_GAME_ID, id)

            val newFrag = GameDetailFragment()
            newFrag.arguments = arguments

            childFragmentManager.beginTransaction()
                    .replace(R.id.detail_container, newFrag)
                    .commit()
        } else {
            val intent = Intent(context!!, SpeedrunBrowserActivity::class.java)
            intent.putExtra(SpeedrunBrowserActivity.EXTRA_FRAGMENT_CLASSPATH, GameDetailFragment::class.java.canonicalName)
            intent.putExtra(GameDetailFragment.ARG_GAME_ID, id)

            startActivity(intent)
        }
    }

    private fun showPlayer(id: String) {
        if (isTwoPane) {
            val arguments = Bundle()
            arguments.putString(PlayerDetailFragment.ARG_PLAYER_ID, id)

            val newFrag = PlayerDetailFragment()
            newFrag.arguments = arguments

            childFragmentManager.beginTransaction()
                    .replace(R.id.detail_container, newFrag)
                    .commit()
        } else {
            val intent = Intent(context!!, SpeedrunBrowserActivity::class.java)
            intent.putExtra(SpeedrunBrowserActivity.EXTRA_FRAGMENT_CLASSPATH, PlayerDetailFragment::class.java.canonicalName)
            intent.putExtra(PlayerDetailFragment.ARG_PLAYER_ID, id)

            startActivity(intent)
        }
    }

    private fun showRun(id: String) {
        val intent = Intent(context!!, SpeedrunBrowserActivity::class.java)
        intent.putExtra(SpeedrunBrowserActivity.EXTRA_FRAGMENT_CLASSPATH, RunDetailFragment::class.java.canonicalName)
        intent.putExtra(RunDetailFragment.ARG_RUN_ID, id)

        startActivity(intent)
    }

    private fun showStream(twitchUrl: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(twitchUrl))

        startActivity(intent)
    }

    private fun showAbout() {
        val intent = Intent(context, AboutActivity::class.java)
        startActivity(intent)
    }

    override fun onItemSelected(itemType: ItemType?, itemId: String, fragment: Fragment, options: ActivityOptions?) {
        when (itemType) {
            ItemType.GAMES -> showGame(itemId)
            ItemType.GAME_GROUPS -> {}
            ItemType.PLAYERS -> showPlayer(itemId)
            ItemType.RUNS -> showRun(itemId)
            ItemType.STREAMS -> showStream(itemId)
        }
    }

    private fun viewStats() {
        val intent = Intent(context!!, SpeedrunBrowserActivity::class.java)
        intent.putExtra(SpeedrunBrowserActivity.EXTRA_FRAGMENT_CLASSPATH, GameGroupStatisticsFragment::class.java.canonicalName)

        if (mGameGroup != null) {
            intent.putExtra(GameGroupStatisticsFragment.EXTRA_GAME_GROUP_ID, mGameGroup!!.id)
        }

        startActivity(intent)
    }

    private inner class PagerAdapter(fm: FragmentManager) : FragmentPagerAdapter(fm, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT), SimpleTabStrip.IconPagerAdapter {

        private val fragments: Array<ItemListFragment> = arrayOf(
            ItemListFragment(),
            ItemListFragment(),
            ItemListFragment(),
            ItemListFragment(),
            ItemListFragment(),
            ItemListFragment()
        )

        init {

            var args = Bundle()
            args.putSerializable(ItemListFragment.ARG_ITEM_TYPE, ItemType.GAMES)
            fragments[0].arguments = args

            args = Bundle()
            args.putSerializable(ItemListFragment.ARG_ITEM_TYPE, ItemType.RUNS)
            fragments[1].arguments = args

            args = Bundle()
            args.putSerializable(ItemListFragment.ARG_ITEM_TYPE, ItemType.STREAMS)
            fragments[2].arguments = args

            if (mGameGroup == null) {
                args = Bundle()
                args.putSerializable(ItemListFragment.ARG_ITEM_TYPE, ItemType.RUNS)
                fragments[3].arguments = args

                args = Bundle()
                args.putSerializable(ItemListFragment.ARG_ITEM_TYPE, ItemType.GAMES)
                fragments[4].arguments = args

                args = Bundle()
                args.putSerializable(ItemListFragment.ARG_ITEM_TYPE, ItemType.PLAYERS)
                fragments[5].arguments = args
            }

            for (i in 0 until count)
                initializePage(i)

            mViewPager!!.addOnPageChangeListener(object : ViewPager.OnPageChangeListener {
                override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {}

                override fun onPageSelected(position: Int) {

                    if (fragments[position].itemType == null)
                        return

                    val type = fragments[position].itemType!!.name

                    var listName = ""
                    when (position) {
                        0 -> listName = "popular"
                        1 -> listName = "latest"
                        2 -> listName = "streams"
                        3 -> listName = "recent"
                        4 -> listName = "subscribed"
                        5 -> listName = "subscribed"
                    }

                    Analytics.logItemView(context!!, type, listName)
                }

                override fun onPageScrollStateChanged(state: Int) {}
            })
        }

        override fun getItem(position: Int): Fragment {
            return fragments[position]
        }

        override fun setPrimaryItem(container: ViewGroup, position: Int, `object`: Any) {
            if (fragments[position] !== `object`) {
                fragments[position] = `object` as ItemListFragment
                initializePage(position)
            }

            super.setPrimaryItem(container, position, `object`)
        }

        override fun getCount(): Int {
            return if (mGameGroup != null) 3 else fragments.size
        }

        override fun getPageTitle(position: Int): CharSequence? {
            return when (position) {
                0 -> getString(R.string.title_tab_games)
                1 -> getString(R.string.title_tab_latest_runs)
                2 -> getString(R.string.title_tab_streams)
                3 -> getString(R.string.title_tab_recently_watched)
                4 -> getString(R.string.title_tab_subscribed_games)
                5 -> getString(R.string.title_tab_subscribed_players)
                else -> ""
            }
        }

        override fun getPageIcon(position: Int): Drawable? {
            return when (position) {
                0 -> ContextCompat.getDrawable(context!!, R.drawable.baseline_videogame_asset_24)
                1 -> ContextCompat.getDrawable(context!!, R.drawable.baseline_play_circle_filled_24)
                2 -> ContextCompat.getDrawable(context!!, R.drawable.baseline_live_tv_24)
                3 -> ContextCompat.getDrawable(context!!, R.drawable.baseline_list_24)
                4 -> ContextCompat.getDrawable(context!!, R.drawable.baseline_videogame_asset_24)
                5 -> ContextCompat.getDrawable(context!!, R.drawable.baseline_person_24)
                else -> null
            }
        }

        private fun initializePage(position: Int) {
            when (position) {
                0 -> {
                    fragments[0].addListMode(ItemListFragment.Companion.ItemListMode(object : ItemListFragment.ItemSource {
                        override fun list(offset: Int): Observable<SpeedrunMiddlewareAPI.APIResponse<Any?>> {
                            return if (mGameGroup != null)
                                SpeedrunMiddlewareAPI.make(context!!).listGamesByGenre(mGameGroup!!.id, "popular", offset).map(ItemListFragment.GenericMapper())
                            else
                                SpeedrunMiddlewareAPI.make(context!!).listGames("popular", offset).map(ItemListFragment.GenericMapper())
                        }
                    }, "popular", getString(R.string.label_list_mode_popular)))

                    fragments[0].addListMode(ItemListFragment.Companion.ItemListMode(object : ItemListFragment.ItemSource {
                        override fun list(offset: Int): Observable<SpeedrunMiddlewareAPI.APIResponse<Any?>> {
                            return if (mGameGroup != null)
                                SpeedrunMiddlewareAPI.make(context!!).listGamesByGenre(mGameGroup!!.id, "trending", offset).map(ItemListFragment.GenericMapper())
                            else
                                SpeedrunMiddlewareAPI.make(context!!).listGames("trending", offset).map(ItemListFragment.GenericMapper())
                        }
                    }, "trending", getString(R.string.label_list_mode_trending)))
                }
                1 -> {
                    fragments[1].addListMode(ItemListFragment.Companion.ItemListMode(object : ItemListFragment.ItemSource {
                        override fun list(offset: Int): Observable<SpeedrunMiddlewareAPI.APIResponse<Any?>> {
                            return (if (mGameGroup != null)
                                SpeedrunMiddlewareAPI.make(context!!).listLatestRunsByGenre(mGameGroup!!.id, offset, true)
                            else
                                SpeedrunMiddlewareAPI.make(context!!).listLatestRuns(offset, true)
                                    ).map(ItemListFragment.GenericMapper())
                        }
                    }, "verified", getString(R.string.label_list_mode_verified)))

                    fragments[1].addListMode(ItemListFragment.Companion.ItemListMode(object : ItemListFragment.ItemSource {
                        override fun list(offset: Int): Observable<SpeedrunMiddlewareAPI.APIResponse<Any?>> {
                            return (if (mGameGroup != null)
                                SpeedrunMiddlewareAPI.make(context!!).listLatestRunsByGenre(
                                        mGameGroup!!.id, offset, false)
                            else
                                SpeedrunMiddlewareAPI.make(context!!).listLatestRuns(offset, false)
                                    ).map(ItemListFragment.GenericMapper())
                        }
                    }, "unverified", getString(R.string.label_list_mode_unverified)))
                }
                2 -> {
                    fragments[2].addListMode(ItemListFragment.Companion.ItemListMode(object : ItemListFragment.ItemSource {
                        override fun list(offset: Int): Observable<SpeedrunMiddlewareAPI.APIResponse<Any?>> {
                            return SpeedrunMiddlewareAPI.make(context!!).listStreamsByGameGroup(if (mGameGroup != null) mGameGroup!!.id else "site", offset).map(ItemListFragment.GenericMapper())
                        }
                    }, "all", getString(R.string.label_list_mode_all)))

                    if (Locale.getDefault().language != Locale.ENGLISH.language) {
                        fragments[2].addListMode(ItemListFragment.Companion.ItemListMode(object : ItemListFragment.ItemSource {
                            override fun list(offset: Int): Observable<SpeedrunMiddlewareAPI.APIResponse<Any?>> {
                                return SpeedrunMiddlewareAPI.make(context!!).listStreamsByGameGroup(if (mGameGroup != null) mGameGroup!!.id else "site", Locale.getDefault().language, offset).map(ItemListFragment.GenericMapper())
                            }
                        }, "locale", Locale.getDefault().displayLanguage))
                    }

                    fragments[2].addListMode(ItemListFragment.Companion.ItemListMode(object : ItemListFragment.ItemSource {
                        override fun list(offset: Int): Observable<SpeedrunMiddlewareAPI.APIResponse<Any?>> {
                            return SpeedrunMiddlewareAPI.make(context!!).listStreamsByGameGroup(if (mGameGroup != null) mGameGroup!!.id else "site", Locale.ENGLISH.language, offset).map(ItemListFragment.GenericMapper())
                        }
                    }, "english", "English"))
                }
                3 -> fragments[3].addListMode(ItemListFragment.Companion.ItemListMode(object : ItemListFragment.ItemSource {
                    override fun list(offset: Int): Observable<SpeedrunMiddlewareAPI.APIResponse<Any?>> {
                        val entries = mDB!!.watchHistoryDao()
                                .getMany(offset)
                                .subscribeOn(Schedulers.io())

                        return entries.flatMapObservable(Function<List<AppDatabase.WatchHistoryEntry>, ObservableSource<SpeedrunMiddlewareAPI.APIResponse<Any?>>> { ents ->
                            if (ents.isEmpty())
                                return@Function Observable.just(SpeedrunMiddlewareAPI.APIResponse())

                            val builder = StringBuilder(ents.size)
                            for ((runId) in ents) {
                                if (builder.isNotEmpty())
                                    builder.append(",")
                                builder.append(runId)
                            }

                            SpeedrunMiddlewareAPI.make(context!!).listRuns(builder.toString()).map(ItemListFragment.GenericMapper())
                        })
                    }
                }))
                4 -> fragments[4].addListMode(ItemListFragment.Companion.ItemListMode(object : ItemListFragment.ItemSource {
                    override fun list(offset: Int): Observable<SpeedrunMiddlewareAPI.APIResponse<Any?>> {

                        val subs = mDB!!.subscriptionDao()
                                .listOfTypeGrouped("game", offset)

                        return subs.flatMapObservable(Function<List<AppDatabase.Subscription>, ObservableSource<SpeedrunMiddlewareAPI.APIResponse<Any?>>> { subscriptions ->
                            if (subscriptions.isEmpty())
                                return@Function Observable.just(SpeedrunMiddlewareAPI.APIResponse())

                            val builder = StringBuilder(subscriptions.size)
                            for (sub in subscriptions) {
                                if (builder.isNotEmpty())
                                    builder.append(",")
                                builder.append(sub.resourceId)
                            }

                            SpeedrunMiddlewareAPI.make(context!!).listGames(builder.toString()).map(ItemListFragment.GenericMapper())
                        })
                    }
                }))
                5 -> fragments[5].addListMode(ItemListFragment.Companion.ItemListMode(object : ItemListFragment.ItemSource {
                    override fun list(offset: Int): Observable<SpeedrunMiddlewareAPI.APIResponse<Any?>> {
                        val subs = mDB!!.subscriptionDao()
                                .listOfType("player", offset)

                        return subs.flatMapObservable(Function<List<AppDatabase.Subscription>, ObservableSource<SpeedrunMiddlewareAPI.APIResponse<Any?>>> { subscriptions ->
                            if (subscriptions.isEmpty())
                                return@Function Observable.just(SpeedrunMiddlewareAPI.APIResponse())

                            val builder = StringBuilder(subscriptions.size)
                            for ((_, resourceId) in subscriptions) {
                                if (builder.isNotEmpty())
                                    builder.append(",")
                                builder.append(resourceId)
                            }

                            SpeedrunMiddlewareAPI.make(context!!).listPlayers(builder.toString()).map(ItemListFragment.GenericMapper())
                        })
                    }
                }))
            }
        }
    }

    companion object {
        private val TAG = GameListFragment::class.java.simpleName

        const val ARG_GAME_GROUP = "game_group"

        private const val SAVED_MAIN_PAGER = "main_pager"
    }
}
