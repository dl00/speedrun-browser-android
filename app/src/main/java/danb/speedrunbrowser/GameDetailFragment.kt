package danb.speedrunbrowser

import android.animation.Animator
import android.annotation.SuppressLint
import android.content.Context.MODE_PRIVATE
import android.content.DialogInterface
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
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
import androidx.navigation.fragment.findNavController
import java.util.HashSet

import danb.speedrunbrowser.api.SpeedrunMiddlewareAPI
import danb.speedrunbrowser.api.objects.Category
import danb.speedrunbrowser.api.objects.Game
import danb.speedrunbrowser.api.objects.Level
import danb.speedrunbrowser.api.objects.Variable
import danb.speedrunbrowser.stats.GameStatisticsFragment
import danb.speedrunbrowser.utils.*
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
class GameDetailFragment : Fragment(), LeaderboardFragment.LeaderboardInteracter, ViewPager.OnPageChangeListener, SpeedrunBrowserActivity.MediaControlListener {

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
    private var mLeaderboardPager: ViewPager? = null

    private val mStartPositionCategory: Category? = null
    private val mStartPositionLevel: Level? = null

    private val subscribedLeaderbards = mutableSetOf<LeaderboardFragment>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setHasOptionsMenu(true)

        mDB = AppDatabase.make(context!!)

        val args = arguments

        when {
            args == null -> {
                Log.e(TAG, "No arguments provided")
                return
            }
            savedInstanceState != null -> {
                mGame = savedInstanceState.getSerializable(SAVED_GAME) as Game
                mVariableSelections = savedInstanceState.getSerializable(SAVED_FILTERS) as Variable.VariableSelections
            }
            args.containsKey(ARG_GAME_ID) -> {
                // Load the dummy content specified by the fragment
                // arguments. In a real-world scenario, use a Loader
                // to load content from a content provider.

                val gameId = args.getString(ARG_GAME_ID)!!

                mVariableSelections = when {
                    args.get(ARG_VARIABLE_SELECTIONS) != null -> args.getSerializable(ARG_VARIABLE_SELECTIONS) as Variable.VariableSelections
                    else -> loadFilterPreferences(gameId)
                }


                loadGame(gameId, args.getString(ARG_LEADERBOARD_ID))
            }
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

        if(item.itemId == R.id.menu_stats) {
            viewStats()
        }
        else if (item.itemId == R.id.menu_subscribe) {
            openSubscriptionDialog()
            return true
        }
        else if(item.itemId == R.id.menu_view_website && mGame != null) {
            startActivity(Util.openInBrowser(context!!, Uri.parse(mGame!!.weblink)))
        }

        return false
    }

    override fun addLeaderboard(frag: LeaderboardFragment) {
        subscribedLeaderbards.add(frag)
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

    private fun loadGame(gameId: String?, leaderboardId: String?): Disposable {
        Log.d(TAG, "Downloading game data: " + gameId!!)
        return SpeedrunMiddlewareAPI.make(context!!).listGames(gameId)
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
                    animateGameIn()
                    setViewData()

                    if(leaderboardId != null) {
                        switchToLeaderboard(leaderboardId)
                    }

                    Analytics.logItemView(context!!, "game", gameId)
                }, ConnectionErrorConsumer(context!!))
    }

    private fun switchToLeaderboard(leaderboardId: String) {
        val spl = leaderboardId.split('_')

        val category = mGame!!.categories!!.find { it.id == spl[0] }
        if(category != null) {

            val level = if(spl.size > 1) mGame!!.levels?.find { it.id == spl[1] } else null

            mLeaderboardPager!!.currentItem = (mLeaderboardPager!!.adapter as LeaderboardPagerAdapter)
                    .indexOf(category, level)
        }
    }

    private fun loadSubscription() {
        mDisposables.add(mDB.subscriptionDao().listOfTypeWithIDPrefix("game", mGame!!.id)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe { subscriptions ->
                    mSubscription = GameSubscription(mGame!!, subscriptions)
                    setMenu()
                })
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        if (savedInstanceState != null) {
            mLeaderboardPager?.onRestoreInstanceState(savedInstanceState.getParcelable<Parcelable>(SAVED_PAGER))
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

        mLeaderboardPager!!.addOnPageChangeListener(this)

        mCategoryTabStrip = rootView.findViewById(R.id.tabCategories)

        if (mGame != null) {
            //setupTabStrip()
            mSpinner!!.visibility = View.GONE
            mGameHeader!!.visibility = View.VISIBLE
        }

        mFiltersButton.setText(if (mVariableSelections!!.empty) R.string.button_filters else R.string.button_filters_used)
        mFiltersButton.setOnClickListener { openFiltersDialog() }

        setViewData()

        return rootView
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        if(mLeaderboardPager != null)
            outState.putParcelable(SAVED_PAGER, mLeaderboardPager!!.onSaveInstanceState())

        outState.putSerializable(SAVED_GAME, mGame)
        outState.putSerializable(SAVED_FILTERS, mVariableSelections)
    }

    private fun setupTabStrip() {
        if (mGame!!.categories!![0].variables!!.isEmpty())
            mFiltersButton.visibility = View.GONE
        else if (mVariableSelections == null)
            mVariableSelections = Variable.VariableSelections()

        mCategoryTabStrip!!.setup(mGame!!, mVariableSelections!!, mLeaderboardPager!!, childFragmentManager)

        if (mStartPositionCategory != null)
            mCategoryTabStrip!!.selectLeaderboard(mStartPositionCategory, mStartPositionLevel)
    }

    private fun animateGameIn() {
        val animTime = resources.getInteger(
                android.R.integer.config_shortAnimTime)

        if(mGameHeader != null) {
            mSpinner!!.animate()
                    .alpha(0.0f)
                    .setDuration(animTime.toLong())
                    .setListener(object : Animator.AnimatorListener {
                        override fun onAnimationStart(animator: Animator) {}

                        override fun onAnimationEnd(animator: Animator) {
                            mSpinner!!.visibility = View.GONE
                            mSpinner!!.stop()
                        }

                        override fun onAnimationCancel(animator: Animator) {}

                        override fun onAnimationRepeat(animator: Animator) {}
                    })

            mGameHeader!!.visibility = View.VISIBLE
            mGameHeader!!.alpha = 0.0f

            mGameHeader!!.animate()
                    .alpha(1.0f)
                    .setDuration(animTime.toLong())
                    .setListener(null)
        }
    }

    private fun setViewData() {
        if (mGame != null) {

            activity?.title = mGame?.resolvedName

            mReleaseDate.text = mGame!!.releaseDate

            // we have to join the string manually because it is java 7
            val sb = StringBuilder()
            for (i in mGame!!.platforms!!.indices) {
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

                val coverConsumer = ImageViewPlacerConsumer(mCover)
                coverConsumer.roundedCorners = resources.getDimensionPixelSize(R.dimen.game_cover_rounded_corners).toFloat()

                if (mGame!!.assets.coverLarge != null)
                    mDisposables.add(il.loadImage(mGame!!.assets.coverLarge!!.uri)
                            .subscribe(coverConsumer))
                if (mGame!!.assets.background != null && mBackground != null)
                    mDisposables.add(il.loadImage(mGame!!.assets.background!!.uri)
                            .subscribe(ImageViewPlacerConsumer(mBackground!!)))
            }
        }
    }

    private fun openFiltersDialog() {
        val dialog = FiltersDialog(requireContext(), mGame!!,
                currentVariables, mVariableSelections!!)

        dialog.show()

        dialog.setOnDismissListener {
            for (lbf in subscribedLeaderbards)
                lbf.notifyFilterChanged(mVariableSelections!!)

            recordFilterPreferences(mGame!!.id)

            mFiltersButton.setText(if (mVariableSelections!!.empty) R.string.button_filters else R.string.button_filters_used)

            mLeaderboardPager!!.requestFocus()
        }
    }

    private val currentVariables
    get() = mCategoryTabStrip!!.pagerAdapter!!.getCategoryOfIndex(mLeaderboardPager!!.currentItem).variables!!

    private fun recordFilterPreferences(gameId: String) {
        val prefs = context!!.getSharedPreferences(SHARED_PREFS_GAME_FILTERS + gameId, MODE_PRIVATE).edit()

        // platform and region are special
        val vars = currentVariables
                .map { it.id }
                .union(setOf("platform", "region"))

        for(cv in vars) {
            val cvv = mVariableSelections!!.getSelections(cv)
            if (cvv != null) {
                prefs.putStringSet(cv, cvv)
            }
            else {
                prefs.remove(cv)
            }
        }

        prefs.apply()
    }

    private fun loadFilterPreferences(gameId: String): Variable.VariableSelections {
        val prefs = context!!.getSharedPreferences(SHARED_PREFS_GAME_FILTERS + gameId, MODE_PRIVATE)

        val vs = Variable.VariableSelections()

        for (pref in prefs.all) {
            vs.selectOnly(pref.key, prefs.getStringSet(pref.key, setOf())!!)
        }

        return vs
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
                        Util.showMsgToast(requireContext(), getString(R.string.success_subscription))
                        mSubscription = dialog.subscriptions
                        setMenu()
                    }
            )
        })
    }

    private fun viewStats() {
        if(mGame != null) {
            findNavController().navigate(GameDetailFragmentDirections.actionGameDetailFragmentToGameStatisticsFragment(mGame!!.id))
        }
    }

    override fun onRewindPressed() {
        val lb = (mLeaderboardPager!!.adapter as LeaderboardPagerAdapter).getItem(mLeaderboardPager!!.currentItem) as LeaderboardFragment
        lb.viewInfo()
    }

    override fun onFastForwardPressed() {
        openFiltersDialog()
    }

    override fun onPlayPausePressed() {}

    class GameSubscription(game: Game, subs: Collection<AppDatabase.Subscription>) : HashSet<String>() {
        var game: Game? = game
            private set

        val baseSubscriptions: MutableSet<AppDatabase.Subscription>
            @SuppressLint("DefaultLocale")
            get() {

                val subs = HashSet<AppDatabase.Subscription>(size)

                for (sub in this) {
                    subs.add(AppDatabase.Subscription("game", game!!.id + "_" + sub, game!!.resolvedName.toLowerCase()))
                }

                return subs
            }

        init {
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
        const val ARG_GAME_ID = "gameId"
        const val ARG_LEADERBOARD_ID = "leaderboardId"
        const val ARG_VARIABLE_SELECTIONS = "variableSelections"

        /**
         * Saved state options
         */
        private const val SAVED_GAME = "game"
        private const val SAVED_PAGER = "pager"
        private const val SAVED_FILTERS = "variable_selections"

        private const val SHARED_PREFS_GAME_FILTERS = "game_filters_"
    }

    override fun onPageScrollStateChanged(state: Int) {}

    override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {}

    override fun onPageSelected(position: Int) {
        mLeaderboardPager!!.requestFocus()
    }
}
