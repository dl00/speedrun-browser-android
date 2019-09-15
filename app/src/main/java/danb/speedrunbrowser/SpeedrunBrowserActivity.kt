package danb.speedrunbrowser

import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.*

import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import danb.speedrunbrowser.api.SpeedrunMiddlewareAPI
import danb.speedrunbrowser.api.objects.Game
import danb.speedrunbrowser.api.objects.User
import danb.speedrunbrowser.api.objects.WhatIsEntry
import danb.speedrunbrowser.utils.*
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable
import io.reactivex.functions.Consumer
import io.reactivex.schedulers.Schedulers
import io.reactivex.subjects.PublishSubject
import java.util.*


class SpeedrunBrowserActivity : AppCompatActivity(), TextWatcher, AdapterView.OnItemClickListener, Consumer<SpeedrunMiddlewareAPI.APIResponse<WhatIsEntry>> {

    private var whatIsQuery: Disposable? = null

    private lateinit var mGameFilterSearchSubject: PublishSubject<String>

    private lateinit var mGameFilter: EditText

    private lateinit var mAutoCompleteResults: ListView

    private val mDisposables = CompositeDisposable()

    private val mBackStack = Stack<Intent>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_speedrun_browser)

        mGameFilter = findViewById(R.id.editGameFilter)
        mAutoCompleteResults = findViewById(R.id.listAutoCompleteResults)

        mGameFilter.addTextChangedListener(this)
        mGameFilterSearchSubject = PublishSubject.create()

        val autoCompleteAdapter = AutoCompleteAdapter(this, mDisposables)
        autoCompleteAdapter.setPublishSubject(mGameFilterSearchSubject)
        mAutoCompleteResults.adapter = autoCompleteAdapter
        mAutoCompleteResults.onItemClickListener = this

        // Show the Up button in the action bar.
        val actionBar = supportActionBar
        actionBar?.setDisplayHomeAsUpEnabled(true)

        onNewIntent(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)

        if(this.intent != intent && this.intent != null)
            mBackStack.push(this.intent)

        setIntent(intent)

        if(intent == null)
            return

        showIntent(intent)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.remove("android:support:fragments")
    }

    override fun onDestroy() {
        super.onDestroy()

        if (whatIsQuery != null)
            whatIsQuery!!.dispose()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val id = item.itemId
        if (id == android.R.id.home) {
            // This ID represents the Home or Up button. In the case of this
            // activity, the Up button is shown. For
            // more details, see the Navigation pattern on Android Design:
            //
            // http://developer.android.com/design/patterns/navigation.html#up-vs-back
            //
            navigateUpTo(Intent(this, GameListFragment::class.java))
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onBackPressed() {
        if (mGameFilter.text.isEmpty()) {
            if(mBackStack.empty())
                finish()
            else
                showIntent(mBackStack.pop())
        }
        else
            mGameFilter.setText("")
    }

    private fun showIntent(intent: Intent) {

        applyFullscreenMode(false)

        // Create the detail fragment and add it to the activity
        // using a fragment transaction.
        val args = intent.extras

        val type = args?.getSerializable(EXTRA_ITEM_TYPE) as ItemType?

        val frag: Fragment?

        when {
            type != null -> frag = when (type) {
                ItemType.GAMES -> GameDetailFragment()
                ItemType.PLAYERS -> PlayerDetailFragment()
                ItemType.RUNS -> RunDetailFragment()
            }
            intent.data != null -> {
                val segs = intent.data!!.pathSegments

                if (segs.isNotEmpty()) {
                    val id = segs[segs.size - 1]

                    Log.d(TAG, "Decoded game or player ID: " + id + ", from URL: " + intent.data)

                    // use the whatis api to resolve the type of object
                    whatIsQuery = SpeedrunMiddlewareAPI.make().whatAreThese(id)
                            .subscribeOn(Schedulers.io())
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe(this, ConnectionErrorConsumer(this))
                    return
                }
                else {
                    frag = GameListFragment()
                }
            }
            else -> frag = GameListFragment()
        }

        showFragment(frag, args)
    }

    fun applyFullscreenMode(enabled: Boolean) {

        if(supportActionBar == null)
            return

        if (enabled) {
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            //supportActionBar!!.hide()
            findViewById<View>(android.R.id.content).background = ColorDrawable(resources.getColor(android.R.color.black))
            mGameFilter.visibility = View.GONE
            mAutoCompleteResults.visibility = View.GONE
        }
        else {
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
            //supportActionBar!!.show()
            findViewById<View>(android.R.id.content).background = ColorDrawable(resources.getColor(R.color.colorPrimary))
            mGameFilter.visibility = View.VISIBLE
            mAutoCompleteResults.visibility = View.VISIBLE
        }
    }

    @Throws(Exception::class)
    override fun accept(whatIsEntryAPIResponse: SpeedrunMiddlewareAPI.APIResponse<WhatIsEntry>) {

        if(whatIsEntryAPIResponse.error != null) {
            Util.showErrorToast(this, getString(R.string.error_could_not_connect))
            return
        }

        if (whatIsEntryAPIResponse.data!!.isEmpty()) {
            Util.showErrorToast(this, getString(R.string.error_could_not_find))
            return
        }

        val args = Bundle()
        var frag: Fragment? = null

        val entry = whatIsEntryAPIResponse.data[0]

        if(entry == null) {
            Analytics.logNotFound(this, intent.data!!)

            val containerView: FrameLayout = findViewById(R.id.detail_container)
            containerView.getChildAt(0).visibility = View.VISIBLE

            containerView.findViewById<Button>(R.id.buttonTryWebsite).setOnClickListener {
                openWebsite()
            }

            containerView.findViewById<Button>(R.id.buttonMainPage).setOnClickListener {
                showMainPage()
            }

            return
        }

        when (entry.type) {
            "game" -> {
                args.putString(GameDetailFragment.ARG_GAME_ID, entry.id)
                frag = GameDetailFragment()
            }
            "player" -> {
                args.putString(PlayerDetailFragment.ARG_PLAYER_ID, entry.id)
                frag = PlayerDetailFragment()
            }
            "run" -> {
                args.putString(RunDetailFragment.ARG_RUN_ID, entry.id)
                frag = RunDetailFragment()
            }
        }

        if (frag != null)
            showFragment(frag, args)
    }

    private fun openWebsite() {
        Util.openInBrowser(this, intent.data!!)
    }

    private fun showMainPage() {
        val intent = Intent(this, GameListFragment::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(intent)
        finish()
    }

    private fun showFragment(frag: Fragment, args: Bundle?) {
        if (args != null)
            frag.arguments = args

        supportFragmentManager.beginTransaction()
                .replace(R.id.detail_container, frag)
                .commit()
    }

    override fun beforeTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {

    }

    override fun onTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {}

    override fun afterTextChanged(editable: Editable) {
        val q = mGameFilter.text.toString().trim { it <= ' ' }
        mGameFilterSearchSubject.onNext(q)
        mAutoCompleteResults.visibility = if (q.isEmpty()) View.GONE else View.VISIBLE
    }

    override fun onItemClick(parent: AdapterView<*>, view: View, position: Int, id: Long) {
        mGameFilter.setText("")
        val item = parent.adapter.getItem(position)
        if (item is User) {
            showPlayer(item.id)
        } else if (item is Game) {
            showGame(item.id)
        }
    }

    fun showGame(gameId: String) {
        val intent = Intent(this, SpeedrunBrowserActivity::class.java)
        intent.putExtra(EXTRA_ITEM_TYPE, ItemType.GAMES)
        intent.putExtra(GameDetailFragment.ARG_GAME_ID, gameId)

        startActivity(intent)
    }

    fun showPlayer(playerId: String) {
        val intent = Intent(this, SpeedrunBrowserActivity::class.java)
        intent.putExtra(EXTRA_ITEM_TYPE, ItemType.PLAYERS)
        intent.putExtra(PlayerDetailFragment.ARG_PLAYER_ID, playerId)

        startActivity(intent)
    }

    companion object {
        private val TAG = SpeedrunBrowserActivity::class.java.simpleName

        const val EXTRA_ITEM_TYPE = "item_type"
    }
}
