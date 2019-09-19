package danb.speedrunbrowser

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.util.Log
import android.view.*
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentPagerAdapter
import androidx.viewpager.widget.ViewPager
import com.google.android.gms.security.ProviderInstaller
import com.google.firebase.crash.FirebaseCrash
import danb.speedrunbrowser.api.SpeedrunMiddlewareAPI
import danb.speedrunbrowser.api.objects.GameGroup
import danb.speedrunbrowser.stats.SiteStatisticsFragment
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

    private lateinit var mMainView: View

    // The detail container view will be present only in the
    // large-screen layouts (res/values-w900dp).
    // If this view is present, then the
    // activity should be in two-pane mode.
    private val isTwoPane: Boolean
        get() = mMainView.findViewById<View>(R.id.detail_container) != null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if(arguments?.containsKey(ARG_GAME_GROUP) == true)
            mGameGroup = arguments!!.getSerializable(ARG_GAME_GROUP) as GameGroup
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        super.onCreateView(inflater, container, savedInstanceState)

        setHasOptionsMenu(true)

        mMainView = inflater.inflate(R.layout.fragment_game_list, container, false)

        mDisposables = CompositeDisposable()

        mDB = AppDatabase.make(context!!)

        FirebaseCrash.setCrashCollectionEnabled(!BuildConfig.DEBUG)

        Util.showNewFeaturesDialog(context!!)

        // might need to update certificates/connection modes on older android versions
        // TODO: this is the synchronous call, may block user interation when installing provider. Consider using async
        try {
            ProviderInstaller.installIfNeeded(context!!.applicationContext)
        } catch (e: Exception) {
            Log.w(TAG, "Could not install latest certificates using Google Play Services")
        }

        mViewPager = mMainView.findViewById(R.id.pager)

        val pagerAdapter = PagerAdapter(childFragmentManager)

        mViewPager!!.adapter = pagerAdapter

        mTabs = mMainView.findViewById(R.id.tabsType)
        mTabs!!.setup(mViewPager!!)

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
        else super.onOptionsItemSelected(item)
    }

    private fun showGame(id: String, fragment: Fragment?, transitionOptions: ActivityOptions?) {
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

    private fun showPlayer(id: String, fragment: Fragment?, transitionOptions: ActivityOptions?) {
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

    private fun showRun(id: String, fragment: Fragment?, transitionOptions: ActivityOptions?) {
        val intent = Intent(context!!, SpeedrunBrowserActivity::class.java)
        intent.putExtra(SpeedrunBrowserActivity.EXTRA_FRAGMENT_CLASSPATH, RunDetailFragment::class.java.canonicalName)
        intent.putExtra(RunDetailFragment.ARG_RUN_ID, id)

        startActivity(intent)
    }

    override fun onItemSelected(itemType: ItemType?, itemId: String, fragment: Fragment, options: ActivityOptions?) {
        when (itemType) {
            ItemType.GAMES -> showGame(itemId, fragment, options)
            ItemType.PLAYERS -> showPlayer(itemId, fragment, options)
            ItemType.RUNS -> showRun(itemId, fragment, options)
        }
    }

    private fun viewStats() {
        val intent = Intent(context!!, SpeedrunBrowserActivity::class.java)
        intent.putExtra(SpeedrunBrowserActivity.EXTRA_FRAGMENT_CLASSPATH, SiteStatisticsFragment::class.java.canonicalName)
        startActivity(intent)
    }

    private inner class PagerAdapter(fm: FragmentManager) : FragmentPagerAdapter(fm), SimpleTabStrip.IconPagerAdapter {

        private val fragments: Array<ItemListFragment> = arrayOf(
            ItemListFragment(),
            RunItemListFragment(),
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

            if (mGameGroup == null) {
                args = Bundle()
                args.putSerializable(ItemListFragment.ARG_ITEM_TYPE, ItemType.RUNS)
                fragments[2].arguments = args

                args = Bundle()
                args.putSerializable(ItemListFragment.ARG_ITEM_TYPE, ItemType.GAMES)
                fragments[3].arguments = args

                args = Bundle()
                args.putSerializable(ItemListFragment.ARG_ITEM_TYPE, ItemType.PLAYERS)
                fragments[4].arguments = args
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
                        2 -> listName = "subscribed"
                        3 -> listName = "subscribed"
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
            return if (mGameGroup != null) 2 else fragments.size
        }

        override fun getPageTitle(position: Int): CharSequence? {
            return when (position) {
                0 -> getString(R.string.title_tab_games)
                1 -> getString(R.string.title_tab_latest_runs)
                2 -> getString(R.string.title_tab_recently_watched)
                3 -> getString(R.string.title_tab_subscribed_games)
                4 -> getString(R.string.title_tab_subscribed_players)
                else -> ""
            }
        }

        override fun getPageIcon(position: Int): Drawable? {
            return when (position) {
                0 -> resources.getDrawable(R.drawable.baseline_videogame_asset_24)
                1 -> resources.getDrawable(R.drawable.baseline_play_circle_filled_24)
                2 -> resources.getDrawable(R.drawable.baseline_list_24)
                3 -> resources.getDrawable(R.drawable.baseline_videogame_asset_24)
                4 -> resources.getDrawable(R.drawable.baseline_person_24)
                else -> null
            }
        }

        private fun initializePage(position: Int) {

            when (position) {
                0 -> fragments[0].setItemsSource(object : ItemListFragment.ItemSource {
                    override fun list(offset: Int): Observable<SpeedrunMiddlewareAPI.APIResponse<Any?>> {
                        return if (mGameGroup != null)
                            SpeedrunMiddlewareAPI.make().listGamesByGenre(mGameGroup!!.id, offset).map<SpeedrunMiddlewareAPI.APIResponse<Any?>>(ItemListFragment.GenericMapper())
                        else
                            SpeedrunMiddlewareAPI.make().listGames(offset).map<SpeedrunMiddlewareAPI.APIResponse<Any?>>(ItemListFragment.GenericMapper())
                    }
                })
                1 -> fragments[1].setItemsSource(object : ItemListFragment.ItemSource {
                    override fun list(offset: Int): Observable<SpeedrunMiddlewareAPI.APIResponse<Any?>> {

                        val shouldShowBleedingEdge = (fragments[1] as RunItemListFragment).shouldShowBleedingEdgeRun

                        return (if (mGameGroup != null)
                            SpeedrunMiddlewareAPI.make().listLatestRunsByGenre(
                                    mGameGroup!!.id,
                                    offset, shouldShowBleedingEdge)
                        else
                            SpeedrunMiddlewareAPI.make().listLatestRuns(
                                    offset, shouldShowBleedingEdge)
                        ).map<SpeedrunMiddlewareAPI.APIResponse<Any?>>(ItemListFragment.GenericMapper())
                    }
                })
                2 -> fragments[2].setItemsSource(object : ItemListFragment.ItemSource {
                    override fun list(offset: Int): Observable<SpeedrunMiddlewareAPI.APIResponse<Any?>> {
                        val entries = mDB!!.watchHistoryDao()
                                .getMany(offset)
                                .subscribeOn(Schedulers.io())

                        return entries.flatMapObservable<SpeedrunMiddlewareAPI.APIResponse<Any?>>(Function<List<AppDatabase.WatchHistoryEntry>, ObservableSource<SpeedrunMiddlewareAPI.APIResponse<Any?>>> { entries ->
                            if (entries.isEmpty())
                                return@Function Observable.just(SpeedrunMiddlewareAPI.APIResponse())

                            val builder = StringBuilder(entries.size)
                            for ((runId) in entries) {
                                if (builder.isNotEmpty())
                                    builder.append(",")
                                builder.append(runId)
                            }

                            SpeedrunMiddlewareAPI.make().listRuns(builder.toString()).map<SpeedrunMiddlewareAPI.APIResponse<Any?>>(ItemListFragment.GenericMapper())
                        })
                    }
                })
                3 -> fragments[3].setItemsSource(object : ItemListFragment.ItemSource {
                    override fun list(offset: Int): Observable<SpeedrunMiddlewareAPI.APIResponse<Any?>> {

                        val subs = mDB!!.subscriptionDao()
                                .listOfTypeGrouped("game", offset)

                        return subs.flatMapObservable<SpeedrunMiddlewareAPI.APIResponse<Any?>>(Function<List<AppDatabase.Subscription>, ObservableSource<SpeedrunMiddlewareAPI.APIResponse<Any?>>> { subscriptions ->
                            if (subscriptions.isEmpty())
                                return@Function Observable.just(SpeedrunMiddlewareAPI.APIResponse())

                            val builder = StringBuilder(subscriptions.size)
                            for (sub in subscriptions) {
                                if (builder.isNotEmpty())
                                    builder.append(",")
                                builder.append(sub.resourceId)
                            }

                            SpeedrunMiddlewareAPI.make().listGames(builder.toString()).map<SpeedrunMiddlewareAPI.APIResponse<Any?>>(ItemListFragment.GenericMapper())
                        })
                    }
                })
                4 -> fragments[4].setItemsSource(object : ItemListFragment.ItemSource {
                    override fun list(offset: Int): Observable<SpeedrunMiddlewareAPI.APIResponse<Any?>> {
                        val subs = mDB!!.subscriptionDao()
                                .listOfType("player", offset)

                        return subs.flatMapObservable<SpeedrunMiddlewareAPI.APIResponse<Any?>>(Function<List<AppDatabase.Subscription>, ObservableSource<SpeedrunMiddlewareAPI.APIResponse<Any?>>> { subscriptions ->
                            if (subscriptions.isEmpty())
                                return@Function Observable.just(SpeedrunMiddlewareAPI.APIResponse())

                            val builder = StringBuilder(subscriptions.size)
                            for ((_, resourceId) in subscriptions) {
                                if (builder.isNotEmpty())
                                    builder.append(",")
                                builder.append(resourceId)
                            }

                            SpeedrunMiddlewareAPI.make().listPlayers(builder.toString()).map<SpeedrunMiddlewareAPI.APIResponse<Any?>>(ItemListFragment.GenericMapper())
                        })
                    }
                })
            }
        }

        fun reloadSearchResults() {
            for (f in fragments) {
                f.reload()
            }
        }
    }

    companion object {
        private val TAG = GameListFragment::class.java.simpleName

        const val ARG_GAME_GROUP = "game_group"

        private const val SAVED_MAIN_PAGER = "main_pager"
    }
}
