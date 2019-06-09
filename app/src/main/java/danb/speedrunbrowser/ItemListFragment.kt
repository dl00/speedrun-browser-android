package danb.speedrunbrowser

import android.app.Activity
import android.app.ActivityOptions
import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import danb.speedrunbrowser.api.SpeedrunMiddlewareAPI
import danb.speedrunbrowser.api.objects.Game
import danb.speedrunbrowser.api.objects.LeaderboardRunEntry
import danb.speedrunbrowser.api.objects.User
import danb.speedrunbrowser.holders.GameCoverViewHolder
import danb.speedrunbrowser.holders.PlayerViewHolder
import danb.speedrunbrowser.holders.ProgressSpinnerViewHolder
import danb.speedrunbrowser.holders.WatchRunViewHolder
import danb.speedrunbrowser.utils.ConnectionErrorConsumer
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable
import io.reactivex.functions.Consumer
import io.reactivex.functions.Function
import io.reactivex.schedulers.Schedulers

import android.os.Handler
import android.os.Looper
import android.os.Message
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup

import java.io.Serializable
import java.util.ArrayList


class ItemListFragment : Fragment() {

    var itemType: ItemType? = null
        private set

    private var mDisposables = CompositeDisposable()

    private var mListener: OnFragmentInteractionListener? = null

    private var mAdapter: ItemListAdapter? = null

    private lateinit var mSearchItemsView: RecyclerView

    private lateinit var mEmptyView: View

    private var mItemSource: ItemSource? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val args = arguments
        if (args != null) {
            itemType = args.getSerializable(ARG_ITEM_TYPE) as ItemType
        }

    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        // Inflate the layout for this fragment
        val v = inflater.inflate(itemType!!.layout, container, false)

        mSearchItemsView = v.findViewById(R.id.listSearchItems)
        mEmptyView = v.findViewById(R.id.empty)

        mAdapter = ItemListAdapter(context!!, mSearchItemsView!!, View.OnClickListener { v ->
            if (mListener != null) {
                var id = ""
                when (itemType) {
                    ItemType.GAMES -> id = (v.tag as Game).id
                    ItemType.PLAYERS -> id = (v.tag as User).id
                    ItemType.RUNS -> id = (v.tag as LeaderboardRunEntry).run.id
                }

                mListener!!.onItemSelected(itemType, id, this@ItemListFragment,
                        itemType!!.makeSceneTransition(activity, v))
            }
        })
        mSearchItemsView!!.adapter = mAdapter
        if (mItemSource != null)
            mAdapter!!.loadListTop()

        return v
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is OnFragmentInteractionListener) {
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
        mDisposables!!.dispose()
    }

    fun setItemsSource(source: ItemSource) {
        mItemSource = source
        reload()
    }

    fun reload() {
        if (mAdapter != null)
            mAdapter!!.loadListTop()
    }

    inner class ItemListAdapter(private val ctx: Context, rv: RecyclerView, private val onClickListener: View.OnClickListener) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

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

            println("My position is" + items)

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

            currentLoading = mItemSource!!.list(0)
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

                        items = ArrayList(0)

                        currentLoading = null
                        isAtEndOfList = true
                        notifyDataSetChanged()
                    })
            notifyDataSetChanged()
        }

        fun loadListMore() {

            if (isAtEndOfList || currentLoading != null)
                return

            currentLoading = mItemSource!!.list(items!!.size)
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

    enum class ItemType constructor(val layout: Int) : Serializable {
        GAMES(R.layout.fragment_game_list),
        PLAYERS(R.layout.fragment_player_list),
        RUNS(R.layout.fragment_run_list);

        fun newViewHolder(ctx: Context?, parent: ViewGroup): RecyclerView.ViewHolder {
            return when (this) {
                GAMES -> GameCoverViewHolder((ctx!!.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater).inflate(R.layout.content_game_cover, parent, false))
                PLAYERS -> PlayerViewHolder((ctx!!.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater).inflate(R.layout.content_player_list, parent, false))
                RUNS -> WatchRunViewHolder((ctx!!.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater).inflate(R.layout.content_watch_list, parent, false))
            }
        }

        fun applyToViewHolder(ctx: Context?, disposables: CompositeDisposable?, holder: RecyclerView.ViewHolder, toApply: Any) {
            when (this) {
                GAMES -> (holder as GameCoverViewHolder).apply(ctx!!, disposables!!, toApply as Game)
                PLAYERS -> (holder as PlayerViewHolder).apply(ctx!!, disposables!!, toApply as User, false)
                RUNS -> (holder as WatchRunViewHolder).apply(ctx!!, disposables!!, (toApply as LeaderboardRunEntry).run.game!!, toApply)
            }
        }

        fun makeSceneTransition(activity: Activity?, v: View): ActivityOptions? {
            return null
        }

        override fun toString(): String {
            return name
        }
    }

    class GenericMapper<T> : Function<SpeedrunMiddlewareAPI.APIResponse<T>, SpeedrunMiddlewareAPI.APIResponse<Any?>> {

        @Throws(Exception::class)
        override fun apply(res: SpeedrunMiddlewareAPI.APIResponse<T>): SpeedrunMiddlewareAPI.APIResponse<Any?> {
            return SpeedrunMiddlewareAPI.APIResponse(
                    ArrayList(res.data),
                    res.error
            )
        }
    }

    companion object {
        // TODO: Rename parameter arguments, choose names that match
        // the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
        const val ARG_ITEM_TYPE = "item_type"
    }
}// Required empty public constructor
