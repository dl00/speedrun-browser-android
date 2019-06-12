package danb.speedrunbrowser

import android.app.AlertDialog
import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ScrollView
import android.widget.TextView

import java.util.Locale

import danb.speedrunbrowser.api.SpeedrunMiddlewareAPI
import danb.speedrunbrowser.api.objects.Genre
import danb.speedrunbrowser.utils.ConnectionErrorConsumer
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.functions.Consumer
import io.reactivex.schedulers.Schedulers

class SelectGenreDialog(ctx: Context, private val mDisposables: CompositeDisposable) : AlertDialog(ctx, THEME_DEVICE_DEFAULT_DARK), View.OnClickListener, AdapterView.OnItemClickListener {

    private lateinit var mGenreAdapter: GenreArrayAdapter

    private lateinit var mGenreListScroller: ScrollView

    var selectedGenre: Genre? = null
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(context)
        layout.orientation = LinearLayout.VERTICAL

        val titleText = TextView(context)


        layout.addView(titleText)

        val searchView = EditText(context)
        searchView.setHint(R.string.enter_genre_filter)
        searchView.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                if (s.length >= SpeedrunMiddlewareAPI.MIN_AUTOCOMPLETE_LENGTH)
                    triggerSearchGenres(s.toString())
                else
                    triggerSearchGenres("")
            }

            override fun afterTextChanged(s: Editable) {}
        })
        layout.addView(searchView)

        mGenreListScroller = ScrollView(context)
        var params = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, context.resources.getDimensionPixelSize(R.dimen.dialog_list_min_height))
        params.weight = 1f
        mGenreListScroller.layoutParams = params
        mGenreListScroller.isFillViewport = true

        val lv = ListView(context)
        params = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0)
        params.weight = 1f
        lv.layoutParams = params
        mGenreAdapter = GenreArrayAdapter(context)
        lv.adapter = mGenreAdapter

        lv.onItemClickListener = this

        //mGenreListScroller.addView(lv);

        //layout.addView(mGenreListScroller);
        layout.addView(lv)

        val cancelButton = Button(context)
        cancelButton.setText(android.R.string.cancel)
        val cancelButtonLP = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT)
        cancelButtonLP.weight = 0f
        cancelButton.layoutParams = cancelButtonLP

        cancelButton.setOnClickListener(this)

        layout.addView(cancelButton)

        triggerSearchGenres("")

        window!!.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM)
        window!!.setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        val inputMananger = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        inputMananger.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0)

        setContentView(layout)
    }

    private fun triggerSearchGenres(query: String) {
        mDisposables.add(SpeedrunMiddlewareAPI.make().listGenres(query)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(Consumer { genreAPIResponse ->
                    mGenreAdapter.clear()
                    mGenreAdapter.addAll(genreAPIResponse.data)
                    mGenreAdapter.notifyDataSetChanged()
                    //((ListView)mGenreListScroller.getChildAt(0)).scrollTo(0, 0);
                }, ConnectionErrorConsumer(context)))
    }

    override fun onClick(v: View) {
        dismiss()
    }

    override fun onItemClick(parent: AdapterView<*>, view: View, position: Int, id: Long) {
        selectedGenre = mGenreAdapter.getItem(position)

        // TODO: Save this genre somewhere so that the result can be picked up by the listener

        dismiss()
    }

    private inner class GenreArrayAdapter(context: Context) : ArrayAdapter<Genre>(context, R.layout.content_named_autocomplete_item) {

        override fun getItem(position: Int): Genre? {
            return if (position == 0) null else super.getItem(position - 1)

        }

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: layoutInflater.inflate(R.layout.content_named_autocomplete_item, parent, false)

            val unusedImage = view.findViewById<View>(R.id.imgItemIcon)
            unusedImage.visibility = View.GONE

            val titleLayout = view.findViewById<LinearLayout>(R.id.txtItemName)
            val titleTv = TextView(context)
            val countTv = view.findViewById<TextView>(R.id.txtItemType)

            val data = getItem(position)

            if (data != null) {
                titleTv.text = data.name
                countTv.text = String.format(Locale.US, "%d", data.game_count)
            } else {
                titleTv.setText(R.string.label_all_genres)
                countTv.text = ""
            }

            titleLayout.removeAllViews()
            titleLayout.addView(titleTv)

            view.minimumHeight = context.resources.getDimensionPixelSize(R.dimen.genre_list_item_height)

            return view
        }
    }
}
