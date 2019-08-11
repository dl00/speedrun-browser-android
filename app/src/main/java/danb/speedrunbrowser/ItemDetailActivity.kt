package danb.speedrunbrowser

import android.content.Intent
import android.os.Bundle
import android.os.PersistableBundle
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

import androidx.appcompat.app.ActionBar
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import danb.speedrunbrowser.api.SpeedrunMiddlewareAPI
import danb.speedrunbrowser.api.objects.Run
import danb.speedrunbrowser.api.objects.WhatIsEntry
import danb.speedrunbrowser.utils.ConnectionErrorConsumer
import danb.speedrunbrowser.utils.ItemType
import danb.speedrunbrowser.utils.Util
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.functions.Consumer
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.activity_run_detail.*
import android.os.Parcelable
import android.R.attr.name
import android.content.ComponentName
import android.content.pm.ResolveInfo
import android.net.Uri
import androidx.core.view.accessibility.AccessibilityEventCompat.setAction



class ItemDetailActivity : AppCompatActivity(), Consumer<SpeedrunMiddlewareAPI.APIResponse<WhatIsEntry>> {

    private var whatIsQuery: Disposable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_item_detail)

        // Show the Up button in the action bar.
        val actionBar = supportActionBar
        actionBar?.setDisplayHomeAsUpEnabled(true)

        // Create the detail fragment and add it to the activity
        // using a fragment transaction.
        val intent = intent
        val args = intent.extras

        if (args == null) {
            finish() // nothing/no way to view...
        }

        val type = args!!.getSerializable(EXTRA_ITEM_TYPE) as ItemType?

        var frag: Fragment? = null

        when {
            type != null -> frag = when (type) {
                ItemType.GAMES -> GameDetailFragment()
                ItemType.PLAYERS -> PlayerDetailFragment()
                else -> {
                    showMainPage()
                    return
                }
            }
            intent.data != null -> {
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
            }
            else -> {
                Log.w(TAG, "Could not find game ID argument")

                Util.showErrorToast(this, getString(R.string.error_could_not_find, "No data provided"))

                finish()
            }
        }

        if (frag != null) {
            showFragment(frag, args)
        }
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

        if(entry == null) {
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

    private fun openWebsite() {
        val i = Intent(Intent.ACTION_VIEW)
        // using the actual URI does not work because android is too smart and will only give back my own app. So we use a dummy URL to force it over.
        i.data = Uri.parse("https://atotallyrealsiterightnow.com/whatever")

        val resInfos = packageManager.queryIntentActivities(i, 0)
        println(resInfos)
        if (resInfos.isNotEmpty()) {
            for (resInfo in resInfos) {
                val packageName = resInfo.activityInfo.packageName
                if (!packageName.toLowerCase().contains("danb.speedrunbrowser")) {
                    val browserIntent = Intent(Intent.ACTION_VIEW, intent.data)
                    browserIntent.component = ComponentName(packageName, resInfo.activityInfo.name)
                    browserIntent.setPackage(packageName)
                    startActivity(browserIntent)
                }
            }
        }
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
