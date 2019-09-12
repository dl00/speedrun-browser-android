package danb.speedrunbrowser

import android.app.ActivityOptions
import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Parcelable
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.EditText
import android.widget.ListView
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentPagerAdapter
import androidx.viewpager.widget.ViewPager
import com.google.android.gms.security.ProviderInstaller
import com.google.firebase.crash.FirebaseCrash
import danb.speedrunbrowser.api.SpeedrunMiddlewareAPI
import danb.speedrunbrowser.api.objects.Game
import danb.speedrunbrowser.api.objects.Genre
import danb.speedrunbrowser.api.objects.User
import danb.speedrunbrowser.stats.SiteStatisticsActivity
import danb.speedrunbrowser.utils.*
import danb.speedrunbrowser.views.SimpleTabStrip
import io.reactivex.Observable
import io.reactivex.ObservableSource
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.functions.Function
import io.reactivex.schedulers.Schedulers
import io.reactivex.subjects.PublishSubject
import java.util.*

/**
 * An activity representing a list of Games. This activity
 * has different presentations for handset and tablet-size devices. On
 * handsets, the activity presents a list of items, which when touched,
 * lead to a [ItemDetailActivity] representing
 * item details. On tablets, the activity presents the list of items and
 * item details side-by-side using two vertical panes.
 */
class GameListActivity : AppCompatActivity(), TextWatcher, ItemListFragment.OnFragmentInteractionListener, AdapterView.OnItemClickListener {

    private var mDB: AppDatabase? = null

    private var mGameFilterSearchSubject: PublishSubject<String>? = null

    private var mGameFilter: EditText? = null
    private var mAutoCompleteResults: ListView? = null

    private var mSelectedGenre: Genre? = null

    private var mTabs: SimpleTabStrip? = null
    private var mViewPager: ViewPager? = null

    private var mDisposables: CompositeDisposable? = null

    // The detail container view will be present only in the
    // large-screen layouts (res/values-w900dp).
    // If this view is present, then the
    // activity should be in two-pane mode.
    private val isTwoPane: Boolean
        get() = findViewById<View>(R.id.detail_container) != null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_game_list)

        mDisposables = CompositeDisposable()

        mDB = AppDatabase.make(this)

        FirebaseCrash.setCrashCollectionEnabled(!BuildConfig.DEBUG)

        Util.showNewFeaturesDialog(this)

        // might need to update certificates/connection modes on older android versions
        // TODO: this is the synchronous call, may block user interation when installing provider. Consider using async
        try {
            ProviderInstaller.installIfNeeded(applicationContext)
        } catch (e: Exception) {
            Log.w(TAG, "Could not install latest certificates using Google Play Services")
        }

        mGameFilter = findViewById(R.id.editGameFilter)
        mViewPager = findViewById(R.id.pager)
        mAutoCompleteResults = findViewById(R.id.listAutoCompleteResults)

        val pagerAdapter = PagerAdapter(supportFragmentManager)

        mViewPager!!.adapter = pagerAdapter

        mTabs = findViewById(R.id.tabsType)
        mTabs!!.setup(mViewPager!!)

        mGameFilter!!.addTextChangedListener(this)

        mGameFilterSearchSubject = PublishSubject.create()

        val autoCompleteAdapter = AutoCompleteAdapter(this, mDisposables!!)
        autoCompleteAdapter.setPublishSubject(mGameFilterSearchSubject!!)
        mAutoCompleteResults!!.adapter = autoCompleteAdapter
        mAutoCompleteResults!!.onItemClickListener = this
    }

    override fun onDestroy() {
        super.onDestroy()
        mDisposables!!.dispose()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val inflater = menuInflater
        inflater.inflate(R.menu.game_list, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.menu_about) {
            showAbout()
            return true
        } else if (item.itemId == R.id.menu_genres) {
            showGenreFilterDialog()
            return true
        } else if (item.itemId == R.id.menu_site_stats) {
            viewStats()
        }

        return false
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        outState.putParcelable(SAVED_MAIN_PAGER, mViewPager!!.onSaveInstanceState())
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle?) {
        super.onRestoreInstanceState(savedInstanceState)

        if (savedInstanceState != null) {
            mViewPager!!.onRestoreInstanceState(savedInstanceState.getParcelable<Parcelable>(SAVED_MAIN_PAGER))
        }
    }

    override fun onBackPressed() {
        if (mGameFilter!!.text.isEmpty())
            finish()
        else
            mGameFilter!!.setText("")
    }

    private fun showAbout() {
        val intent = Intent(this, AboutActivity::class.java)
        startActivity(intent)
    }

    override fun beforeTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {

    }

    override fun onTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {}

    override fun afterTextChanged(editable: Editable) {
        val q = mGameFilter!!.text.toString().trim { it <= ' ' }
        mGameFilterSearchSubject!!.onNext(q)
        mAutoCompleteResults!!.visibility = if (q.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun showGame(id: String, fragment: Fragment?, transitionOptions: ActivityOptions?) {
        if (isTwoPane) {
            val arguments = Bundle()
            arguments.putString(GameDetailFragment.ARG_GAME_ID, id)

            val newFrag = GameDetailFragment()
            newFrag.arguments = arguments

            supportFragmentManager.beginTransaction()
                    .replace(R.id.detail_container, newFrag)
                    .commit()
        } else {
            val intent = Intent(this, ItemDetailActivity::class.java)
            intent.putExtra(ItemDetailActivity.EXTRA_ITEM_TYPE, ItemType.GAMES)
            intent.putExtra(GameDetailFragment.ARG_GAME_ID, id)

            if (fragment != null && transitionOptions != null)
                startActivityFromFragment(fragment, intent, 0, transitionOptions.toBundle())
            else
                startActivity(intent)

        }
    }

    private fun showPlayer(id: String, fragment: Fragment?, transitionOptions: ActivityOptions?) {
        if (isTwoPane) {
            val arguments = Bundle()
            arguments.putString(PlayerDetailFragment.ARG_PLAYER_ID, id)

            val newFrag = PlayerDetailFragment()
            newFrag.arguments = arguments

            supportFragmentManager.beginTransaction()
                    .replace(R.id.detail_container, newFrag)
                    .commit()
        } else {
            val intent = Intent(this, ItemDetailActivity::class.java)
            intent.putExtra(ItemDetailActivity.EXTRA_ITEM_TYPE, ItemType.PLAYERS)
            intent.putExtra(PlayerDetailFragment.ARG_PLAYER_ID, id)

            if (fragment != null && transitionOptions != null)
                startActivityFromFragment(fragment, intent, 0, transitionOptions.toBundle())
            else
                startActivity(intent)
        }
    }

    private fun showRun(id: String, fragment: Fragment?, transitionOptions: ActivityOptions?) {
        val intent = Intent(this, RunDetailActivity::class.java)
        intent.putExtra(RunDetailActivity.EXTRA_RUN_ID, id)

        if (fragment != null && transitionOptions != null)
            startActivityFromFragment(fragment, intent, 0, transitionOptions.toBundle())
        else
            startActivity(intent)
    }

    private fun showGenreFilterDialog() {
        val dialog = SelectGenreDialog(this, mDisposables!!)

        dialog.setOnDismissListener {

            if(dialog.selectedGenre != null && mSelectedGenre != dialog.selectedGenre) {
                mSelectedGenre = if(dialog.selectedGenre == Genre.ALL_GENRES_GENRE)
                    null
                else
                    dialog.selectedGenre

                (mViewPager!!.adapter as PagerAdapter).reloadSearchResults()
            }
        }

        dialog.show()
    }

    override fun onItemSelected(itemType: ItemType?, itemId: String, fragment: Fragment, options: ActivityOptions?) {
        when (itemType) {
            ItemType.GAMES -> showGame(itemId, fragment, options)
            ItemType.PLAYERS -> showPlayer(itemId, fragment, options)
            ItemType.RUNS -> showRun(itemId, fragment, options)
        }
    }

    override fun onItemClick(parent: AdapterView<*>, view: View, position: Int, id: Long) {
        val item = parent.adapter.getItem(position)
        if (item is User) {
            showPlayer(item.id, null, null)
        } else if (item is Game) {
            showGame(item.id, null, null)
        }
    }



    private fun viewStats() {
        val intent = Intent(this, SiteStatisticsActivity::class.java)
        startActivity(intent)
    }

    private inner class PagerAdapter(fm: FragmentManager) : FragmentPagerAdapter(fm), SimpleTabStrip.IconPagerAdapter {

        private val fragments: Array<ItemListFragment> = Array(5) { ItemListFragment() }

        init {
            var args = Bundle()
            args.putSerializable(ItemListFragment.ARG_ITEM_TYPE, ItemType.GAMES)
            fragments[0].arguments = args

            args = Bundle()
            args.putSerializable(ItemListFragment.ARG_ITEM_TYPE, ItemType.RUNS)
            fragments[1].arguments = args

            args = Bundle()
            args.putSerializable(ItemListFragment.ARG_ITEM_TYPE, ItemType.RUNS)
            fragments[2].arguments = args

            args = Bundle()
            args.putSerializable(ItemListFragment.ARG_ITEM_TYPE, ItemType.GAMES)
            fragments[3].arguments = args

            args = Bundle()
            args.putSerializable(ItemListFragment.ARG_ITEM_TYPE, ItemType.PLAYERS)
            fragments[4].arguments = args

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

                    Analytics.logItemView(this@GameListActivity, type, listName)
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
            return fragments.size
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
                        return if (mSelectedGenre != null)
                            SpeedrunMiddlewareAPI.make().listGamesByGenre(mSelectedGenre!!.id, offset).map<SpeedrunMiddlewareAPI.APIResponse<Any?>>(ItemListFragment.GenericMapper())
                        else
                            SpeedrunMiddlewareAPI.make().listGames(offset).map<SpeedrunMiddlewareAPI.APIResponse<Any?>>(ItemListFragment.GenericMapper())
                    }
                })
                1 -> fragments[1].setItemsSource(object : ItemListFragment.ItemSource {
                    override fun list(offset: Int): Observable<SpeedrunMiddlewareAPI.APIResponse<Any?>> {
                        return if (mSelectedGenre != null)
                            SpeedrunMiddlewareAPI.make().listLatestRunsByGenre(mSelectedGenre!!.id, offset).map<SpeedrunMiddlewareAPI.APIResponse<Any?>>(ItemListFragment.GenericMapper())
                        else
                            SpeedrunMiddlewareAPI.make().listLatestRuns(offset).map<SpeedrunMiddlewareAPI.APIResponse<Any?>>(ItemListFragment.GenericMapper())
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
        private val TAG = GameListActivity::class.java.simpleName

        private const val SAVED_MAIN_PAGER = "main_pager"
    }
}
