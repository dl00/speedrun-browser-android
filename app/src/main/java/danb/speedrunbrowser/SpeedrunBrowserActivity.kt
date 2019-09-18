package danb.speedrunbrowser

import android.content.Intent
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
import danb.speedrunbrowser.stats.SiteStatisticsFragment
import danb.speedrunbrowser.utils.*
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable
import io.reactivex.functions.Consumer
import io.reactivex.schedulers.Schedulers
import io.reactivex.subjects.PublishSubject


class SpeedrunBrowserActivity : AppCompatActivity(), TextWatcher, AdapterView.OnItemClickListener, Consumer<SpeedrunMiddlewareAPI.APIResponse<WhatIsEntry>> {

    private var whatIsQuery: Disposable? = null

    private lateinit var mGameFilterSearchSubject: PublishSubject<String>

    private lateinit var mGameFilter: EditText

    private lateinit var mAutoCompleteResults: ListView

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

        // Show the Up button in the action bar.
        val actionBar = supportActionBar
        actionBar?.setDisplayHomeAsUpEnabled(true)

        onNewIntent(intent)
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

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        super.onCreateOptionsMenu(menu)
        menuInflater.inflate(R.menu.game_list, menu)

        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val id = item.itemId
        if (id == android.R.id.home) {
            //showFragment(mBackStack[0], null)
            //return true
        }
        else if (id == R.id.menu_site_stats) {
            showFragment(SiteStatisticsFragment(), null)
        }
        else if (id == R.id.menu_about) {
            showAbout()
        }
        return super.onOptionsItemSelected(item)
    }

    private fun showAbout() {
        val intent = Intent(this, AboutActivity::class.java)
        startActivity(intent)
    }

    private fun showIntent(intent: Intent) {

        val prevCp = this.intent.extras?.getString(EXTRA_FRAGMENT_CLASSPATH)

        setIntent(intent)

        applyFullscreenMode(false)

        // Create the detail fragment and add it to the activity
        // using a fragment transaction.
        val args = intent.extras

        val cp = args?.getString(EXTRA_FRAGMENT_CLASSPATH)

        val frag: Fragment?

        when {
            cp != null -> frag = (Class.forName(cp) as Class<Fragment>).getConstructor().newInstance()
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

        showFragment(frag, args, true)
    }

    private fun showFragment(frag: Fragment, args: Bundle?, backstack: Boolean = true) {

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

        if (backstack && supportFragmentManager.fragments.isNotEmpty())
            transaction.addToBackStack(null)

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
            //mAutoCompleteResults.visibility = View.GONE
        }
        else {
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
            supportActionBar!!.show()
            findViewById<View>(R.id.layoutRoot).background = ColorDrawable(resources.getColor(R.color.colorPrimary))
            mGameFilter.visibility = View.VISIBLE
            //mAutoCompleteResults.visibility = View.VISIBLE
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
        } else if (item is GameGroup) {
            showGameGroup(item)
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

    companion object {
        private val TAG = SpeedrunBrowserActivity::class.java.simpleName

        const val EXTRA_FRAGMENT_CLASSPATH = "fragment_classpath"
    }
}
