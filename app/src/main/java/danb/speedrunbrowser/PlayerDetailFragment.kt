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
import androidx.navigation.fragment.findNavController
import danb.speedrunbrowser.api.SpeedrunMiddlewareAPI
import danb.speedrunbrowser.api.objects.Game
import danb.speedrunbrowser.api.objects.LeaderboardRunEntry
import danb.speedrunbrowser.api.objects.User
import danb.speedrunbrowser.utils.*
import danb.speedrunbrowser.views.ProgressSpinnerView
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.functions.Consumer
import io.reactivex.schedulers.Schedulers

class PlayerDetailFragment : Fragment(), View.OnClickListener {

    private val mDisposables = CompositeDisposable()

    private lateinit var mDB: AppDatabase
    private lateinit var mImageLoader: ImageLoader

    private var mPlayer: User? = null
    private var mPlayerBests: ArrayList<LeaderboardRunEntry> = arrayListOf()
    private var mSubscription: AppDatabase.Subscription? = null

    private var mMenu: Menu? = null

    private lateinit var mSpinner: ProgressSpinnerView
    private lateinit var mBestsSpinner: ProgressSpinnerView
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

        mDB = AppDatabase.make(requireContext())
        mImageLoader = ImageLoader(requireContext())

        requireActivity().setTitle(R.string.title_loading)

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

        mBestsSpinner = rootView.findViewById(R.id.bestsSpinner)

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
            startActivity(Util.openInBrowser(requireContext(), Uri.parse(mPlayer!!.weblink)))
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

        mDisposables.add(SpeedrunMiddlewareAPI.make(requireContext()).listPlayers(playerId)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(Consumer { gameAPIResponse ->
                    if (gameAPIResponse.data == null || gameAPIResponse.data.isEmpty()) {
                        // game was not able to be found for some reason?
                        Util.showErrorToast(requireContext(), getString(R.string.error_missing_game, playerId))
                        return@Consumer
                    }

                    mPlayer = gameAPIResponse.data[0]
                    loadPlayerBests(mPlayer!!.id)

                    setViewData()


                    Analytics.logItemView(requireContext(), "player", playerId)
                }, ConnectionErrorConsumer(requireContext())))
    }

    private fun loadPlayerBests(playerId: String, replace: Boolean = false) {

        var startAfter = ""
        if (!replace) {
            startAfter = mPlayerBests.lastOrNull()?.run?.id ?: ""
        }

        mBestsSpinner.start()
        mBestsSpinner.visibility = View.VISIBLE

        mDisposables.add(SpeedrunMiddlewareAPI.make(requireContext()).listPlayerBests(playerId, startAfter)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(Consumer { runsApiResponse ->
                    if (runsApiResponse.data == null || runsApiResponse.data.isEmpty()) {
                        // game was not able to be found for some reason?
                        Util.showErrorToast(requireContext(), getString(R.string.error_missing_game, playerId))
                        return@Consumer
                    }

                    val runsList = runsApiResponse.data.filterNotNull()

                    if (replace)
                        mPlayerBests = ArrayList(runsList)
                    else
                        mPlayerBests.addAll(runsList)

                    populateBestsFrame(replace)

                    mBestsSpinner.stop()
                    mBestsSpinner.visibility = View.GONE

                    Analytics.logItemView(requireContext(), "player", playerId)
                }, ConnectionErrorConsumer(requireContext())))
    }

    private fun toggleSubscribed(): Boolean {

        val subscribeMenuItem = mMenu!!.findItem(R.id.menu_subscribe)

        val psv = ProgressSpinnerView(requireContext(), null)
        psv.setDirection(ProgressSpinnerView.Direction.RIGHT)
        psv.setScale(0.5f)

        subscribeMenuItem.actionView = psv

        val sc = SubscriptionChanger(requireContext(), mDB)

        if (mSubscription != null) {
            mDisposables.add(sc.unsubscribeFrom(mSubscription!!)
                    .subscribe {
                        mSubscription = null
                        setMenu()
                        Util.showMsgToast(requireContext(), getString(R.string.success_subscription))
                    })

            return true
        } else if (mPlayer != null) {
            mSubscription = AppDatabase.Subscription("player", mPlayer!!.id, mPlayer!!.resolvedName.toLowerCase())

            mDisposables.add(sc.subscribeTo(mSubscription!!)
                    .subscribe {
                        setMenu()
                        Util.showMsgToast(requireContext(), getString(R.string.success_subscription))
                    })

            return true
        }

        return false
    }

    private fun setMenu() {
        if (mMenu == null)
            return

        val subscribeItem = mMenu!!.findItem(R.id.menu_subscribe) ?: return

        subscribeItem.actionView = null
        if (mSubscription != null) {
            subscribeItem.setIcon(R.drawable.baseline_star_24)
            subscribeItem.setTitle(R.string.menu_unsubscribe)
        } else {
            subscribeItem.setIcon(R.drawable.baseline_star_border_24)
            subscribeItem.setTitle(R.string.menu_subscribe)
        }
    }

    override fun onResume() {
        super.onResume()
        if (mPlayer != null)
            requireActivity().title = mPlayer!!.resolvedName
    }

    private fun setViewData() {
        if (mPlayer != null) {
            val il = ImageLoader(requireContext())

            requireActivity().title = mPlayer!!.resolvedName

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
                    val placer = ImageViewPlacerConsumer(mPlayerIcon)
                    placer.roundedCorners = resources.getDimensionPixelSize(R.dimen.player_rounded_corners).toFloat()

                    mBestsFrame.visibility = View.VISIBLE
                    mDisposables.add(il.loadImage(URL(String.format(Constants.AVATAR_IMG_LOCATION, mPlayer!!.names!!["international"])))
                            .subscribeOn(Schedulers.io())
                            .subscribe(placer))
                } catch (e: MalformedURLException) {
                    Log.w(TAG, "Chould not show player logo:", e)
                    mBestsFrame.visibility = View.GONE
                }

            } else
                mBestsFrame.visibility = View.GONE

            populateBestsFrame()

            mSpinner.visibility = View.GONE
            mPlayerHead.visibility = View.VISIBLE

            mScrollBests?.visibility = View.VISIBLE
            mFrameBests?.visibility = View.VISIBLE
        }
    }

    @SuppressLint("SetTextI18n", "InflateParams")
    private fun populateBestsFrame(replace: Boolean = true) {

        if(replace) {
            mBestsFrame.removeAllViews()
        }

        var curGameLayout: View? = null
        var curGame: Game? = null
        for (pb in mPlayerBests) {

            if(curGame?.id != pb.run.game?.id) {
                if (curGameLayout != null)
                    mBestsFrame.addView(curGameLayout)

                curGameLayout = layoutInflater.inflate(R.layout.content_game_personal_bests, null)
                curGame = pb.run.game

                (curGameLayout.findViewById<View>(R.id.txtGameName) as TextView).text = curGame!!.names!!["international"]

                if (curGame.assets.coverLarge != null) {
                    val imgView = curGameLayout.findViewById<ImageView>(R.id.imgGameCover)

                    val placer = ImageViewPlacerConsumer(imgView)
                    placer.roundedCorners = resources.getDimensionPixelSize(R.dimen.game_cover_rounded_corners).toFloat()

                    mDisposables.add(mImageLoader.loadImage(curGame.assets.coverLarge!!.uri)
                            .subscribeOn(Schedulers.io())
                            .subscribe(placer))
                }
            }

            val bestTable = curGameLayout!!.findViewById<LinearLayout>(R.id.tablePersonalBests)

            val rowPersonalBest = layoutInflater.inflate(R.layout.content_row_personal_best, null) as LinearLayout
            (rowPersonalBest.findViewById<View>(R.id.txtRunCategory) as TextView).text = if (pb.run.level?.name != null) {
                "${pb.run.category!!.name} - ${pb.run.level.name}"
            } else {
                pb.run.category!!.name
            }

            val placeImg = rowPersonalBest.findViewById<ImageView>(R.id.imgPlace)

            if (pb.place == 1 && curGame!!.assets.trophy1st != null) {
                mDisposables.add(mImageLoader.loadImage(curGame.assets.trophy1st!!.uri)
                        .subscribeOn(Schedulers.io())
                        .subscribe(ImageViewPlacerConsumer(placeImg)))
            }
            if (pb.place == 2 && curGame!!.assets.trophy2nd != null) {
                mDisposables.add(mImageLoader.loadImage(curGame.assets.trophy2nd!!.uri)
                        .subscribeOn(Schedulers.io())
                        .subscribe(ImageViewPlacerConsumer(placeImg)))
            }
            if (pb.place == 3 && curGame!!.assets.trophy3rd != null) {
                mDisposables.add(mImageLoader.loadImage(curGame.assets.trophy3rd!!.uri)
                        .subscribeOn(Schedulers.io())
                        .subscribe(ImageViewPlacerConsumer(placeImg)))
            }
            if (pb.place == 4 && curGame!!.assets.trophy4th != null) {
                mDisposables.add(mImageLoader.loadImage(curGame.assets.trophy4th!!.uri)
                        .subscribeOn(Schedulers.io())
                        .subscribe(ImageViewPlacerConsumer(placeImg)))
            } else
                (placeImg as ImageView).setImageDrawable(ColorDrawable(Color.TRANSPARENT))

            (rowPersonalBest.findViewById<View>(R.id.txtPlace) as TextView).text = pb.placeName

            (rowPersonalBest.findViewById<View>(R.id.txtRunTime) as TextView).text = pb.run.times!!.time
            (rowPersonalBest.findViewById<View>(R.id.txtRunDate) as TextView).text = pb.run.date

            rowPersonalBest.setOnClickListener { viewRun(pb.run.id) }
            rowPersonalBest.isFocusable = true
            rowPersonalBest.background = resources.getDrawable(R.drawable.clickable_item)

            bestTable.addView(rowPersonalBest)
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
        findNavController().navigate(PlayerDetailFragmentDirections.actionPlayerDetailFragmentToRunDetailFragment(null, null, null, null, runId))
    }

    private fun viewStats() {
        if(mPlayer != null) {
            findNavController().navigate(PlayerDetailFragmentDirections.actionPlayerDetailFragmentToPlayerStatisticsFragment(mPlayer!!.id))
        }
    }

    companion object {
        private val TAG = PlayerDetailFragment::class.java.simpleName

        val ARG_PLAYER = "player"
        val ARG_PLAYER_ID = "playerId"
    }
}
