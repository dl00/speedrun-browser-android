package danb.speedrunbrowser.utils

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

import java.util.ArrayList
import java.util.LinkedList
import java.util.concurrent.TimeUnit
import danb.speedrunbrowser.R
import danb.speedrunbrowser.api.SpeedrunMiddlewareAPI
import danb.speedrunbrowser.api.objects.Game
import danb.speedrunbrowser.api.objects.SearchResultItem
import danb.speedrunbrowser.api.objects.User
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.functions.Consumer
import io.reactivex.subjects.Subject

class AutoCompleteAdapter(private val ctx: Context, private val disposables: CompositeDisposable) : BaseAdapter(), Consumer<SpeedrunMiddlewareAPI.APISearchResponse> {

    var query: String = ""

    private var rawSearchData: SpeedrunMiddlewareAPI.APISearchData? = null

    private var searchResults: MutableList<SearchResultItem>? = null

    init {
        searchResults = ArrayList()
    }

    private fun mergeSearchResults(results: List<SearchResultItem>) {
        if (searchResults!!.isNotEmpty()) {
            val sr = searchResults!!.listIterator()
            var cur = sr.next()
            for (resultItem in results) {
                // select the longest matching substring
                val lcsp = LCSMatcher(query.toLowerCase(), resultItem.resolvedName.toLowerCase(), 3)

                var lcsg: LCSMatcher
                do {
                    lcsg = LCSMatcher(query.toLowerCase(), cur.resolvedName.toLowerCase(), 3)

                    if(sr.hasNext())
                        cur = sr.next()
                } while (lcsg.maxMatchLength >= lcsp.maxMatchLength && lcsp.maxMatchLength < resultItem.resolvedName.length - 1 && sr.hasNext())

                sr.previous()
                if(sr.hasPrevious())
                    sr.previous()
                sr.add(resultItem)
                sr.next()
            }
        }

        searchResults!!.addAll(results)
    }

    private fun recalculateSearchResults() {
        searchResults = LinkedList()

        searchResults!!.addAll(rawSearchData!!.games)
        mergeSearchResults(rawSearchData!!.players)
        mergeSearchResults(rawSearchData!!.game_groups)

        searchResults = ArrayList(searchResults!!)

        notifyDataSetChanged()
    }

    override fun getCount(): Int {
        return searchResults!!.size
    }

    override fun getItem(position: Int): Any {
        return searchResults!![position]
    }

    override fun getItemId(position: Int): Long {
        return 0
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        var convertView = convertView
        if (convertView == null)
            convertView = (ctx.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater).inflate(R.layout.content_named_autocomplete_item, parent, false)

        val item = searchResults!![position]

        val viewIcon = convertView!!.findViewById<ImageView>(R.id.imgItemIcon)
        val viewName = convertView.findViewById<LinearLayout>(R.id.txtItemName)
        val viewType = convertView.findViewById<TextView>(R.id.txtItemType)

        val iconUrl = item.iconUrl
        if (iconUrl != null) {
            disposables.add(ImageLoader(ctx).loadImage(iconUrl)
                    .subscribe(ImageViewPlacerConsumer(viewIcon)))
        }
        else
            viewIcon.setImageDrawable(null)

        viewName.removeAllViews()

        val tv = TextView(ctx)
        item.applyTextView(tv)
        viewName.addView(tv)

        viewType.text = item.type

        return convertView
    }

    fun setPublishSubject(subj: Subject<String>) {

        val obs = subj
                .distinctUntilChanged()
                .filter { it.isEmpty() || it.length >= SpeedrunMiddlewareAPI.MIN_AUTOCOMPLETE_LENGTH }

        disposables.add(obs.subscribe { s -> query = s })

        disposables.add(obs
                .throttleLast(DEBOUNCE_SEARCH_DELAY.toLong(), TimeUnit.MILLISECONDS)
                .switchMap<SpeedrunMiddlewareAPI.APISearchResponse> { s ->
                    if (s.length < SpeedrunMiddlewareAPI.MIN_AUTOCOMPLETE_LENGTH)
                        Observable.just(SpeedrunMiddlewareAPI.APISearchResponse())
                    else
                        SpeedrunMiddlewareAPI.make().autocomplete(s)
                }
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this))
    }

    override fun accept(apiSearchResponse: SpeedrunMiddlewareAPI.APISearchResponse) {
        // TODO: Handle error

        rawSearchData = apiSearchResponse.search
        recalculateSearchResults()
    }

    companion object {
        private val TAG = AutoCompleteAdapter::class.java.simpleName

        private val DEBOUNCE_SEARCH_DELAY = 200
    }
}
