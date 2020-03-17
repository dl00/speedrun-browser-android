package danb.speedrunbrowser

import android.app.ActivityOptions
import android.content.Context
import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import danb.speedrunbrowser.api.SpeedrunMiddlewareAPI
import danb.speedrunbrowser.api.objects.Game
import danb.speedrunbrowser.api.objects.LeaderboardRunEntry
import danb.speedrunbrowser.api.objects.User
import danb.speedrunbrowser.holders.ProgressSpinnerViewHolder
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable
import io.reactivex.functions.Function
import io.reactivex.schedulers.Schedulers

import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import androidx.core.content.ContextCompat
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import danb.speedrunbrowser.api.objects.GameGroup
import danb.speedrunbrowser.utils.ItemType

import java.util.ArrayList


open class ItemListFragment : Fragment() {

    var itemType: ItemType? = null
        private set

    private var mDisposables = CompositeDisposable()

    private var mListener: OnFragmentInteractionListener? = null

    private var mRootView: View? = null

    private var mModeHsv: HorizontalScrollView? = null

    private var mAdapter: ItemListAdapter? = null

    private lateinit var mSearchItemsView: RecyclerView

    private lateinit var mEmptyView: View

    private var mItemModes = mutableListOf<ItemListMode>()
    private var mSelectedMode: String? = null

    private val currentItemSource
    get() = mItemModes.find { it.id == mSelectedMode }?.itemSource

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val args = arguments
        if (args != null) {
            itemType = args.getSerializable(ARG_ITEM_TYPE) as ItemType
        }

    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {

        if(mRootView != null) {
            (mRootView!!.parent as? ViewGroup?)?.removeView(mRootView)
            return mRootView
        }

        // Inflate the layout for this fragment
        mRootView = inflater.inflate(itemType!!.layout, container, false)

        mSearchItemsView = mRootView!!.findViewById(R.id.listSearchItems)
        mEmptyView = mRootView!!.findViewById(R.id.empty)

        mModeHsv = mRootView!!.findViewById(R.id.hsvListModes)

        mAdapter = ItemListAdapter(context!!, mSearchItemsView, View.OnClickListener { v ->
            if (mListener != null) {
                var id = ""
                when (itemType) {
                    ItemType.GAMES -> id = (v.tag as Game).id
                    ItemType.PLAYERS -> id = (v.tag as User).id
                    ItemType.RUNS -> id = (v.tag as LeaderboardRunEntry).run.id
                    ItemType.GAME_GROUPS -> id = (v.tag as GameGroup).id
                }

                mListener!!.onItemSelected(itemType, id, this@ItemListFragment,
                        null)
            }
        })
        mSearchItemsView.adapter = mAdapter
        reload()

        return mRootView
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (parentFragment is OnFragmentInteractionListener) {
            mListener = parentFragment as OnFragmentInteractionListener
        } else if (context is OnFragmentInteractionListener) {
            mListener = context
        } else {
            throw RuntimeException("$context must implement OnFragmentInteractionListener")
        }
    }

    override fun onDetach() {
        super.onDetach()
        mListener = null
    }

    override fun onDestroy() {
        super.onDestroy()
        mDisposables.dispose()
    }

    fun addListMode(mode: ItemListMode) {
        mItemModes.add(mode)
        if (mItemModes.size == 1)
            mSelectedMode = mode.id
    }

    private fun addModeChips() {

        val hsv = mModeHsv ?: return

        val cg = if (hsv.childCount == 0) {
            val ncg = ChipGroup(context)
            ncg.tag = id
            ncg.isSingleSelection = true

            hsv.addView(ncg)

            ncg
        }
        else hsv.getChildAt(0) as ChipGroup

        for (mode in mItemModes.subList(cg.childCount, mItemModes.size)) {
            val cv = Chip(context!!, null, R.style.AppTheme_Chip_Choice)
            cv.setTextAppearanceResource(R.style.AppTheme_TextAppearance)
            cv.text = mode.label
            cv.chipBackgroundColor = ContextCompat.getColorStateList(context!!, R.color.filter)
            cv.isCheckedIconVisible = false
            cv.tag = mode

            cv.isClickable = true
            cv.isCheckable = true

            cv.setOnClickListener {
                if (!cv.isChecked)
                    cv.isChecked = true
                else {
                    mSelectedMode = mode.id
                    reload()
                }
            }

            cg.addView(cv)

            if(cg.childCount == 1)
                cv.isChecked = true
        }

        hsv.visibility = if (cg.childCount > 1) View.VISIBLE else View.GONE
    }

    fun reload() {
        addModeChips()

        if (mAdapter != null && mItemModes.isNotEmpty())
            mAdapter!!.loadListTop()
    }

    inner class ItemListAdapter(ctx: Context, rv: RecyclerView, private val onClickListener: View.OnClickListener) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        private val inflater: LayoutInflater

        private var currentLoading: Disposable? = null
        private var isAtEndOfList: Boolean = false

        private var items: MutableList<Any>? = null

        init {
            this.items = ArrayList()

            inflater = ctx.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater

            rv.addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    val lm = rv.layoutManager
                    var visibleItemPosition = 0
                    if (lm is LinearLayoutManager)
                        visibleItemPosition = lm.findLastVisibleItemPosition()

                    if (visibleItemPosition == items!!.size - 1) {
                        val handler = Handler(Looper.getMainLooper())
                        handler.post { loadListMore() }
                    }
                }
            })
        }

        override fun getItemViewType(position: Int): Int {
            return if (items == null || items!!.size > position)
                0
            else
                1
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            return if (viewType == 0)
                itemType!!.newViewHolder(context, parent)
            else
                ProgressSpinnerViewHolder(context!!)
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            if (getItemViewType(position) == 0) {
                itemType!!.applyToViewHolder(context, mDisposables, holder, items!![position])
                holder.itemView.setOnClickListener(onClickListener)
                holder.itemView.tag = items!![position]
            }
        }

        override fun getItemCount(): Int {
            var count = if (items != null) items!!.size else 0

            if (currentLoading != null)
                count++

            return count
        }

        fun loadListTop() {

            if (currentLoading != null)
                currentLoading!!.dispose()

            items = ArrayList(0)
            notifyDataSetChanged()

            currentLoading = currentItemSource!!.list(0)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe({ (data, _, more) ->
                        currentLoading = null

                        items = data!!.filterNotNull().toMutableList()
                        isAtEndOfList = if (more != null) !more.hasMore else items!!.isEmpty()

                        if (isAtEndOfList)
                            mEmptyView.visibility = View.VISIBLE
                        else
                            mEmptyView.visibility = View.GONE

                        notifyDataSetChanged()
                    }, {
                        // probably went past the end of the list if we got to this point.
                        // TODO: handle the error more

                        Log.e(TAG, "Cannot load list: ", it)

                        items = ArrayList(0)

                        currentLoading = null
                        isAtEndOfList = true
                        notifyDataSetChanged()
                    })
        }

        fun loadListMore() {

            if (isAtEndOfList || currentLoading != null)
                return

            currentLoading = currentItemSource!!.list(items!!.size)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe({ (data, _, more) ->
                        currentLoading = null
                        if (data!!.isEmpty()) {
                            isAtEndOfList = true
                            notifyItemRemoved(items!!.size)
                        } else {
                            val prevSize = items!!.size
                            items!!.addAll(data.filterNotNull())
                            notifyItemChanged(prevSize)
                            notifyItemRangeInserted(prevSize + 1, data.size - 1)

                            if (more != null)
                                isAtEndOfList = !more.hasMore
                        }
                    }, {
                        // probably went past the end of the list if we got to this point.
                        // TODO: handle the error more properly

                        currentLoading = null
                        isAtEndOfList = true
                        notifyItemRemoved(items!!.size)
                    })

            notifyItemChanged(items!!.size)
        }
    }

    interface ItemSource {
        fun list(offset: Int): Observable<SpeedrunMiddlewareAPI.APIResponse<Any?>>
    }

    interface OnFragmentInteractionListener {
        // TODO: Update argument type and name
        fun onItemSelected(itemType: ItemType?, itemId: String, fragment: Fragment, options: ActivityOptions?)
    }

    class GenericMapper<T> : Function<SpeedrunMiddlewareAPI.APIResponse<T>, SpeedrunMiddlewareAPI.APIResponse<Any?>> {

        @Throws(Exception::class)
        override fun apply(res: SpeedrunMiddlewareAPI.APIResponse<T>): SpeedrunMiddlewareAPI.APIResponse<Any?> {
            val arrl = ArrayList<Any?>()

            if(res.data != null)
                arrl.addAll(res.data)

            return SpeedrunMiddlewareAPI.APIResponse(
                    arrl,
                    res.error
            )
        }
    }

    companion object {
        // TODO: Rename parameter arguments, choose names that match
        // the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
        const val ARG_ITEM_TYPE = "item_type"

        val TAG = ItemListFragment::javaClass.name

        data class ItemListMode(
                val itemSource: ItemSource,
                val id: String = "",
                val label: String = ""
        )
    }
}
