package danb.speedrunbrowser

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView

import java.net.MalformedURLException
import java.net.URL
import java.util.ArrayList
import androidx.fragment.app.Fragment
import danb.speedrunbrowser.api.SpeedrunMiddlewareAPI
import danb.speedrunbrowser.api.objects.LeaderboardRunEntry
import danb.speedrunbrowser.api.objects.User
import danb.speedrunbrowser.stats.PlayerStatisticsActivity
import danb.speedrunbrowser.utils.*
import danb.speedrunbrowser.views.ProgressSpinnerView
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.functions.Consumer
import io.reactivex.schedulers.Schedulers

class PlayerDetailFragment : Fragment(), View.OnClickListener {

    private val mDisposables = CompositeDisposable()

    private lateinit var mDB: AppDatabase

    private var mPlayer: User? = null
    private var mSubscription: AppDatabase.Subscription? = null

    private var mMenu: Menu? = null

    private lateinit var mSpinner: ProgressSpinnerView
    private lateinit var mPlayerHead: View
    private var mScrollBests: View? = null
    private var mFrameBests: View? = null

    private lateinit var mPlayerIcon: ImageView
    private lateinit var mPlayerName: TextView
    private lateinit var mPlayerCountry: ImageView

    private lateinit var mIconTwitch: ImageView
    private lateinit var mIconTwitter: ImageView
    private lateinit var mIconYoutube: ImageView
    private lateinit var mIconZSR: ImageView

    private lateinit var mBestsFrame: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setHasOptionsMenu(true)

        mDB = AppDatabase.make(context!!)

        activity!!.setTitle(R.string.title_loading)

        val args = arguments

        if(args != null) {
            mPlayer = args.getSerializable(ARG_PLAYER) as User?
            if (mPlayer != null) {
                loadSubscription(mPlayer!!.id)
                setViewData()
            } else if (args.getString(ARG_PLAYER_ID) != null) {
                val pid = args.getString(ARG_PLAYER_ID)
                loadSubscription(pid)
                loadPlayer(pid)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        mDisposables.dispose()
        mDB.close()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {

        val rootView = inflater.inflate(R.layout.activity_player_detail, container, false)

        mSpinner = rootView.findViewById(R.id.spinner)
        mPlayerHead = rootView.findViewById(R.id.layoutPlayerHeader)
        mScrollBests = rootView.findViewById(R.id.scrollPlayerBests)
        mFrameBests = rootView.findViewById(R.id.framePlayerBests)

        mPlayerIcon = rootView.findViewById(R.id.imgAvatar)
        mPlayerCountry = rootView.findViewById(R.id.imgPlayerCountry)
        mPlayerName = rootView.findViewById(R.id.txtPlayerName)
        mIconTwitch = rootView.findViewById(R.id.iconTwitch)
        mIconTwitter = rootView.findViewById(R.id.iconTwitter)
        mIconYoutube = rootView.findViewById(R.id.iconYoutube)
        mIconZSR = rootView.findViewById(R.id.iconZSR)
        mBestsFrame = rootView.findViewById(R.id.bestsLayout)

        mIconTwitch.setOnClickListener(this)
        mIconTwitter.setOnClickListener(this)
        mIconYoutube.setOnClickListener(this)
        mIconZSR.setOnClickListener(this)

        setViewData()

        return rootView
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.player, menu)
        mMenu = menu
        setMenu()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.menu_subscribe) {
            toggleSubscribed()
            return true
        }
        else if(item.itemId == R.id.menu_stats) {
            viewStats()
            return true
        }
        else if(item.itemId == R.id.menu_view_website) {
            startActivity(Util.openInBrowser(context!!, Uri.parse(mPlayer!!.weblink)))
            return true
        }

        return false
    }

    private fun loadSubscription(playerId: String?) {
        mDisposables.add(mDB.subscriptionDao()[playerId!!]
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe { subscription ->
                    mSubscription = subscription
                    setMenu()
                })
    }

    private fun loadPlayer(playerId: String?) {
        Log.d(TAG, "Download playerId: " + playerId!!)

        /// TODO: ideally this would be zipped/run in parallel
        mDisposables.add(SpeedrunMiddlewareAPI.make().listPlayers(playerId)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(Consumer { gameAPIResponse ->
                    if (gameAPIResponse.data == null || gameAPIResponse.data.isEmpty()) {
                        // game was not able to be found for some reason?
                        Util.showErrorToast(context!!, getString(R.string.error_missing_game, playerId))
                        return@Consumer
                    }

                    mPlayer = gameAPIResponse.data[0]

                    setViewData()


                    Analytics.logItemView(context!!, "player", playerId)
                }, ConnectionErrorConsumer(context!!)))
    }

    private fun toggleSubscribed(): Boolean {

        val subscribeMenuItem = mMenu!!.findItem(R.id.menu_subscribe)

        val psv = ProgressSpinnerView(context!!, null)
        psv.setDirection(ProgressSpinnerView.Direction.RIGHT)
        psv.setScale(0.5f)

        subscribeMenuItem.actionView = psv

        val sc = SubscriptionChanger(context!!, mDB)

        if (mSubscription != null) {
            mDisposables.add(sc.unsubscribeFrom(mSubscription!!)
                    .subscribe {
                        mSubscription = null
                        setMenu()
                        Util.showMsgToast(context!!, getString(R.string.success_subscription))
                    })

            return true
        } else if (mPlayer != null) {
            mSubscription = AppDatabase.Subscription("player", mPlayer!!.id, mPlayer!!.resolvedName.toLowerCase())

            mDisposables.add(sc.subscribeTo(mSubscription!!)
                    .subscribe {
                        setMenu()
                        Util.showMsgToast(context!!, getString(R.string.success_subscription))
                    })

            return true
        }

        return false
    }

    private fun setMenu() {
        if (mMenu == null)
            return

        val subscribeItem = mMenu!!.findItem(R.id.menu_subscribe)
        subscribeItem.actionView = null
        if (mSubscription != null) {
            subscribeItem.setIcon(R.drawable.baseline_star_24)
            subscribeItem.setTitle(R.string.menu_unsubscribe)
        } else {
            subscribeItem.setIcon(R.drawable.baseline_star_border_24)
            subscribeItem.setTitle(R.string.menu_subscribe)
        }
    }

    private fun setViewData() {
        if (mPlayer != null) {
            val il = ImageLoader(context!!)

            activity!!.setTitle(mPlayer!!.resolvedName)

            // find out if we are subscribed
            setMenu()

            mPlayer!!.applyCountryImage(mPlayerCountry)
            mPlayer!!.applyTextView(mPlayerName)

            mIconTwitch.visibility = if (mPlayer!!.twitch != null) View.VISIBLE else View.GONE
            mIconTwitter.visibility = if (mPlayer!!.twitter != null) View.VISIBLE else View.GONE
            mIconYoutube.visibility = if (mPlayer!!.youtube != null) View.VISIBLE else View.GONE
            mIconZSR.visibility = if (mPlayer!!.speedrunslive != null) View.VISIBLE else View.GONE


            if (!mPlayer!!.isGuest) {
                try {
                    mBestsFrame.visibility = View.VISIBLE
                    mDisposables.add(il.loadImage(URL(String.format(Constants.AVATAR_IMG_LOCATION, mPlayer!!.names!!["international"])))
                            .subscribe(ImageViewPlacerConsumer(mPlayerIcon)))
                } catch (e: MalformedURLException) {
                    Log.w(TAG, "Chould not show player logo:", e)
                    mBestsFrame.visibility = View.GONE
                }

            } else
                mBestsFrame.visibility = View.GONE

            populateBestsFrame(il)

            mSpinner.visibility = View.GONE
            mPlayerHead.visibility = View.VISIBLE

            mScrollBests?.visibility = View.VISIBLE
            mFrameBests?.visibility = View.VISIBLE
        }
    }

    @SuppressLint("SetTextI18n")
    private fun populateBestsFrame(il: ImageLoader) {
        if (mPlayer!!.bests == null)
            return

        val playerGameBests: List<User.UserGameBests> =
                ArrayList<User.UserGameBests>(mPlayer!!.bests!!.values)
                        .sortedBy { it.newestRun?.run?.date ?: "00000000000" }.reversed()

        for (gameBests in playerGameBests) {
            val gameLayout = layoutInflater.inflate(R.layout.content_game_personal_bests, null)

            (gameLayout.findViewById<View>(R.id.txtGameName) as TextView).setText(gameBests.names!!.get("international"))

            if (gameBests.assets!!.coverLarge != null) {
                val imgView = gameLayout.findViewById<ImageView>(R.id.imgGameCover)
                mDisposables.add(il.loadImage(gameBests.assets.coverLarge!!.uri)
                        .subscribe(ImageViewPlacerConsumer(imgView)))
            }

            var runsToAdd: MutableList<PersonalBestRunRow> = mutableListOf()

            for (categoryBest in gameBests.categories!!.values) {

                if (categoryBest.levels != null && categoryBest.levels.isNotEmpty()) {
                    for (levelBest in categoryBest.levels.values) {
                        val rr = PersonalBestRunRow(categoryBest.name, levelBest.name, levelBest.run)
                        runsToAdd.add(rr)
                    }
                } else {
                    val rr = PersonalBestRunRow(categoryBest.name, null, categoryBest.run!!)
                    runsToAdd.add(rr)
                }
            }

            // sort these runs by date, descending
            val runsAdded = runsToAdd.sortedByDescending { it.re.run.date ?: "00000000000" }

            val bestTable = gameLayout.findViewById<TableLayout>(R.id.tablePersonalBests)

            for (row in runsAdded) {
                val rowPersonalBest = layoutInflater.inflate(R.layout.content_row_personal_best, null) as TableRow
                (rowPersonalBest.findViewById<View>(R.id.txtRunCategory) as TextView).text = row.label

                val placeImg = rowPersonalBest.findViewById<ImageView>(R.id.imgPlace)

                if (row.re.place == 1 && gameBests.assets.trophy1st != null) {
                    mDisposables.add(il.loadImage(gameBests.assets.trophy1st.uri)
                            .subscribe(ImageViewPlacerConsumer(placeImg)))
                }
                if (row.re.place == 2 && gameBests.assets.trophy2nd != null) {
                    mDisposables.add(il.loadImage(gameBests.assets.trophy2nd.uri)
                            .subscribe(ImageViewPlacerConsumer(placeImg)))
                }
                if (row.re.place == 3 && gameBests.assets.trophy3rd != null) {
                    mDisposables.add(il.loadImage(gameBests.assets.trophy3rd.uri)
                            .subscribe(ImageViewPlacerConsumer(placeImg)))
                }
                if (row.re.place == 4 && gameBests.assets.trophy4th != null) {
                    mDisposables.add(il.loadImage(gameBests.assets.trophy4th.uri)
                            .subscribe(ImageViewPlacerConsumer(placeImg)))
                } else
                    (placeImg as ImageView).setImageDrawable(ColorDrawable(Color.TRANSPARENT))

                (rowPersonalBest.findViewById<View>(R.id.txtPlace) as TextView).text = row.re.placeName

                (rowPersonalBest.findViewById<View>(R.id.txtRunTime) as TextView).text = row.re.run.times!!.time
                (rowPersonalBest.findViewById<View>(R.id.txtRunDate) as TextView).text = row.re.run.date

                rowPersonalBest.setOnClickListener { viewRun(row.re.run.id) }

                bestTable.addView(rowPersonalBest)
            }

            mBestsFrame.addView(gameLayout)
        }
    }

    override fun onClick(v: View) {

        var selectedLink: URL? = null

        if (v === mIconTwitch)
            selectedLink = mPlayer!!.twitch!!.uri
        if (v === mIconTwitter)
            selectedLink = mPlayer!!.twitter!!.uri
        if (v === mIconYoutube)
            selectedLink = mPlayer!!.youtube!!.uri
        if (v === mIconZSR)
            selectedLink = mPlayer!!.speedrunslive!!.uri

        if (selectedLink != null) {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(selectedLink.toString()))
            startActivity(intent)
        }
    }

    private fun viewRun(runId: String) {
        val intent = Intent(context, SpeedrunBrowserActivity::class.java)
        intent.putExtra(SpeedrunBrowserActivity.EXTRA_ITEM_TYPE, ItemType.RUNS)
        intent.putExtra(RunDetailFragment.ARG_RUN_ID, runId)
        startActivity(intent)
    }

    private fun viewStats() {
        if(mPlayer != null) {
            val intent = Intent(context!!, PlayerStatisticsActivity::class.java)
            intent.putExtra(PlayerStatisticsActivity.EXTRA_PLAYER_ID, mPlayer!!.id)
            startActivity(intent)
        }
    }

    private class PersonalBestRunRow(categoryName: String, levelName: String?, var re: LeaderboardRunEntry) {

        var label: String

        init {

            if (levelName != null)
                label = "$categoryName - $levelName"
            else
                label = categoryName
        }
    }

    companion object {
        private val TAG = PlayerDetailFragment::class.java.simpleName

        val ARG_PLAYER = "player"
        val ARG_PLAYER_ID = "player_id"
    }
}
