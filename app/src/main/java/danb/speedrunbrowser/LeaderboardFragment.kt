package danb.speedrunbrowser

import android.animation.Animator
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.content.ContextCompat

import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup

import danb.speedrunbrowser.api.SpeedrunMiddlewareAPI
import danb.speedrunbrowser.api.objects.Category
import danb.speedrunbrowser.api.objects.Game
import danb.speedrunbrowser.api.objects.LeaderboardRunEntry
import danb.speedrunbrowser.api.objects.Level
import danb.speedrunbrowser.api.objects.Variable
import danb.speedrunbrowser.holders.RunViewHolder
import danb.speedrunbrowser.utils.ConnectionErrorConsumer
import danb.speedrunbrowser.utils.Util
import danb.speedrunbrowser.views.ProgressSpinnerView
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable
import io.reactivex.functions.Consumer
import io.reactivex.schedulers.Schedulers

/**
 * A fragment representing a single leaderboard, containing a list a records.
 */
class LeaderboardFragment : Fragment(), Consumer<SpeedrunMiddlewareAPI.APIResponse<LeaderboardRunEntry>> {

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

    private var mRuns: ArrayList<LeaderboardRunEntry> = arrayListOf()
    private var mRunsLength: Int? = null
    private var mFilteredLeaderboardRuns: List<LeaderboardRunEntry>? = null

    private var mHsvSubcategories: HorizontalScrollView? = null

    private var mLeaderboardList: RecyclerView? = null

    private var mEmptyRuns: TextView? = null

    private var mRunsListAdapter: LeaderboardAdapter? = null

    var doFocus = false
    set(value) {
        field = value

        if (value && mLeaderboardList != null) {
            mLeaderboardList?.requestFocus()
            //field = false
        }
    }


    private val lastLoadedRunId: String?
    get() {
        return mRuns.lastOrNull()?.run?.id
    }

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

        var p = parentFragment
        while (p != null) {
            if (p is LeaderboardInteracter)
                p.addLeaderboard(this)

            p = p.parentFragment
        }

        val args = requireArguments()

        game = args.getSerializable(ARG_GAME) as Game
        mCategory = args.getSerializable(ARG_CATEGORY) as Category
        mLevel = args.getSerializable(ARG_LEVEL) as Level?

        if (filter != null)
            filter!!.setDefaults(mCategory!!.variables!!)

        if (BuildConfig.DEBUG && (mCategory == null || game == null)) {
            error("game or category is not defined")
        }

        /*if (leaderboardId != null)
            loadLeaderboard(leaderboardId!!)*/
    }

    override fun onDestroy() {
        super.onDestroy()
        mDisposables!!.dispose()
    }

    fun loadLeaderboard(leaderboardId: String): Disposable {

        Log.d(TAG, "Loading leaderboard: $leaderboardId ($lastLoadedRunId)")

        if(mRuns.isEmpty()) {
            mProgressSpinner?.visibility = View.VISIBLE
            mProgressSpinner?.start()
        }

        val filters = mCategory!!.variables?.mapNotNull {
            if (!it.isSubcategory || it.scope!!.type == "single-level" && (mLevel == null || it.scope.level != mLevel!!.id))
                null
            else
                "var_" + it.id to filter!!.getSelections(it.id)!!.first()
        }?.toMap()

        return SpeedrunMiddlewareAPI.make(requireContext()).listLeaderboardRuns(leaderboardId, lastLoadedRunId ?: "", filters ?: mapOf())
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this, ConnectionErrorConsumer(requireContext()))
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        val rootView = inflater.inflate(R.layout.fragment_leaderboard, container, false)

        mContentLayout = rootView.findViewById(R.id.contentLayout)

        mHsvSubcategories = rootView.findViewById(R.id.hsvSubcategories)
        mHsvSubcategories!!.isHorizontalScrollBarEnabled = false
        updateSubcategorySelections()

        mProgressSpinner = rootView.findViewById(R.id.progress)

        mLeaderboardList = rootView.findViewById<RecyclerView>(R.id.leaderboardList)
        mEmptyRuns = rootView.findViewById(R.id.emptyRuns)

        mRunsListAdapter = LeaderboardAdapter()
        mLeaderboardList!!.adapter = mRunsListAdapter

        mLeaderboardList!!.addOnLayoutChangeListener { v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
            if (doFocus) {
                mLeaderboardList!!.requestFocus()
                doFocus = false
            }
        }

        val viewRulesButton = rootView.findViewById<Button>(R.id.viewLeaderboardInfoButton)

        viewRulesButton.setOnClickListener { viewInfo() }

        if (mRuns.isNotEmpty()) {
            mProgressSpinner!!.visibility = View.GONE
            mContentLayout!!.visibility = View.VISIBLE

            if (mRuns.isEmpty()) {
                mEmptyRuns!!.visibility = View.VISIBLE
            }
        }

        notifyFilterChanged()

        return rootView
    }

    private fun populateSubcategories() {

        mHsvSubcategories!!.removeAllViews()

        val layout = LinearLayout(context)
        layout.orientation = LinearLayout.HORIZONTAL
        layout.gravity = Gravity.CENTER_VERTICAL
        //layout.focusable = View.NOT_FOCUSABLE

        for ((id, _, _, _, scope, _, isSubcategory, values) in mCategory!!.variables!!) {
            if (!isSubcategory || scope!!.type == "single-level" && (mLevel == null || scope.level != mLevel!!.id))
                continue

            val cg = ChipGroup(requireContext())
            cg.tag = id
            cg.isFocusable = false

            //cg.add

            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.leftMargin = resources.getDimensionPixelSize(R.dimen.half_fab_margin)
            lp.rightMargin = resources.getDimensionPixelSize(R.dimen.half_fab_margin)
            cg.layoutParams = lp

            for (vv in values.keys) {
                val cv = Chip(requireContext(), null, R.style.Widget_MaterialComponents_Chip_Choice)
                cv.text = values.getValue(vv).label
                cv.chipBackgroundColor = ContextCompat.getColorStateList(requireContext(), R.color.filter)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                    cv.foreground = resources.getDrawable(R.drawable.clickable_item)

                cv.isCheckedIconVisible = false
                cv.tag = vv

                cv.isClickable = true
                //cv.focusable = View.NOT_FOCUSABLE
                cv.isCheckable = true

                cv.setOnClickListener {
                    filter!!.selectOnly(id, vv)
                    updateSubcategorySelections()
                    notifyFilterChanged()
                }

                cv.setOnFocusChangeListener { v, hasFocus ->

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
                    cg.clearCheck()
                    cg.check(v.id)
                    break
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()

        if (mRuns.isNotEmpty()) {
            mProgressSpinner!!.visibility = View.GONE
            mContentLayout!!.visibility = View.VISIBLE

            if (mRuns.isEmpty()) {
                mEmptyRuns!!.visibility = View.VISIBLE
            }
        }
    }

    // show game rules as a Alert Dialog
    fun viewInfo() {
        if(leaderboardId != null) {
            findNavController().navigate(GameDetailFragmentDirections.actionGameDetailFragmentToLeaderboardStatisticsFragment(leaderboardId!!))
        }
    }

    @Throws(Exception::class)
    override fun accept(leaderboardAPIResponse: SpeedrunMiddlewareAPI.APIResponse<LeaderboardRunEntry>) {

        if(leaderboardAPIResponse.error != null) {
            Util.showErrorToast(requireContext(), getString(R.string.error_could_not_connect))
            return
        }

        val runs = leaderboardAPIResponse.data

        /*if (runs!!.isEmpty()) {
            if(mRuns.si)
            // not found
            Util.showErrorToast(requireContext(), getString(R.string.error_missing_leaderboard, leaderboardId))
            return
        }*/

        if(runs!!.isEmpty())
            mRunsLength = mRuns.size

        mRuns.addAll(runs.filterNotNull())
        generateFilteredRuns()

        Log.d(TAG, "Downloaded " + mRuns.size + " runs!")

        if (mRunsListAdapter != null) {
            mRunsListAdapter!!.notifyDataSetChanged()
            //notifyFilterChanged()

            if (mRuns.size > 0 && mRuns.size == runs!!.size && context != null)
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
                        mProgressSpinner!!.translationY = 0.0f
                        mProgressSpinner!!.alpha = 1.0f;
                        mProgressSpinner!!.visibility = View.GONE
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

    fun notifyFilterChanged(selections: Variable.VariableSelections? = null) {
        if (selections != null && selections != filter) {
            filter = selections
            return
        }

        updateSubcategorySelections()

        mRuns.clear()
        mRunsLength = null
        mRunsListAdapter?.notifyDataSetChanged()

        if (leaderboardId != null)
            loadLeaderboard(leaderboardId!!)

        /*if (mRuns.isNotEmpty()) {
            mFilteredLeaderboardRuns = if (filter != null) {
                Log.d(TAG, "Filtering runs: $leaderboardId")

                val activeVars = if (mLevel != null) { mLevel!!.variables!!.intersect(mCategory!!.variables!!) } else { mCategory!!.variables!! }

                filter!!.filterLeaderboardRuns(mRuns, activeVars)
            } else
                mRuns
            if (mRunsListAdapter != null) {
                mRunsListAdapter!!.notifyDataSetChanged()

                if (mFilteredLeaderboardRuns!!.isEmpty()) {
                    mEmptyRuns!!.visibility = View.VISIBLE
                } else {
                    mEmptyRuns!!.visibility = View.GONE
                }
            }
        }*/
    }

    fun generateFilteredRuns() {
        mFilteredLeaderboardRuns = if (filter != null) {
            Log.d(TAG, "Filtering runs: $leaderboardId")

            val activeVars = if (mLevel != null) { mLevel!!.variables!!.intersect(mCategory!!.variables!!) } else { mCategory!!.variables!! }

            filter!!.filterLeaderboardRuns(mRuns, activeVars)
        } else
            mRuns
        if (mRunsListAdapter != null) {
            mRunsListAdapter!!.notifyDataSetChanged()

            if (mFilteredLeaderboardRuns!!.isEmpty()) {
                mEmptyRuns!!.visibility = View.VISIBLE
            } else {
                mEmptyRuns!!.visibility = View.GONE
            }
        }
    }

    interface LeaderboardInteracter {
        fun addLeaderboard(frag: LeaderboardFragment)
    }

    inner class LeaderboardAdapter : RecyclerView.Adapter<RunViewHolder>() {

        private val inflater = context!!.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater

        private val mOnClickListener = View.OnClickListener { view ->
            val (run) = view.tag as LeaderboardRunEntry

            try {
                findNavController().navigate(GameDetailFragmentDirections.actionGameDetailFragmentToRunDetailFragment(null, null, null, null, run.id))
            } catch(e: Exception) {
                findNavController().navigate(GameListFragmentDirections.actionGameListFragmentToRunDetailFragment(null, null, null, null, run.id))
            }
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

            if(position == mFilteredLeaderboardRuns!!.size - 1 && mRunsLength == null)
                loadLeaderboard(leaderboardId!!)
        }

        override fun getItemCount(): Int {
            return mFilteredLeaderboardRuns?.size ?: 0
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
