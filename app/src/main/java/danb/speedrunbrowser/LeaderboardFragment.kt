package danb.speedrunbrowser

import android.animation.Animator
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView

import java.util.Objects
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView

import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup

import danb.speedrunbrowser.api.SpeedrunMiddlewareAPI
import danb.speedrunbrowser.api.objects.Category
import danb.speedrunbrowser.api.objects.Game
import danb.speedrunbrowser.api.objects.Leaderboard
import danb.speedrunbrowser.api.objects.LeaderboardRunEntry
import danb.speedrunbrowser.api.objects.Level
import danb.speedrunbrowser.api.objects.Variable
import danb.speedrunbrowser.holders.RunViewHolder
import danb.speedrunbrowser.stats.LeaderboardStatisticsFragment
import danb.speedrunbrowser.utils.ConnectionErrorConsumer
import danb.speedrunbrowser.utils.ItemType
import danb.speedrunbrowser.utils.Util
import danb.speedrunbrowser.views.ProgressSpinnerView
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable
import io.reactivex.functions.Consumer

/**
 * A fragment representing a single leaderboard, containing a list a records.
 */
class LeaderboardFragment : Fragment(), Consumer<SpeedrunMiddlewareAPI.APIResponse<Leaderboard>> {

    private var mDisposables: CompositeDisposable? = null

    var game: Game? = null
        private set
    private var mCategory: Category? = null
    private var mLevel: Level? = null

    var filter: Variable.VariableSelections? = null
        set(selections) {
            field = selections

            if (mCategory != null && filter != null)
                filter!!.setDefaults(mCategory!!.variables!!)

            notifyFilterChanged()
        }

    private var mProgressSpinner: ProgressSpinnerView? = null

    private var mContentLayout: LinearLayout? = null

    private var mLeaderboard: Leaderboard? = null
    private var mFilteredLeaderboardRuns: List<LeaderboardRunEntry>? = null

    private var mHsvSubcategories: HorizontalScrollView? = null

    private var mEmptyRuns: TextView? = null

    private var mRunsListAdapter: LeaderboardAdapter? = null

    val leaderboardId
        get() = if(mLevel != null) mCategory!!.id + "_" + mLevel!!.id else mCategory?.id

    /**
     * Mandatory empty constructor for the fragment manager to instantiate the
     * fragment (e.g. upon screen orientation changes).
     */
    init {
        game = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        mDisposables = CompositeDisposable()

        val args = Objects.requireNonNull<Bundle>(arguments)

        game = args.getSerializable(ARG_GAME) as Game
        mCategory = args.getSerializable(ARG_CATEGORY) as Category
        mLevel = args.getSerializable(ARG_LEVEL) as Level?

        if (filter != null)
            filter!!.setDefaults(mCategory!!.variables!!)

        assert(game != null)
        assert(mCategory != null)

        if (leaderboardId != null)
            loadLeaderboard(leaderboardId!!)
    }

    override fun onDestroy() {
        super.onDestroy()
        mDisposables!!.dispose()
    }

    fun loadLeaderboard(leaderboardId: String): Disposable {

        Log.d(TAG, "Loading leaderboard: $leaderboardId")

        return SpeedrunMiddlewareAPI.make(context!!).listLeaderboards(leaderboardId)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this, ConnectionErrorConsumer(context!!))
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        val rootView = inflater.inflate(R.layout.fragment_leaderboard, container, false)

        mContentLayout = rootView.findViewById(R.id.contentLayout)

        mHsvSubcategories = rootView.findViewById(R.id.hsvSubcategories)
        mHsvSubcategories!!.isHorizontalScrollBarEnabled = false
        updateSubcategorySelections()

        mProgressSpinner = rootView.findViewById(R.id.progress)

        val leaderboardList = rootView.findViewById<RecyclerView>(R.id.leaderboardList)
        mEmptyRuns = rootView.findViewById(R.id.emptyRuns)

        mRunsListAdapter = LeaderboardAdapter()
        leaderboardList.adapter = mRunsListAdapter

        val viewRulesButton = rootView.findViewById<Button>(R.id.viewLeaderboardInfoButton)

        viewRulesButton.setOnClickListener { viewInfo() }

        notifyFilterChanged()

        return rootView
    }

    private fun populateSubcategories() {

        mHsvSubcategories!!.removeAllViews()

        val layout = LinearLayout(context)
        layout.orientation = LinearLayout.HORIZONTAL
        layout.gravity = Gravity.CENTER_VERTICAL

        for ((id, _, _, _, scope, _, isSubcategory, values) in mCategory!!.variables!!) {
            if (!isSubcategory || scope!!.type == "single-level" && (mLevel == null || scope.level != mLevel!!.id))
                continue

            val cg = ChipGroup(Objects.requireNonNull<Context>(context))
            cg.tag = id

            cg.isSingleSelection = true

            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.leftMargin = resources.getDimensionPixelSize(R.dimen.half_fab_margin)
            lp.rightMargin = resources.getDimensionPixelSize(R.dimen.half_fab_margin)
            cg.layoutParams = lp

            for (vv in values.keys) {
                val cv = Chip(context!!, null, R.style.Widget_MaterialComponents_Chip_Choice)
                cv.text = values.getValue(vv).label
                cv.chipBackgroundColor = context!!.resources.getColorStateList(R.color.filter)
                cv.isCheckedIconVisible = false
                cv.tag = vv

                cv.isClickable = true
                cv.isCheckable = true

                cv.setOnClickListener {
                    filter!!.selectOnly(id, vv)
                    notifyFilterChanged()
                }

                cg.addView(cv)
            }

            layout.addView(cg)
        }

        mHsvSubcategories!!.addView(layout)

        if (layout.childCount == 0) {
            mHsvSubcategories!!.visibility = View.GONE
        } else {
            mHsvSubcategories!!.visibility = View.VISIBLE
            updateSubcategorySelections()
        }
    }

    private fun updateSubcategorySelections() {

        if (mHsvSubcategories == null || filter == null)
            return

        if (mHsvSubcategories!!.childCount == 0)
            populateSubcategories()

        val layout = mHsvSubcategories!!.getChildAt(0) as LinearLayout

        for (i in 0 until layout.childCount) {
            val cg = layout.getChildAt(i) as ChipGroup

            for (j in 0 until cg.childCount) {
                val v = cg.getChildAt(j)

                if (filter!!.isSelected(cg.tag as String, v.tag as String)) {
                    cg.check(v.id)
                    break
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()

        if (mLeaderboard != null) {
            mProgressSpinner!!.visibility = View.GONE
            mContentLayout!!.visibility = View.VISIBLE

            if (mFilteredLeaderboardRuns != null && mFilteredLeaderboardRuns!!.isEmpty()) {
                mEmptyRuns!!.visibility = View.VISIBLE
            }
        }
    }

    // show game rules as a Alert Dialog
    private fun viewInfo() {
        val intent = Intent(context!!, SpeedrunBrowserActivity::class.java)
        intent.putExtra(SpeedrunBrowserActivity.EXTRA_FRAGMENT_CLASSPATH, LeaderboardStatisticsFragment::class.java.canonicalName)
        intent.putExtra(LeaderboardStatisticsFragment.EXTRA_LEADERBOARD_ID, leaderboardId)
        startActivity(intent)
    }

    @Throws(Exception::class)
    override fun accept(leaderboardAPIResponse: SpeedrunMiddlewareAPI.APIResponse<Leaderboard>) {

        if(leaderboardAPIResponse.error != null) {
            Util.showErrorToast(context!!, getString(R.string.error_could_not_connect))
            return
        }

        val leaderboards = leaderboardAPIResponse.data

        if (leaderboards!!.isEmpty()) {
            // not found
            Util.showErrorToast(context!!, getString(R.string.error_missing_leaderboard, leaderboardId))
            return
        }

        mLeaderboard = leaderboards[0]

        if(mLeaderboard == null)
            mLeaderboard = Leaderboard.EMPTY_LEADERBOARD

        Log.d(TAG, "Downloaded " + mLeaderboard!!.runs!!.size + " runs!")

        if (mRunsListAdapter != null) {
            mRunsListAdapter!!.notifyDataSetChanged()
            notifyFilterChanged()

            if (context != null)
                animateLeaderboardIn()
        }
    }

    private fun animateLeaderboardIn() {
        val animTime = resources.getInteger(
                android.R.integer.config_shortAnimTime)

        val translationDistance = resources.getDimensionPixelSize(R.dimen.anim_slide_transition_distance)

        mProgressSpinner!!.animate()
                .alpha(0.0f)
                .translationY((-translationDistance).toFloat())
                .setDuration(animTime.toLong())
                .setListener(object : Animator.AnimatorListener {
                    override fun onAnimationStart(animator: Animator) {}

                    override fun onAnimationEnd(animator: Animator) {
                        mProgressSpinner!!.stop()
                    }

                    override fun onAnimationCancel(animator: Animator) {}

                    override fun onAnimationRepeat(animator: Animator) {}
                })

        mContentLayout!!.alpha = 0.0f
        mContentLayout!!.visibility = View.VISIBLE

        mContentLayout!!.translationY = translationDistance.toFloat()

        mContentLayout!!.animate()
                .alpha(1.0f)
                .setDuration(animTime.toLong())
                .translationY(0f)
                .setListener(null)
    }

    fun notifyFilterChanged() {
        updateSubcategorySelections()

        if (mLeaderboard != null) {
            mFilteredLeaderboardRuns = if (filter != null) {
                Log.d(TAG, "Filtering runs: $leaderboardId")

                filter!!.filterLeaderboardRuns(mLeaderboard!!, mCategory!!.variables!!)
            } else
                mLeaderboard!!.runs

            if (mRunsListAdapter != null) {
                mRunsListAdapter!!.notifyDataSetChanged()

                if (mFilteredLeaderboardRuns!!.isEmpty()) {
                    mEmptyRuns!!.visibility = View.VISIBLE
                } else {
                    mEmptyRuns!!.visibility = View.GONE
                }
            }
        }
    }

    inner class LeaderboardAdapter : RecyclerView.Adapter<RunViewHolder>() {

        private val inflater = context!!.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater

        private val mOnClickListener = View.OnClickListener { view ->
            val (run) = view.tag as LeaderboardRunEntry

            val intent = Intent(context, SpeedrunBrowserActivity::class.java)
            intent.putExtra(SpeedrunBrowserActivity.EXTRA_FRAGMENT_CLASSPATH, RunDetailFragment::class.java.canonicalName)
            intent.putExtra(RunDetailFragment.ARG_RUN_ID, run.id)
            startActivity(intent)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RunViewHolder {
            val v = inflater.inflate(R.layout.content_leaderboard_list, parent, false)
            return RunViewHolder(v)
        }

        override fun onBindViewHolder(holder: RunViewHolder, position: Int) {
            val run = mFilteredLeaderboardRuns!![position]

            holder.apply(context!!, mDisposables!!, game!!, run)

            holder.itemView.setOnClickListener(mOnClickListener)
            holder.itemView.tag = run
        }

        override fun getItemCount(): Int {
            return if (mFilteredLeaderboardRuns != null) mFilteredLeaderboardRuns!!.size else 0
        }
    }

    companion object {
        private val TAG = LeaderboardFragment::class.java.simpleName

        /**
         * The fragment argument representing the item ID that this fragment
         * represents.
         */
        val ARG_GAME = "game"
        val ARG_CATEGORY = "category"
        val ARG_LEVEL = "level"
    }

}
