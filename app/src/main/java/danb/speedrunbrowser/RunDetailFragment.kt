package danb.speedrunbrowser

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Point
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.preference.PreferenceManager
import androidx.tvprovider.media.tv.TvContractCompat
import androidx.tvprovider.media.tv.WatchNextProgram
import com.google.android.flexbox.FlexboxLayout
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import danb.speedrunbrowser.api.SpeedrunAPI
import danb.speedrunbrowser.api.SpeedrunMiddlewareAPI
import danb.speedrunbrowser.api.objects.*
import danb.speedrunbrowser.utils.*
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
import java.util.concurrent.TimeUnit

class RunDetailFragment : Fragment(), MultiVideoView.Listener {

    private var mDisposables = CompositeDisposable()

    private var mDisposableBackgroundSaveInterval: Disposable? = null

    /**
     * Game detail views
     */

    private lateinit var mRootView: LinearLayout

    private lateinit var mSpinner: ProgressSpinnerView
    private lateinit var mGameInfoPane: LinearLayout
    private lateinit var mInfoHeader: LinearLayout
    private lateinit var mRunFooterPane: LinearLayout
    private lateinit var mGameName: TextView
    private lateinit var mReleaseDate: TextView
    private lateinit var mPlatformList: TextView

    private lateinit var mCover: ImageView

    private lateinit var mCategoryName: TextView
    private lateinit var mVariableChips: ChipGroup
    private lateinit var mPlayerNames: FlexboxLayout
    private lateinit var mRunPlaceImg: ImageView
    private lateinit var mRunPlaceTxt: TextView
    private lateinit var mRunTime: TextView
    private lateinit var mRunDate: TextView
    private lateinit var mRunSubmitted: TextView

    private lateinit var mRunComment: TextView

    private lateinit var mRunSplits: ListView

    private lateinit var mViewOnOfficial: Button

    private lateinit var mModerationTools: LinearLayout

    /**
     * Video views
     */
    private lateinit var mVideoFrame: FrameLayout

    private var mVideoView: MultiVideoView? = null

    private lateinit var mDB: AppDatabase

    private var mGame: Game? = null
    private var mCategory: Category? = null
    private var mLevel: Level? = null
    private var mRun: LeaderboardRunEntry? = null

    private var mModerationList = listOf<String>()

    private var mModerationIndex = 0

    private lateinit var mSrcApiKey: String

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {

        mSrcApiKey = PreferenceManager.getDefaultSharedPreferences(requireContext()).getString("srcApiKey", "")!!

        val v = inflater.inflate(R.layout.fragment_run_detail, container, false)

        mDB = AppDatabase.make(requireContext())

        mRootView = v.findViewById(R.id.contentLayout)
        mSpinner = v.findViewById(R.id.spinner)
        mGameInfoPane = v.findViewById(R.id.gameInfoHead)
        mInfoHeader = v.findViewById(R.id.headerLayout)
        mRunFooterPane = v.findViewById(R.id.runFooter)
        mGameName = v.findViewById(R.id.txtGameName)
        mReleaseDate = v.findViewById(R.id.txtReleaseDate)
        mPlatformList = v.findViewById(R.id.txtPlatforms)
        mCover = v.findViewById(R.id.imgCover)
        mCategoryName = v.findViewById(R.id.txtCategoryName)
        mVariableChips = v.findViewById(R.id.chipsVariables)
        mPlayerNames = v.findViewById(R.id.txtPlayerNames)
        mRunPlaceImg = v.findViewById(R.id.imgRunPlace)
        mRunPlaceTxt = v.findViewById(R.id.txtRunPlace)
        mRunTime = v.findViewById(R.id.txtRunTime)
        mRunDate = v.findViewById(R.id.txtRunDate)
        mRunSubmitted = v.findViewById(R.id.txtRunSubmitted)
        mVideoFrame = v.findViewById(R.id.videoFrame)
        mViewOnOfficial = v.findViewById(R.id.buttonViewOnOfficial)

        mRunComment = v.findViewById(R.id.txtRunComment)

        mRunSplits = v.findViewById(R.id.runSplitsList)

        mModerationTools = v.findViewById(R.id.layoutApproveOrReject)

        mModerationTools.findViewById<Button>(R.id.buttonApprove).setOnClickListener { doApprove() }
        mModerationTools.findViewById<Button>(R.id.buttonReject).setOnClickListener { doReject() }
        mModerationTools.findViewById<Button>(R.id.buttonSkip).setOnClickListener { doNextModeration() }

        if(activity is VideoSupplier)
            mVideoView = (activity as VideoSupplier).requestVideoView()
        else if(context is VideoSupplier)
            mVideoView = (context as VideoSupplier).requestVideoView()

        mVideoFrame.addView(mVideoView)

        if(mVideoView != null) {
            if(mVideoView!!.parent != null)
                (mVideoView!!.parent as ViewGroup).removeView(mVideoView)

            mVideoFrame.addView(mVideoView)
        }
        else
            throw UnsupportedOperationException("Cannot initialize fragment: video frame not supplied")

        if(arguments == null) {
            Log.w(TAG, "Cannot initialize fragment: no arguments")
            return null
        }

        val args = requireArguments()

        mModerationList = args.getStringArrayList(ARG_MODERATION_LIST) ?: listOf()

        when {
            mModerationList.isNotEmpty() -> {
                doNextModeration(false)
                mModerationTools.visibility = View.VISIBLE
            }
            args.getSerializable(ARG_RUN) != null -> {
                mRun = args.getSerializable(ARG_RUN) as LeaderboardRunEntry

                mGame = args.getSerializable(ARG_GAME) as Game
                mCategory = args.getSerializable(ARG_CATEGORY) as Category
                mLevel = args.getSerializable(ARG_LEVEL) as Level

                onDataReady()
            }
            args.getString(ARG_RUN_ID) != null -> {
                loadRun(args.getString(ARG_RUN_ID))
            }
            else -> {

                mGame = args.getSerializable(ARG_GAME) as Game
                mCategory = args.getSerializable(ARG_CATEGORY) as Category
                mLevel = args.getSerializable(ARG_LEVEL) as Level
                mRun = args.getSerializable(ARG_RUN) as LeaderboardRunEntry

                onDataReady()
            }
        }

        v.findViewById<Button>(R.id.buttonViewRules).setOnClickListener { viewRules() }
        v.findViewById<ImageView>(R.id.imgShare).setOnClickListener { doShare() }

        v.findViewById<Button>(R.id.buttonApprove).setOnClickListener { doApprove() }
        v.findViewById<Button>(R.id.buttonReject).setOnClickListener { doReject() }

        mGameInfoPane.getChildAt(0).setOnClickListener { viewGame() }
        mViewOnOfficial.setOnClickListener { viewOnOfficial() }

        return v
    }

    private fun loadRun(runId: String?) {
        Log.d(TAG, "Download runId: " + runId!!)
        mDisposables.add(SpeedrunMiddlewareAPI.make(requireContext()).listRuns(runId)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(Consumer { (data) ->
                    if (data == null) {
                        // game was not able to be found for some reason?
                        Util.showErrorToast(requireContext(), getString(R.string.error_missing_game, runId))
                        return@Consumer
                    }

                    mRun = data[0]!!
                    mGame = mRun!!.run.game
                    mCategory = mRun!!.run.category
                    mLevel = mRun!!.run.level

                    onDataReady()
                }, ConnectionErrorConsumer(requireContext())))
    }

    override fun onResume() {
        super.onResume()

        requireActivity().title = ""
        onConfigurationChanged(resources.configuration)
    }

    override fun onPause() {
        super.onPause()

        mVideoView?.pause()

        mDisposableBackgroundSaveInterval!!.dispose()
    }

    override fun onStart() {
        super.onStart()

        // set an interval to record the watch time
        mDisposableBackgroundSaveInterval = FlowableInterval(BACKGROUND_SEEK_SAVE_START.toLong(), BACKGROUND_SEEK_SAVE_PERIOD.toLong(), TimeUnit.SECONDS, Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ recordStartPlaybackTime() }, { throwable -> Log.w(TAG, "Problem running background save interval: ", throwable) })
    }

    override fun onStop() {
        super.onStop()

        // save the video to more watching if not close to done
        if (mVideoView != null && context != null && mVideoView!!.seekTime < mVideoView!!.lengthTime * 0.9) {
            val builder = WatchNextProgram.Builder()
                    .setType(TvContractCompat.WatchNextPrograms.TYPE_CLIP)
                    .setWatchNextType(TvContractCompat.WatchNextPrograms.WATCH_NEXT_TYPE_CONTINUE)
                    .setTitle(getString(R.string.msg_share_run, User.printPlayerNames(mRun!!.run.players!!),
                            mRun!!.run.game!!.resolvedName,
                            mRun!!.run.times?.time ?: "", mRun!!.run.weblink))
                    .setAuthor(mRun!!.run.players!![0].name)
                    .setReleaseDate(mRun!!.run.date)
                    .setLastEngagementTimeUtcMillis(System.currentTimeMillis())
                    .setThumbnailAspectRatio(TvContractCompat.PreviewPrograms.ASPECT_RATIO_MOVIE_POSTER)
                    .setThumbnailUri(Uri.parse(mRun!!.run.game!!.assets.coverLarge!!.uri.toString()))
                    .setPosterArtAspectRatio(TvContractCompat.PreviewPrograms.ASPECT_RATIO_MOVIE_POSTER)
                    .setPosterArtUri(Uri.parse(mRun!!.run.game!!.assets.coverLarge!!.uri.toString()))
                    .setIntentUri(Uri.parse(mRun!!.run.weblink!!))
                    .setInternalProviderId(mRun!!.run.id)
                    .setId(mRun!!.run.id.hashCode().toLong())

            try {
                requireContext().contentResolver
                        .insert(TvContractCompat.WatchNextPrograms.CONTENT_URI,
                                builder.build().toContentValues())
            } catch (err: IllegalArgumentException) {}
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mDisposables.dispose()
        if (mDB.isOpen) {
            mDB.close()
        }
    }

    private fun onDataReady() {

        setViewData()
        mSpinner.visibility = View.GONE

        Analytics.logItemView(requireContext(), "run", mRun!!.run.id)

        onConfigurationChanged(resources.configuration)

        // check watch history to set video start time
        mDisposables.add(mDB.watchHistoryDao()[mRun!!.run.id]
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(Consumer { (_, seekPos) ->
                    Log.d(TAG, "Got seek record for run: " + mRun!!.run.id + ", " + seekPos)
                    mVideoView!!.seekTime = seekPos.toInt()
                    onVideoReady()
                }, NoopConsumer(), Action {
                    mVideoView!!.seekTime = 0
                    onVideoReady()
                }))
    }

    private fun onVideoReady() {

        mVideoView!!.setListener(this)

        if (mRun!!.run.videos == null || mRun!!.run.videos!!.links == null || mRun!!.run.videos!!.links!!.isEmpty()) {
            mVideoView!!.setVideoNotAvailable()
            mViewOnOfficial.visibility = View.GONE

            return
        }

        // find the first available video recognized
        for (ml in mRun!!.run.videos!!.links!!) {
            if (mVideoView!!.loadVideo(ml))
                break
        }

        if (!mVideoView!!.hasLoadedVideo()) {
            Log.w(TAG, "Could not play a video for this run")
            // just record the fact that the video page was accessed
            mVideoView!!.setVideoFrameOther(mRun!!.run.videos!!.links!![0])
            mViewOnOfficial.visibility = View.GONE

            if (mModerationList.isNotEmpty())
                writeWatchToDb(0)
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        if (mSpinner.visibility == View.VISIBLE)
            return

        val act = requireActivity()

        if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {

            if(act is SpeedrunBrowserActivity)
                act.applyFullscreenMode(true)

            // if the screen's aspect ratio is < 16:9, re-orient the text view so it still centers properly
            val displ = requireActivity().windowManager.defaultDisplay
            val size = Point()
            displ.getSize(size)

            // (size.x.toFloat() / size.y < 16.0f / 9)
                mRootView.orientation = LinearLayout.HORIZONTAL

            // hide things
            mGameInfoPane.visibility = View.GONE
            mInfoHeader.visibility = View.GONE
            mRunFooterPane.visibility = View.GONE
            mViewOnOfficial.visibility = View.GONE

        } else {
            if(act is SpeedrunBrowserActivity)
                act.applyFullscreenMode(false)

            // layout should always be vertical in this case
            mRootView.orientation = LinearLayout.VERTICAL

            // show things
            mGameInfoPane.visibility = View.VISIBLE
            mRunFooterPane.visibility = View.VISIBLE
            mViewOnOfficial.visibility = View.VISIBLE
        }
    }

    private fun recordStartPlaybackTime() {

        if(mModerationList.isNotEmpty())
            return // dont record on moderation

        if (mVideoView!!.hasLoadedVideo() && mVideoView!!.seekTime != 0)
            writeWatchToDb(mVideoView!!.seekTime.toLong())
    }

    private fun writeWatchToDb(seekTime: Long) {

        Log.d(TAG, "Record seek time: $seekTime")

        mDisposables.add(mDB.watchHistoryDao().record(AppDatabase.WatchHistoryEntry(mRun!!.run.id, seekTime))
                .subscribeOn(Schedulers.io())
                .subscribe())
    }

    private fun setViewData() {

        mVideoView?.requestFocus()

        mGameName.text = mGame!!.resolvedName
        mReleaseDate.text = mGame!!.releaseDate

        // we have to join the string manually because it is java 7
        if (mGame!!.platforms != null) {
            val sb = StringBuilder()
            for (i in mGame!!.platforms!!.indices) {
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
        if (mLevel != null && mLevel!!.name != null)
            fullCategoryName.append(" \u2022 ").append(mLevel!!.name)

        if (mGame!!.shouldShowPlatformFilter() && mRun!!.run.system != null) {
            val chip = Chip(requireContext())

            for ((id, name) in mGame!!.platforms!!) {
                if (id == mRun!!.run.system!!.platform) {
                    chip.text = name
                    mVariableChips.addView(chip)
                    break
                }
            }
        }

        if (mGame!!.shouldShowRegionFilter() && mRun!!.run.system != null) {
            val chip = Chip(requireContext())

            for ((id, name) in mGame!!.regions!!) {
                if (id == mRun!!.run.system!!.region) {
                    chip.text = name
                    mVariableChips.addView(chip)
                    break
                }
            }
        }

        if (mCategory!!.variables != null) {
            for ((id, name, _, _, _, _, isSubcategory, values) in mCategory!!.variables!!) {
                if (mRun!!.run.values!!.containsKey(id) && !isSubcategory && values.containsKey(mRun!!.run.values!![id])) {
                    val chip = Chip(requireContext())
                    chip.text = StringBuilder(name).append(": ").append(values[mRun!!.run.values!![id]]!!.label)
                    mVariableChips.addView(chip)
                } else if (isSubcategory && values.containsKey(mRun!!.run.values!![id])) {
                    fullCategoryName.append(" \u2022 ").append(values[mRun!!.run.values!![id]]!!.label)
                }
            }
        }

        if (mGame!!.assets.coverLarge != null) {

            val coverConsumer = ImageViewPlacerConsumer(mCover)
            coverConsumer.roundedCorners = resources.getDimensionPixelSize(R.dimen.game_cover_rounded_corners).toFloat()

            mDisposables.add(
                    ImageLoader(requireContext()).loadImage(mGame!!.assets.coverLarge!!.uri)
                            .subscribe(coverConsumer))
        }

        mPlayerNames.removeAllViews()
        for (player in mRun!!.run.players!!) {

            val iv = ImageView(requireContext())
            player.applyCountryImage(iv)
            mPlayerNames.addView(iv)

            val tv = TextView(requireContext())
            tv.textSize = 16f
            player.applyTextView(tv)

            val padding = resources.getDimensionPixelSize(R.dimen.half_fab_margin)
            tv.setPadding(padding, padding, padding, padding)
            tv.setOnClickListener { viewPlayer(player) }

            mPlayerNames.addView(tv)
        }

        mCategoryName.text = fullCategoryName

        if(mRun?.place != null) {
            mRunPlaceImg.visibility = View.VISIBLE

            val loadImage = when(mRun!!.place) {
                1 -> mGame!!.assets.trophy1st?.uri
                2 -> mGame!!.assets.trophy2nd?.uri
                3 -> mGame!!.assets.trophy3rd?.uri
                4 -> mGame!!.assets.trophy4th?.uri
                else -> null
            }

            if(loadImage != null) {
                mDisposables.add(
                        ImageLoader(requireContext()).loadImage(loadImage)
                                .subscribe(ImageViewPlacerConsumer(mRunPlaceImg)))
            }

            mRunPlaceTxt.visibility = View.VISIBLE
            mRunPlaceTxt.text = mRun!!.placeName
        }
        else {
            mRunPlaceImg.visibility = View.GONE
            mRunPlaceTxt.visibility = View.GONE
        }
        mRunTime.text = mRun!!.run.times!!.time + " • " + mRun!!.run.date

        mRunSubmitted.text = getString(R.string.msg_submitted_at, mRun!!.run.submitted)


        mRunComment.text = mRun!!.run.comment

        //val emptyTv = TextView(requireContext())

        //emptyTv.setText(R.string.empty_no_splits)
    }

    private fun viewPlayer(player: User) {
        val intent = Intent(requireContext(), SpeedrunBrowserActivity::class.java)
        intent.putExtra(SpeedrunBrowserActivity.EXTRA_FRAGMENT_CLASSPATH, PlayerDetailFragment::class.java.canonicalName)
        intent.putExtra(PlayerDetailFragment.ARG_PLAYER_ID, player.id)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            intent.flags = Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT
        }

        startActivity(intent)

        findNavController().navigate(RunDetailFragmentDirections.actionRunDetailFragmentToPlayerDetailFragment(null, player.id))
    }

    private fun viewGame() {
        val intent = Intent(requireContext(), SpeedrunBrowserActivity::class.java)
        intent.putExtra(SpeedrunBrowserActivity.EXTRA_FRAGMENT_CLASSPATH, GameDetailFragment::class.java.canonicalName)
        intent.putExtra(GameDetailFragment.ARG_GAME_ID, mGame!!.id)
        intent.putExtra(GameDetailFragment.ARG_LEADERBOARD_ID, mCategory!!.id + (if (mLevel != null) "_" + mLevel!!.id else ""))
        intent.putExtra(GameDetailFragment.ARG_VARIABLE_SELECTIONS, Variable.VariableSelections(mRun?.run))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            intent.flags = Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT
        }

        startActivity(intent)

        findNavController().navigate(RunDetailFragmentDirections.actionRunDetailFragmentToGameDetailFragment(
                mGame!!.id,
                mCategory!!.id + (if (mLevel != null) "_" + mLevel!!.id else ""),
                Variable.VariableSelections(mRun?.run)
        ))
    }

    // show game rules as a Alert Dialog
    private fun viewRules() {
        var rulesText = mCategory!!.getRulesText(Variable.VariableSelections(mRun?.run))

        if (rulesText.isEmpty())
            rulesText = getString(R.string.msg_no_rules_content)

        val dialog = AlertDialog.Builder(requireContext(), AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                .setMessage(rulesText)
                .setNeutralButton(android.R.string.ok, null)
                .create()

        dialog.show()
    }

    private fun viewOnOfficial() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(mRun!!.run.videos!!.links!![0].uri.toString()))
        startActivity(intent)
    }

    private fun doShare() {
        if(mRun != null) {
            val intent = Intent(Intent.ACTION_SEND)
                    .setType("text/plain")
                    .putExtra(Intent.EXTRA_TEXT,
                            getString(R.string.msg_share_run, User.printPlayerNames(mRun!!.run.players!!),
                                    mRun!!.run.game!!.resolvedName,
                                    mRun!!.run.times?.time ?: "", mRun!!.run.weblink))

            startActivity(Intent.createChooser(intent, getString(R.string.msg_share_run_explain)))
        }
    }

    private fun disableModTools() {
        mModerationTools.findViewById<Button>(R.id.buttonApprove).isEnabled = false
        mModerationTools.findViewById<Button>(R.id.buttonSkip).isEnabled = false
        mModerationTools.findViewById<Button>(R.id.buttonReject).isEnabled = false
    }

    private fun enableModTools() {
        mModerationTools.findViewById<Button>(R.id.buttonApprove).isEnabled = true
        mModerationTools.findViewById<Button>(R.id.buttonSkip).isEnabled = true
        mModerationTools.findViewById<Button>(R.id.buttonReject).isEnabled = true
    }

    private fun doApprove() {

        disableModTools()

        mDisposables.add(SpeedrunAPI.make(requireContext()).setRunStatus(mSrcApiKey, mRun!!.run.id, SpeedrunAPI.APIRunStatus(status = RunStatus(status = "verified")))
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    doNextModeration()
                }, {
                    Log.e(TAG, "Failed talking to SRC API:", it)
                    Util.showErrorToast(requireContext(), "Could not set run: ${it.localizedMessage}")
                }))
    }

    private fun doReject() {
        // get the reason from the user

        val builder = AlertDialog.Builder(requireContext(), if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) android.R.style.Theme_DeviceDefault_Dialog_Alert else AlertDialog.THEME_DEVICE_DEFAULT_DARK)
        builder.setTitle(R.string.dialog_title_reject_reason)

        val input = EditText(requireContext())
        input.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_SHORT_MESSAGE
        builder.setView(input)

        builder.setPositiveButton(android.R.string.ok) { _,_ ->
            disableModTools()

            mDisposables.add(SpeedrunAPI.make(requireContext()).setRunStatus(mSrcApiKey, mRun!!.run.id, SpeedrunAPI.APIRunStatus(status = RunStatus(
                    status = "rejected",
                    reason = input.text.toString()
            )))
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe({
                        doNextModeration()
                    }, {
                        Log.e(TAG, "Failed talking to SRC API:", it)
                        Util.showErrorToast(requireContext(), "Could not set run: ${it.localizedMessage}")
                    }))
        }
        builder.setNegativeButton(android.R.string.cancel) { _,_ -> }

        builder.show()

        (requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).toggleSoftInput(InputMethodManager.SHOW_FORCED, InputMethodManager.HIDE_IMPLICIT_ONLY)
        input.requestFocus()
    }

    private fun doNextModeration(increment: Boolean = true) {
        if (increment)
            mModerationIndex++

        if(mModerationIndex >= mModerationList.size) {
            // finish this
            (requireActivity() as SpeedrunBrowserActivity).onBackPressed()
        }

        loadRun(mModerationList[mModerationIndex])
    }

    override fun onFullscreenToggleListener() {
        requireActivity().requestedOrientation = if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE)
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        else
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE

        // prevent screen rotation from being locked
        mDisposables.add(Observable.timer(SCREEN_LOCK_ROTATE_PERIOD.toLong(), TimeUnit.SECONDS)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe { requireActivity().requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED })
    }

    interface VideoSupplier {
        fun requestVideoView(): MultiVideoView
    }

    companion object {
        private val TAG = RunDetailFragment::class.java.simpleName

        const val ARG_GAME = "game"
        const val ARG_CATEGORY = "category"
        const val ARG_LEVEL = "level"
        const val ARG_RUN = "run"

        const val ARG_RUN_ID = "runId"

        const val ARG_MODERATION_LIST = "moderationList"

        /// how often to save the current watch position/time to the watch history db
        private const val BACKGROUND_SEEK_SAVE_START = 15
        private const val BACKGROUND_SEEK_SAVE_PERIOD = 30

        /// amount of time to hold the screen in a certain rotation after pressing the fullscreen button
        private const val SCREEN_LOCK_ROTATE_PERIOD = 5
    }
}
