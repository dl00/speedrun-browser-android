package danb.speedrunbrowser

import android.content.Intent
import android.content.res.Configuration
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.*

import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import danb.speedrunbrowser.api.SpeedrunMiddlewareAPI
import danb.speedrunbrowser.api.objects.Game
import danb.speedrunbrowser.api.objects.GameGroup
import danb.speedrunbrowser.api.objects.User
import danb.speedrunbrowser.api.objects.WhatIsEntry
import danb.speedrunbrowser.stats.GameGroupStatisticsFragment
import danb.speedrunbrowser.utils.*
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable
import io.reactivex.functions.Consumer
import io.reactivex.schedulers.Schedulers
import io.reactivex.subjects.PublishSubject
import androidx.fragment.app.FragmentManager
import android.app.Activity
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import danb.speedrunbrowser.views.MultiVideoView


class SpeedrunBrowserActivity : AppCompatActivity(), TextWatcher, AdapterView.OnItemClickListener, Consumer<SpeedrunMiddlewareAPI.APIResponse<WhatIsEntry>> {

    private var whatIsQuery: Disposable? = null

    private lateinit var mGameFilterSearchSubject: PublishSubject<String>

    private lateinit var mGameFilter: EditText

    private lateinit var mAutoCompleteResults: ListView

    private lateinit var mVideoView: MultiVideoView

    private val mDisposables = CompositeDisposable()

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

        supportFragmentManager.removeOnBackStackChangedListener {
            onFragmentMove()
        }

        supportFragmentManager.addOnBackStackChangedListener {
            onFragmentMove()
        }

        onNewIntent(intent)
    }

    private fun onFragmentMove() {
        applyFullscreenMode(false)

        hideKeyboard()

        supportActionBar!!.setDisplayHomeAsUpEnabled(supportFragmentManager.backStackEntryCount != 0 ||
                supportFragmentManager.fragments[0] !is GameListFragment)
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

        // reattach current fragment
        val frag = supportFragmentManager.fragments[0]

        // TODO: clean this up
        if (frag is RunDetailFragment)
            return

        supportFragmentManager.beginTransaction()
                .detach(frag)
                .attach(frag)
                .commit()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        super.onCreateOptionsMenu(menu)
        menuInflater.inflate(R.menu.game_list, menu)

        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val id = item.itemId
        if (id == android.R.id.home) {
            if(supportFragmentManager.backStackEntryCount >= 1) {
                val entry = supportFragmentManager.getBackStackEntryAt(
                        0)

                // clear the backstack and return to app root
                supportFragmentManager.popBackStack(entry.id,
                        FragmentManager.POP_BACK_STACK_INCLUSIVE)
                supportFragmentManager.executePendingTransactions()
            }
            else {
                showFragment(GameListFragment(), null, false)
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun showIntent(intent: Intent) {

        setIntent(intent)

        // Create the detail fragment and add it to the activity
        // using a fragment transaction.
        val args = intent.extras

        val cp = args?.getString(EXTRA_FRAGMENT_CLASSPATH)

        val frag: Fragment?

        when {
            cp != null -> frag = (Class.forName(cp) as Class<Fragment>).getConstructor().newInstance()
            intent.data != null -> {
                val segs = intent.data!!.pathSegments

                if (segs.intersect(BLACKLIST_URL_SEGS).isNotEmpty()) {
                    val notFoundArgs = Bundle()
                    notFoundArgs.putParcelable(NotFoundFragment.ARG_URL, intent.data!!)
                    showFragment(NotFoundFragment(), notFoundArgs)
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
                else {
                    frag = GameListFragment()
                }
            }
            else -> frag = GameListFragment()
        }

        showFragment(frag, args, true)
    }

    private fun showFragment(frag: Fragment, args: Bundle?, backstack: Boolean = true) {

        mVideoView.stopVideo()
        if(mVideoView.parent is ViewGroup)
            (mVideoView.parent as ViewGroup).removeView(mVideoView)

        if (frag is RunDetailFragment)
            frag.supplyVideoView(mVideoView)

        if (args != null)
            frag.arguments = args

        val transaction = supportFragmentManager.beginTransaction()
        transaction
                .setCustomAnimations(
                        R.anim.fade_shift_top_in,
                        R.anim.fade_shift_top_out,
                        R.anim.fade_shift_top_in,
                        R.anim.fade_shift_top_out)
                .replace(R.id.detail_container, frag)

        if (backstack && supportFragmentManager.fragments.isNotEmpty()) {
            transaction.addToBackStack(null)
            supportActionBar?.setDisplayShowHomeEnabled(true)
        }

        transaction.commit()
    }

    fun applyFullscreenMode(enabled: Boolean) {

        if(supportActionBar == null)
            return

        if (enabled) {
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            supportActionBar!!.hide()
            findViewById<View>(R.id.layoutRoot).background = ColorDrawable(resources.getColor(android.R.color.black))
            mGameFilter.visibility = View.GONE
        }
        else {
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
            supportActionBar!!.show()
            findViewById<View>(R.id.layoutRoot).background = ColorDrawable(resources.getColor(R.color.colorPrimary))
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
        var frag: Fragment? = null

        val entries = whatIsEntryAPIResponse.data.filterNotNull()

        if(entries.isEmpty()) {
            Analytics.logNotFound(this, intent.data!!)

            args.putParcelable(NotFoundFragment.ARG_URL, intent.data!!)
            showFragment(NotFoundFragment(), args)

            return
        }

        val lastEntry = entries.last()

        when (lastEntry.type) {
            "game" -> {
                args.putString(GameDetailFragment.ARG_GAME_ID, lastEntry.id)
                frag = GameDetailFragment()
            }
            "player" -> {
                args.putString(PlayerDetailFragment.ARG_PLAYER_ID, lastEntry.id)
                frag = PlayerDetailFragment()
            }
            "run" -> {
                args.putString(RunDetailFragment.ARG_RUN_ID, lastEntry.id)
                frag = RunDetailFragment()
            }
        }

        if (frag != null)
            showFragment(frag, args)
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
        val intent = Intent(this, SpeedrunBrowserActivity::class.java)
        intent.putExtra(EXTRA_FRAGMENT_CLASSPATH, GameDetailFragment::class.java.canonicalName)
        intent.putExtra(GameDetailFragment.ARG_GAME_ID, gameId)

        startActivity(intent)
    }

    private fun showGameGroup(gameGroup: GameGroup) {
        val intent = Intent(this, SpeedrunBrowserActivity::class.java)
        intent.putExtra(EXTRA_FRAGMENT_CLASSPATH, GameListFragment::class.java.canonicalName)
        intent.putExtra(GameListFragment.ARG_GAME_GROUP, gameGroup)

        startActivity(intent)
    }

    private fun showPlayer(playerId: String) {
        val intent = Intent(this, SpeedrunBrowserActivity::class.java)
        intent.putExtra(EXTRA_FRAGMENT_CLASSPATH, PlayerDetailFragment::class.java.canonicalName)
        intent.putExtra(PlayerDetailFragment.ARG_PLAYER_ID, playerId)

        startActivity(intent)
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
    }

    companion object {
        private val TAG = SpeedrunBrowserActivity::class.java.simpleName

        const val EXTRA_FRAGMENT_CLASSPATH = "fragment_classpath"

        private val BLACKLIST_URL_SEGS = listOf("forum", "thread")
    }
}
