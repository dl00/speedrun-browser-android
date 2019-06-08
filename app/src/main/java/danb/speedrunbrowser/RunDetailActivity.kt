package danb.speedrunbrowser

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Point
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Display
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView

import com.google.android.flexbox.FlexboxLayout
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import java.util.Objects
import java.util.concurrent.TimeUnit

import androidx.appcompat.app.AppCompatActivity
import danb.speedrunbrowser.api.SpeedrunMiddlewareAPI
import danb.speedrunbrowser.api.objects.Category
import danb.speedrunbrowser.api.objects.Game
import danb.speedrunbrowser.api.objects.LeaderboardRunEntry
import danb.speedrunbrowser.api.objects.Level
import danb.speedrunbrowser.api.objects.MediaLink
import danb.speedrunbrowser.api.objects.Platform
import danb.speedrunbrowser.api.objects.Region
import danb.speedrunbrowser.api.objects.Run
import danb.speedrunbrowser.api.objects.User
import danb.speedrunbrowser.api.objects.Variable
import danb.speedrunbrowser.utils.Analytics
import danb.speedrunbrowser.utils.AppDatabase
import danb.speedrunbrowser.utils.ConnectionErrorConsumer
import danb.speedrunbrowser.utils.ImageLoader
import danb.speedrunbrowser.utils.ImageViewPlacerConsumer
import danb.speedrunbrowser.utils.NoopConsumer
import danb.speedrunbrowser.utils.Util
import danb.speedrunbrowser.views.MultiVideoView
import danb.speedrunbrowser.views.ProgressSpinnerView
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable
import io.reactivex.functions.Action
import io.reactivex.functions.Consumer
import io.reactivex.internal.operators.flowable.FlowableInterval
import io.reactivex.schedulers.Schedulers

class RunDetailActivity : AppCompatActivity(), MultiVideoView.Listener {

    private var mDisposables = CompositeDisposable()

    private var mDisposableBackgroundSaveInterval: Disposable? = null

    /**
     * Game detail views
     */

    private lateinit var mRootView: LinearLayout

    private lateinit var mSpinner: ProgressSpinnerView
    private lateinit var mGameInfoPane: LinearLayout
    private lateinit var mRunFooterPane: LinearLayout
    private lateinit var mGameName: TextView
    private lateinit var mReleaseDate: TextView
    private lateinit var mPlatformList: TextView

    private lateinit var mCover: ImageView

    private lateinit var mCategoryName: TextView
    private lateinit var mVariableChips: ChipGroup
    private lateinit var mPlayerNames: FlexboxLayout
    private lateinit var mRunTime: TextView

    private lateinit var mRunComment: TextView

    private lateinit var mRunSplits: ListView
    private lateinit var mRunEmptySplits: TextView

    /**
     * Video views
     */
    private lateinit var mVideoFrame: MultiVideoView

    private lateinit var mDB: AppDatabase

    private var mGame: Game? = null
    private var mCategory: Category? = null
    private var mLevel: Level? = null
    private var mRun: Run? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_run_detail)

        mDB = AppDatabase.make(this)

        mRootView = findViewById(R.id.contentLayout)
        mSpinner = findViewById(R.id.spinner)
        mGameInfoPane = findViewById(R.id.gameInfoHead)
        mRunFooterPane = findViewById(R.id.runFooter)
        mGameName = findViewById(R.id.txtGameName)
        mReleaseDate = findViewById(R.id.txtReleaseDate)
        mPlatformList = findViewById(R.id.txtPlatforms)
        mCover = findViewById(R.id.imgCover)
        mCategoryName = findViewById(R.id.txtCategoryName)
        mVariableChips = findViewById(R.id.chipsVariables)
        mPlayerNames = findViewById(R.id.txtPlayerNames)
        mRunTime = findViewById(R.id.txtRunTime)
        mVideoFrame = findViewById(R.id.videoFrame)

        mRunComment = findViewById(R.id.txtRunComment)

        mRunSplits = findViewById(R.id.runSplitsList)
        mRunEmptySplits = findViewById(R.id.emptySplits)

        val args = intent.extras!!

        if (args.getSerializable(EXTRA_RUN) != null) {
            mRun = args.getSerializable(EXTRA_RUN) as Run

            mGame = args.getSerializable(EXTRA_GAME) as Game
            mCategory = args.getSerializable(EXTRA_CATEGORY) as Category
            mLevel = args.getSerializable(EXTRA_LEVEL) as Level

            onDataReady()
        } else if (args.getString(EXTRA_RUN_ID) != null) {
            loadRun(args.getString(EXTRA_RUN_ID))
        } else if (intent.data != null) {
            val appLinkIntent = intent
            val appLinkData = appLinkIntent.data

            val segments = appLinkData!!.pathSegments

            if (segments.size >= 3) {
                val runId = segments[2]

                loadRun(runId)
            } else {
                Util.showErrorToast(this, getString(R.string.error_invalod_url, appLinkData))
            }
        } else {

            mGame = args.getSerializable(EXTRA_GAME) as Game
            mCategory = args.getSerializable(EXTRA_CATEGORY) as Category
            mLevel = args.getSerializable(EXTRA_LEVEL) as Level
            mRun = args.getSerializable(EXTRA_RUN) as Run

            onDataReady()
        }

        mGameInfoPane.setOnClickListener { viewGame() }
    }

    private fun loadRun(runId: String?) {
        Log.d(TAG, "Download runId: " + runId!!)
        mDisposables.add(SpeedrunMiddlewareAPI.make().listRuns(runId)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(Consumer { (data) ->
                    if (data == null) {
                        // game was not able to be found for some reason?
                        Util.showErrorToast(this@RunDetailActivity, getString(R.string.error_missing_game, runId))
                        return@Consumer
                    }

                    mRun = data[0].run
                    mGame = mRun!!.game
                    mCategory = mRun!!.category
                    mLevel = mRun!!.level

                    onDataReady()
                }, ConnectionErrorConsumer(this)))
    }

    override fun onResume() {
        super.onResume()


        // set an interval to record the watch time
        mDisposableBackgroundSaveInterval = FlowableInterval(BACKGROUND_SEEK_SAVE_START.toLong(), BACKGROUND_SEEK_SAVE_PERIOD.toLong(), TimeUnit.SECONDS, Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ recordStartPlaybackTime() }, { throwable -> Log.w(TAG, "Problem running background save interval: ", throwable) })
    }

    override fun onPause() {
        super.onPause()

        mDisposableBackgroundSaveInterval!!.dispose()
    }

    override fun onDestroy() {
        super.onDestroy()
        mDisposables.dispose()
        mDB.close()
    }

    private fun onDataReady() {

        setViewData()
        mSpinner.visibility = View.GONE

        Analytics.logItemView(this, "run", mRun!!.id)

        onConfigurationChanged(resources.configuration)

        // check watch history to set video start time
        mDisposables.add(mDB.watchHistoryDao()[mRun!!.id]
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(Consumer { (_, _, seekPos) ->
                    Log.d(TAG, "Got seek record for run: " + mRun!!.id)
                    mVideoFrame.seekTime = seekPos.toInt()
                    onVideoReady()
                }, NoopConsumer(), Action {
                    println("No seek record for run: " + mRun!!.id)
                    onVideoReady()
                }))
    }

    private fun onVideoReady() {

        mVideoFrame.setListener(this)

        if (mRun!!.videos == null || mRun!!.videos!!.links == null || mRun!!.videos!!.links!!.isEmpty()) {
            mVideoFrame.setVideoNotAvailable()

            return
        }

        // find the first available video recognized
        for (ml in mRun!!.videos!!.links!!) {
            if (mVideoFrame.loadVideo(ml))
                break
        }

        if (!mVideoFrame.hasLoadedVideo()) {
            Log.w(TAG, "Could not play a video for this run")
            // just record the fact that the video page was accessed
            mVideoFrame.setVideoFrameOther(mRun!!.videos!!.links!![0])
            writeWatchToDb(0)
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        if (mSpinner.visibility == View.VISIBLE)
            return

        if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            mRootView.background = ColorDrawable(resources.getColor(android.R.color.black))

            // if the screen's aspect ratio is < 16:9, re-orient the text view so it still centers properly
            val displ = windowManager.defaultDisplay
            val size = Point()
            displ.getSize(size)

            if (size.x.toFloat() / size.y < 16.0f / 9)
                mRootView.orientation = LinearLayout.HORIZONTAL

            // hide things
            mGameInfoPane.visibility = View.GONE
            mRunFooterPane.visibility = View.GONE

        } else {
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
            mRootView.background = ColorDrawable(resources.getColor(R.color.colorPrimary))

            // layout should always be vertical in this case
            mRootView.orientation = LinearLayout.VERTICAL

            // show things
            mGameInfoPane.visibility = View.VISIBLE
            mRunFooterPane.visibility = View.VISIBLE
        }
    }

    private fun recordStartPlaybackTime() {
        if (mVideoFrame.hasLoadedVideo())
            writeWatchToDb(mVideoFrame.seekTime.toLong())
    }

    private fun writeWatchToDb(seekTime: Long) {

        Log.d(TAG, "Record seek time: $seekTime")

        mDisposables.add(mDB.watchHistoryDao().record(AppDatabase.WatchHistoryEntry(mRun!!.id, seekTime, 0))
                .subscribeOn(Schedulers.io())
                .subscribe())
    }

    private fun setViewData() {
        mGameName.text = mGame!!.resolvedName
        mReleaseDate.text = mGame!!.releaseDate

        // we have to join the string manually because it is java 7
        if (mGame!!.platforms != null) {
            val sb = StringBuilder()
            for (i in 0 until mGame!!.platforms!!.size) {
                sb.append(mGame!!.platforms!![i].name)
                if (i < mGame!!.platforms!!.size - 1)
                    sb.append(", ")
            }

            mPlatformList.text = sb.toString()
        } else {
            mPlatformList.text = ""
        }

        mVariableChips.removeAllViews()

        val fullCategoryName = StringBuilder(mCategory!!.name)
        if (mLevel != null)
            fullCategoryName.append(" \u2022 ").append(mLevel!!.name)

        if (mGame!!.shouldShowPlatformFilter() && mRun!!.system != null) {
            val chip = Chip(this)

            for ((id, name) in mGame!!.platforms!!) {
                if (id == mRun!!.system!!.platform) {
                    chip.text = name
                    mVariableChips.addView(chip)
                    break
                }
            }
        }

        if (mGame!!.shouldShowRegionFilter() && mRun!!.system != null) {
            val chip = Chip(this)

            for ((id, name) in mGame!!.regions!!) {
                if (id == mRun!!.system!!.region) {
                    chip.text = name
                    mVariableChips.addView(chip)
                    break
                }
            }
        }

        if (mCategory!!.variables != null) {
            for ((id, name, _, _, _, _, isSubcategory, values) in mCategory!!.variables!!) {
                if (mRun!!.values!!.containsKey(id) && !isSubcategory && values.containsKey(mRun!!.values!![id])) {
                    val chip = Chip(this)
                    chip.text = StringBuilder(name).append(": ").append(values[mRun!!.values!![id]]!!.label)
                    mVariableChips.addView(chip)
                } else if (isSubcategory && values.containsKey(mRun!!.values!![id])) {
                    fullCategoryName.append(" \u2022 ").append(values[mRun!!.values!![id]]!!.label)
                }
            }
        }

        if (mGame!!.assets.coverLarge != null)
            mDisposables.add(
                    ImageLoader(this).loadImage(mGame!!.assets.coverLarge!!.uri)
                            .subscribe(ImageViewPlacerConsumer(mCover)))

        mPlayerNames.removeAllViews()
        for (player in mRun!!.players!!) {
            val tv = TextView(this)
            tv.textSize = 16f
            player.applyTextView(tv)

            val padding = resources.getDimensionPixelSize(R.dimen.half_fab_margin)

            tv.setPadding(padding, padding, padding, padding)

            tv.setOnClickListener { viewPlayer(player) }

            mPlayerNames.addView(tv)
        }

        mCategoryName.text = fullCategoryName
        mRunTime.text = mRun!!.times!!.time

        mRunComment.text = mRun!!.comment

        val emptyTv = TextView(this)

        emptyTv.setText(R.string.empty_no_splits)
    }

    private fun viewPlayer(player: User) {
        val intent = Intent(this, ItemDetailActivity::class.java)
        intent.putExtra(ItemDetailActivity.EXTRA_ITEM_TYPE, ItemListFragment.ItemType.PLAYERS)
        intent.putExtra(PlayerDetailFragment.ARG_PLAYER_ID, player.id)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            intent.flags = Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT
        }

        startActivity(intent)
    }

    private fun viewGame() {
        val intent = Intent(this, ItemDetailActivity::class.java)
        intent.putExtra(ItemDetailActivity.EXTRA_ITEM_TYPE, ItemListFragment.ItemType.GAMES)
        intent.putExtra(GameDetailFragment.ARG_GAME_ID, mGame!!.id)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            intent.flags = Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT
        }

        startActivity(intent)
    }

    override fun onFullscreenToggleListener() {
        requestedOrientation = if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE)
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        else
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE

        // prevent screen rotation from being locked
        mDisposables.add(Observable.timer(SCREEN_LOCK_ROTATE_PERIOD.toLong(), TimeUnit.SECONDS)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe { requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED })
    }

    companion object {
        private val TAG = RunDetailActivity::class.java.simpleName

        const val EXTRA_GAME = "game"
        const val EXTRA_CATEGORY = "category"
        const val EXTRA_LEVEL = "level"
        const val EXTRA_RUN = "run"

        const val EXTRA_RUN_ID = "runId"

        /// how often to save the current watch position/time to the watch history db
        private const val BACKGROUND_SEEK_SAVE_START = 15
        private const val BACKGROUND_SEEK_SAVE_PERIOD = 30

        /// amount of time to hold the screen in a certain rotation after pressing the fullscreen button
        private const val SCREEN_LOCK_ROTATE_PERIOD = 5
    }
}
