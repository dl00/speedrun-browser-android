package danb.speedrunbrowser

import android.content.Intent
import android.content.res.Configuration
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.widget.*

import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import danb.speedrunbrowser.api.SpeedrunMiddlewareAPI
import danb.speedrunbrowser.api.objects.Game
import danb.speedrunbrowser.api.objects.GameGroup
import danb.speedrunbrowser.api.objects.User
import danb.speedrunbrowser.api.objects.WhatIsEntry
import danb.speedrunbrowser.utils.*
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable
import io.reactivex.functions.Consumer
import io.reactivex.schedulers.Schedulers
import io.reactivex.subjects.PublishSubject
import androidx.fragment.app.FragmentManager
import android.app.Activity
import android.os.PersistableBundle
import android.view.*
import android.view.inputmethod.InputMethodManager
import androidx.core.content.ContextCompat
import androidx.navigation.*
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.NavigationUI
import androidx.viewpager.widget.ViewPager
import danb.speedrunbrowser.views.MultiVideoView


class SpeedrunBrowserActivity : AppCompatActivity(), TextWatcher, AdapterView.OnItemClickListener, Consumer<SpeedrunMiddlewareAPI.APIResponse<WhatIsEntry>>, RunDetailFragment.VideoSupplier, NavController.OnDestinationChangedListener {

    private var whatIsQuery: Disposable? = null

    private lateinit var mGameFilterSearchSubject: PublishSubject<String>

    private lateinit var mGameFilter: EditText

    private lateinit var mAutoCompleteResults: ListView

    private lateinit var mVideoView: MultiVideoView

    private val mDisposables = CompositeDisposable()

    private var hasFocusedGameList = false

    private val searchNavOptions = NavOptions.Builder()
            .setEnterAnim(R.anim.fade_shift_top_in)
            .setExitAnim(R.anim.fade_shift_top_out)
            .setPopExitAnim(R.anim.fade_shift_top_out)
            .setPopEnterAnim(R.anim.fade_shift_top_out)
            .build()


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

        mVideoView = MultiVideoView(this, null)


        val navController = findNavController(R.id.nav_host)
        navController.addOnDestinationChangedListener(this)

        NavigationUI.setupActionBarWithNavController(this, navController)

        if (savedInstanceState?.getBundle(SAVED_INTENT_EXTRAS) != null && intent.getStringExtra(EXTRA_FRAGMENT_CLASSPATH) == null) {
            val intent = Intent(this, SpeedrunBrowserActivity::class.java)
            val e = savedInstanceState.getBundle(SAVED_INTENT_EXTRAS)!!

            intent.putExtras(e)

            startActivity(intent)
        }
        else
            onNewIntent(intent)
    }

    override fun onResume() {
        super.onResume()

        hideKeyboard()
    }

    override fun onSaveInstanceState(outState: Bundle, outPersistentState: PersistableBundle) {
        super.onSaveInstanceState(outState, outPersistentState)

        outState.putBundle(SAVED_INTENT_EXTRAS, intent.extras)
    }

    override fun onDestinationChanged(controller: NavController, destination: NavDestination, arguments: Bundle?) {
        applyFullscreenMode(false)
        hideKeyboard()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)

        if(intent == null)
            return

        showIntent(intent)
    }

    override fun onDestroy() {
        super.onDestroy()

        if (whatIsQuery != null)
            whatIsQuery!!.dispose()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        /*val frag = supportFragmentManager.primaryNavigationFragment!!

        supportFragmentManager.beginTransaction()
                .detach(frag)
                //.attach(frag)
                .replace(R.id.nav_host, frag)
                .commit()*/
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        super.onCreateOptionsMenu(menu)
        menuInflater.inflate(R.menu.game_list, menu)

        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val id = item.itemId
        if (id == android.R.id.home) {
            return findNavController(R.id.nav_host).navigateUp()
        }
        return super.onOptionsItemSelected(item)
    }

    private fun showIntent(intent: Intent) {

        setIntent(intent)

        when {
            intent.data != null -> {
                val segs = intent.data!!.pathSegments

                if (segs.intersect(BLACKLIST_URL_SEGS).isNotEmpty()) {
                    val notFoundArgs = Bundle()
                    notFoundArgs.putParcelable(NotFoundFragment.ARG_URL, intent.data!!)
                    findNavController(R.id.nav_host).navigate(R.id.notFoundFragment, notFoundArgs)
                    return
                }


                if (segs.isNotEmpty()) {

                    Log.d(TAG, "Decoded possible ID segments: " + segs + ", from URL: " + intent.data)

                    // use the whatis api to resolve the type of object
                    whatIsQuery = SpeedrunMiddlewareAPI.make(this)
                            .whatAreThese(segs.joinToString(","))
                            .subscribeOn(Schedulers.io())
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe(this, ConnectionErrorConsumer(this))
                    return
                }
            }
        }
    }

    override fun onBackPressed() {

        if (mGameFilter.text.isNotEmpty()) {
            mGameFilter.text.clear()
            return
        }

        if (supportFragmentManager.fragments[0] is GameListFragment && hasFocusedGameList) {
            (supportFragmentManager.fragments[0] as GameListFragment).requestFocus()
            hasFocusedGameList = true
            return
        }

        super.onBackPressed()
    }

    fun applyFullscreenMode(enabled: Boolean) {

        if(supportActionBar == null)
            return

        if (enabled) {
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            supportActionBar!!.hide()
            findViewById<View>(R.id.layoutRoot).background = ColorDrawable(ContextCompat.getColor(this, android.R.color.black))
            mGameFilter.visibility = View.GONE
        }
        else {
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
            supportActionBar!!.show()
            findViewById<View>(R.id.layoutRoot).background = ColorDrawable(ContextCompat.getColor(this, R.color.colorPrimary))
            mGameFilter.visibility = View.VISIBLE
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

        val entries = whatIsEntryAPIResponse.data.filterNotNull()

        if(entries.isEmpty()) {
            Analytics.logNotFound(this, intent.data!!)

            args.putParcelable(NotFoundFragment.ARG_URL, intent.data!!)
            findNavController(R.id.nav_host).navigate(R.id.notFoundFragment, args)

            return
        }

        val lastEntry = entries.last()

        when (lastEntry.type) {
            "game" -> {
                args.putString(GameDetailFragment.ARG_GAME_ID, lastEntry.id)
                findNavController(R.id.nav_host).navigate(R.id.gameDetailFragment, args)
            }
            "player" -> {
                args.putString(PlayerDetailFragment.ARG_PLAYER_ID, lastEntry.id)
                findNavController(R.id.nav_host).navigate(R.id.playerDetailFragment, args)
            }
            "run" -> {
                args.putString(RunDetailFragment.ARG_RUN_ID, lastEntry.id)
                findNavController(R.id.nav_host).navigate(R.id.runDetailFragment, args)
            }
        }
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
        when (val item = parent.adapter.getItem(position)) {
            is User -> {
                showPlayer(item.id)
            }
            is Game -> {
                showGame(item.id)
            }
            is GameGroup -> {
                showGameGroup(item)
            }
        }
    }

    private fun showGame(gameId: String) {
        val b = Bundle()
        b.putString("gameId", gameId)
        findNavController(R.id.nav_host).navigate(R.id.gameDetailFragment, b, searchNavOptions)
    }

    private fun showGameGroup(gameGroup: GameGroup) {
        val b = Bundle()
        b.putSerializable("gameGroup", gameGroup)
        findNavController(R.id.nav_host).navigate(R.id.gameListFragment, b, searchNavOptions)
    }

    private fun showPlayer(playerId: String) {
        val b = Bundle()
        b.putString("playerId", playerId)
        findNavController(R.id.nav_host).navigate(R.id.playerDetailFragment, b, searchNavOptions)
    }

    private fun hideKeyboard() {
        val imm = getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
        //Find the currently focused view, so we can grab the correct window token from it.
        var view = currentFocus
        //If no view currently has focus, create a new one, just so we can grab a window token from it
        if (view == null) {
            view = View(this)
        }
        imm.hideSoftInputFromWindow(view.windowToken, 0)
        this.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {

        val frag = supportFragmentManager.fragments[0]

        when (keyCode) {
            KeyEvent.KEYCODE_SEARCH -> {
                mGameFilter.requestFocus()
                return true
            }
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                // open filters menu
                (frag as? MediaControlListener)?.onFastForwardPressed()
                return true
            }
            KeyEvent.KEYCODE_MEDIA_REWIND -> {
                // open statistics for whatever we are looking at
                (frag as? MediaControlListener)?.onRewindPressed()
                return true
            }
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                // open statistics for whatever we are looking at
                (frag as? MediaControlListener)?.onPlayPausePressed()
                return true
            }
        }

        var cur: Any? = currentFocus
        // if its inside a view pager, we want to override the functionality
        while(cur != null && cur !is ViewPager) {
            if (cur is View)
                cur = cur.parent
            else
                cur = null
        }

        if (cur is ViewPager) {
            if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                cur.currentItem = cur.currentItem + 1
                return true
            }
            else if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                cur.currentItem = cur.currentItem - 1
                return true
            }
        }

        return super.onKeyDown(keyCode, event)
    }

    override fun requestVideoView(): MultiVideoView {
        if(mVideoView.parent is ViewGroup)
            (mVideoView.parent as ViewGroup).removeView(mVideoView)

        return mVideoView
    }

    interface MediaControlListener {
        fun onRewindPressed()
        fun onFastForwardPressed()
        fun onPlayPausePressed()
    }

    companion object {
        private val TAG = SpeedrunBrowserActivity::class.java.simpleName

        const val EXTRA_FRAGMENT_CLASSPATH = "fragment_classpath"

        const val SAVED_INTENT_EXTRAS = "intent_extras"

        private val BLACKLIST_URL_SEGS = listOf("forum", "thread")
    }
}
