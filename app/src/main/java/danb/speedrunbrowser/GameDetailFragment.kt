package danb.speedrunbrowser

import android.content.DialogInterface
import android.os.Bundle
import android.os.Parcelable
import androidx.fragment.app.Fragment
import androidx.viewpager.widget.ViewPager
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import java.util.HashSet

import danb.speedrunbrowser.api.SpeedrunMiddlewareAPI
import danb.speedrunbrowser.api.objects.Category
import danb.speedrunbrowser.api.objects.Game
import danb.speedrunbrowser.api.objects.Level
import danb.speedrunbrowser.api.objects.Variable
import danb.speedrunbrowser.utils.Analytics
import danb.speedrunbrowser.utils.AppDatabase
import danb.speedrunbrowser.utils.ConnectionErrorConsumer
import danb.speedrunbrowser.utils.ImageLoader
import danb.speedrunbrowser.utils.ImageViewPlacerConsumer
import danb.speedrunbrowser.utils.SubscriptionChanger
import danb.speedrunbrowser.utils.Util
import danb.speedrunbrowser.views.CategoryTabStrip
import danb.speedrunbrowser.views.ProgressSpinnerView
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable
import io.reactivex.functions.Consumer
import io.reactivex.schedulers.Schedulers

/**
 * A fragment representing a single Game detail screen.
 * This fragment is either contained in a [GameListActivity]
 * in two-pane mode (on tablets) or a [ItemDetailActivity]
 * on handsets.
 */
/**
 * Mandatory empty constructor for the fragment manager to instantiate the
 * fragment (e.g. upon screen orientation changes).
 */
class GameDetailFragment : Fragment() {

    private lateinit var mDB: AppDatabase

    /**
     * The dummy content this fragment is presenting.
     */
    private var mGame: Game? = null
    private var mVariableSelections: Variable.VariableSelections? = null
    private var mSubscription: GameSubscription? = null

    private var mMenu: Menu? = null

    /**
     * Game detail view views
     */
    private var mSpinner: ProgressSpinnerView? = null
    private var mGameHeader: View? = null

    private var mDisposables = CompositeDisposable()

    private lateinit var mReleaseDate: TextView
    private lateinit var mPlatformList: TextView

    private lateinit var mFiltersButton: Button

    private lateinit var mCover: ImageView
    private var mBackground: ImageView? = null

    private var mCategoryTabStrip: CategoryTabStrip? = null
    private lateinit var mLeaderboardPager: ViewPager

    private val mStartPositionCategory: Category? = null
    private val mStartPositionLevel: Level? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setHasOptionsMenu(true)

        mDB = AppDatabase.make(context!!)

        if (savedInstanceState != null) {
            mVariableSelections = savedInstanceState.getSerializable(SAVED_FILTERS) as Variable.VariableSelections
        } else {
            mVariableSelections = Variable.VariableSelections()
        }

        val args = arguments

        if (args == null) {
            Log.e(TAG, "No arguments provided")
            return
        }
        else if(savedInstanceState != null) {
            mGame = savedInstanceState.getSerializable(SAVED_GAME) as Game
        }
        else if (args.containsKey(ARG_GAME_ID)) {
            // Load the dummy content specified by the fragment
            // arguments. In a real-world scenario, use a Loader
            // to load content from a content provider.

            val gameId = args.getString(ARG_GAME_ID)
            loadGame(gameId)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mDisposables.dispose()
        mDB.close()
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.game, menu)
        mMenu = menu
        setMenu()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.menu_subscribe) {
            openSubscriptionDialog()
            return true
        }

        return false
    }

    private fun setMenu() {
        if (mMenu == null)
            return

        val subscribeItem = mMenu!!.findItem(R.id.menu_subscribe)
        subscribeItem.isVisible = mSubscription != null
        if (mSubscription != null && !mSubscription!!.isEmpty()) {
            subscribeItem.setIcon(R.drawable.baseline_star_24)
            subscribeItem.setTitle(R.string.menu_unsubscribe)
        } else {
            subscribeItem.setIcon(R.drawable.baseline_star_border_24)
            subscribeItem.setTitle(R.string.menu_subscribe)
        }
    }

    fun loadGame(gameId: String?): Disposable {
        Log.d(TAG, "Downloading game data: " + gameId!!)
        return SpeedrunMiddlewareAPI.make().listGames(gameId)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(Consumer { gameAPIResponse ->

                    if (gameAPIResponse.error != null) {
                        Util.showErrorToast(context!!, getString(R.string.error_could_not_connect))
                    }

                    if (gameAPIResponse.data!!.isEmpty()) {
                        // game was not able to be found for some reason?
                        Util.showErrorToast(context!!, getString(R.string.error_missing_game, gameId))
                        return@Consumer
                    }

                    mGame = gameAPIResponse.data[0]
                    loadSubscription()
                    setViewData()

                    Analytics.logItemView(context!!, "game", gameId)
                }, ConnectionErrorConsumer(context!!))
    }

    private fun loadSubscription() {
        println("Load subscription " + mGame!!.id)
        mDisposables.add(mDB.subscriptionDao().listOfTypeWithIDPrefix("game", mGame!!.id)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe { subscriptions ->
                    println(subscriptions)
                    mSubscription = GameSubscription(mGame!!, subscriptions)
                    setMenu()
                })
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        if (savedInstanceState != null) {
            mLeaderboardPager.onRestoreInstanceState(savedInstanceState.getParcelable<Parcelable>(SAVED_PAGER))
            Log.d(TAG, "Loaded from saved instance state")
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        val rootView = inflater.inflate(R.layout.game_detail, container, false)

        mSpinner = rootView.findViewById(R.id.spinner)
        mGameHeader = rootView.findViewById(R.id.gameInfoHead)

        mReleaseDate = rootView.findViewById(R.id.txtReleaseDate)
        mPlatformList = rootView.findViewById(R.id.txtPlatforms)

        mFiltersButton = rootView.findViewById(R.id.filtersButton)

        mCover = rootView.findViewById(R.id.imgCover)
        mBackground = rootView.findViewById(R.id.imgBackground)

        mLeaderboardPager = rootView.findViewById(R.id.pageLeaderboard)

        mCategoryTabStrip = rootView.findViewById(R.id.tabCategories)

        if (mGame != null) {
            setupTabStrip()
        }

        mFiltersButton.setOnClickListener { openFiltersDialog() }

        setViewData()

        return rootView
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        val pa = mCategoryTabStrip!!.pagerAdapter

        outState.putParcelable(SAVED_PAGER, mLeaderboardPager.onSaveInstanceState())
        outState.putSerializable(SAVED_GAME, mGame)
        outState.putSerializable(SAVED_FILTERS, mVariableSelections)
    }

    private fun setupTabStrip() {
        if (mGame!!.categories!![0].variables!!.isEmpty())
            mFiltersButton.visibility = View.GONE
        else if (mVariableSelections == null)
            mVariableSelections = Variable.VariableSelections()

        mCategoryTabStrip!!.setup(mGame!!, mVariableSelections!!, mLeaderboardPager, childFragmentManager)

        if (mStartPositionCategory != null)
            mCategoryTabStrip!!.selectLeaderboard(mStartPositionCategory, mStartPositionLevel)
    }

    private fun setViewData() {
        if (mGame != null) {

            activity?.title = mGame?.resolvedName

            mReleaseDate.text = mGame!!.releaseDate

            // we have to join the string manually because it is java 7
            val sb = StringBuilder()
            for (i in 0 until mGame!!.platforms!!.size) {
                sb.append(mGame!!.platforms!![i].name)
                if (i < mGame!!.platforms!!.size - 1)
                    sb.append(", ")
            }

            mPlatformList.text = sb.toString()

            // leaderboards
            if (mCategoryTabStrip != null) {
                setupTabStrip()
            }

            val ctx = context
            if (ctx != null) {

                val il = ImageLoader(ctx)

                if (mGame!!.assets.coverLarge != null)
                    mDisposables.add(il.loadImage(mGame!!.assets.coverLarge!!.uri)
                            .subscribe(ImageViewPlacerConsumer(mCover)))
                if (mGame!!.assets.background != null && mBackground != null)
                    mDisposables.add(il.loadImage(mGame!!.assets.background!!.uri)
                            .subscribe(ImageViewPlacerConsumer(mBackground!!)))
            }

            mSpinner!!.visibility = View.GONE
            mGameHeader!!.visibility = View.VISIBLE

        }
    }

    private fun openFiltersDialog() {
        val dialog = FiltersDialog(context!!, mGame!!,
                mCategoryTabStrip!!.pagerAdapter!!.getCategoryOfIndex(mLeaderboardPager.currentItem).variables!!, mVariableSelections!!)

        dialog.show()

        dialog.setOnDismissListener { mCategoryTabStrip!!.pagerAdapter!!.notifyFilterChanged() }
    }

    private fun openSubscriptionDialog() {

        val dialog = GameSubscribeDialog(context!!, mSubscription!!.clone() as GameSubscription)

        dialog.show()

        dialog.setOnDismissListener(DialogInterface.OnDismissListener {
            val newSubs = dialog.subscriptions.baseSubscriptions
            val oldSubs = mSubscription!!.baseSubscriptions
            val delSubs: MutableSet<AppDatabase.Subscription> = HashSet(oldSubs)

            delSubs.removeAll(newSubs)
            newSubs.removeAll(oldSubs)

            println("Started the subscription changer: $newSubs, $delSubs")

            if (newSubs.isEmpty() && delSubs.isEmpty()) {
                // no change
                return@OnDismissListener
            }

            val psv = ProgressSpinnerView(context!!, null)
            psv.setDirection(ProgressSpinnerView.Direction.RIGHT)
            psv.setScale(0.5f)

            mMenu!!.findItem(R.id.menu_subscribe).actionView = psv

            val sc = SubscriptionChanger(context!!, mDB)

            // change all the subscriptions async in one go
            mDisposables.add(Observable.fromIterable<AppDatabase.Subscription>(delSubs)
                    .flatMapCompletable {
                        subscription -> sc.unsubscribeFrom(subscription)
                    }
                    .mergeWith(Observable.fromIterable<AppDatabase.Subscription>(newSubs)
                            .flatMapCompletable {
                                subscription -> sc.subscribeTo(subscription)
                            }
                    )
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe {
                        mMenu!!.findItem(R.id.menu_subscribe).actionView = null
                        Util.showMsgToast(context!!, getString(R.string.success_subscription))
                        mSubscription = dialog.subscriptions
                        setMenu()
                    }
            )
        })
    }

    class GameSubscription : HashSet<String> {
        var game: Game? = null
            private set

        val baseSubscriptions: MutableSet<AppDatabase.Subscription>
            get() {

                val subs = HashSet<AppDatabase.Subscription>(size)

                for (sub in this) {
                    subs.add(AppDatabase.Subscription("game", game!!.id + "_" + sub, game!!.resolvedName.toLowerCase()))
                }

                return subs
            }

        constructor(game: Game) {
            this.game = game
        }

        constructor(game: Game, subs: Collection<AppDatabase.Subscription>) {
            this.game = game

            for ((_, resourceId) in subs) {
                add(resourceId.substring(resourceId.indexOf('_') + 1))
            }
        }
    }

    companion object {

        val TAG = GameDetailFragment::class.java.simpleName

        /**
         * The fragment argument representing the item ID that this fragment
         * represents.
         */
        const val ARG_GAME_ID = "game_id"

        /**
         * Saved state options
         */
        private const val SAVED_GAME = "game"
        private const val SAVED_PAGER = "pager"
        private const val SAVED_FILTERS = "variable_selections"
    }
}