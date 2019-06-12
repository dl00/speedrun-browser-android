package danb.speedrunbrowser

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.MenuItem

import androidx.appcompat.app.ActionBar
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import danb.speedrunbrowser.api.SpeedrunMiddlewareAPI
import danb.speedrunbrowser.api.objects.Run
import danb.speedrunbrowser.api.objects.WhatIsEntry
import danb.speedrunbrowser.utils.ConnectionErrorConsumer
import danb.speedrunbrowser.utils.Util
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.functions.Consumer
import io.reactivex.schedulers.Schedulers

class ItemDetailActivity : AppCompatActivity(), Consumer<SpeedrunMiddlewareAPI.APIResponse<WhatIsEntry>> {

    private var whatIsQuery: Disposable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_item_detail)

        // Show the Up button in the action bar.
        val actionBar = supportActionBar
        actionBar?.setDisplayHomeAsUpEnabled(true)

        // savedInstanceState is non-null when there is fragment state
        // saved from previous configurations of this activity
        // (e.g. when rotating the screen from portrait to landscape).
        // In this case, the fragment will automatically be re-added
        // to its container so we don't need to manually add it.
        // For more information, see the Fragments API guide at:
        //
        // http://developer.android.com/guide/components/fragments.html
        //
        if (savedInstanceState == null) {
            // Create the detail fragment and add it to the activity
            // using a fragment transaction.
            val intent = intent
            val args = intent.extras

            if (args == null) {
                finish() // nothing/no way to view...
            }

            val type = args!!.getSerializable(EXTRA_ITEM_TYPE) as ItemListFragment.ItemType?

            var frag: Fragment? = null

            if (type != null) {
                when (type) {
                    ItemListFragment.ItemType.GAMES -> frag = GameDetailFragment()
                    ItemListFragment.ItemType.PLAYERS -> frag = PlayerDetailFragment()
                }
            } else if (intent.data != null) {
                val segs = intent.data!!.pathSegments

                if (segs.isEmpty()) {
                    showMainPage()
                    return
                }

                val id = segs[segs.size - 1]

                Log.d(TAG, "Decoded game or player ID: " + id + ", from URL: " + intent.data)

                // use the whatis api to resolve the type of object
                whatIsQuery = SpeedrunMiddlewareAPI.make().whatAreThese(id)
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(this, ConnectionErrorConsumer(this))
            } else {
                Log.w(TAG, "Could not find game ID argument")

                Util.showErrorToast(this, getString(R.string.error_could_not_find, "No data provided"))

                finish()
            }

            if (frag != null) {
                showFragment(frag, args)
            }
        }
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
            navigateUpTo(Intent(this, GameListActivity::class.java))
            return true
        }
        return super.onOptionsItemSelected(item)
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

        when (entry.type) {
            "game" -> {
                args.putString(GameDetailFragment.ARG_GAME_ID, entry.id)
                frag = GameDetailFragment()
            }
            "player" -> {
                args.putString(PlayerDetailFragment.ARG_PLAYER_ID, entry.id)
                frag = PlayerDetailFragment()
            }
            "run" -> // wrong activity
                showRun(entry.id)
        }

        if (frag != null)
            showFragment(frag, args)
    }

    private fun showRun(id: String?) {
        val intent = Intent(this, RunDetailActivity::class.java)
        intent.putExtra(RunDetailActivity.EXTRA_RUN_ID, id)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(intent)
        finish()
    }

    private fun showMainPage() {
        val intent = Intent(this, GameListActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(intent)
        finish()
    }

    private fun showFragment(frag: Fragment, args: Bundle) {
        frag.arguments = args
        supportFragmentManager.beginTransaction()
                .add(R.id.detail_container, frag)
                .commit()
    }

    companion object {
        private val TAG = ItemDetailActivity::class.java.simpleName

        val EXTRA_ITEM_TYPE = "item_type"
    }
}
