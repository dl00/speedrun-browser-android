package danb.speedrunbrowser.views

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.util.AttributeSet
import android.util.Log
import android.view.*
import android.webkit.WebView
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat

import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit
import danb.speedrunbrowser.R
import danb.speedrunbrowser.api.objects.MediaLink
import danb.speedrunbrowser.utils.Constants
import danb.speedrunbrowser.utils.Util
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.internal.operators.flowable.FlowableInterval
import io.reactivex.schedulers.Schedulers

@SuppressLint("SetJavaScriptEnabled")
class MultiVideoView(context: Context, attrs: AttributeSet?) : FrameLayout(context, attrs) {


    private var mShownLink: MediaLink? = null

    // twitch
    private val mWebView: WebView = WebView(context)

    init {
        // configure the webview to support playing video
        mWebView.settings.setAppCacheMaxSize((1 * 1024 * 1024).toLong())
        mWebView.settings.javaScriptEnabled = true
        mWebView.settings.mediaPlaybackRequiresUserGesture = false
        mWebView.webChromeClient = CustomWebChromeClient(context)

        isFocusable = true
    }

    var seekTime: Int = 0

    private var isPlaying = false

    private var mayHaveContentWarning = false

    private var mListener: Listener? = null

    private var mPeriodicUpdate: Disposable? = null

    fun loadVideo(ml: MediaLink): Boolean {
        Log.d(TAG, "Trying to find YT/Twitch video: " + ml.uri)

        when {
            ml.youtubeVideoID != null -> setVideoFrameYT(ml)
            ml.twitchVideoID != null -> setVideoFrameTwitch(ml)
            else -> return false
        }

        isPlaying = true

        mShownLink = ml

        return true
    }

    fun stopVideo() {
        mWebView.loadUrl("about:blank")
        mShownLink = null
    }

    private fun setVideoFrameYT(m: MediaLink) {

        val videoId = m.youtubeVideoID

        Log.d(TAG, "Show YT video ID: " + videoId!!)

        val scaleFactor = 1.0f
        val pageContent: String
        try {
            pageContent = String.format(Locale.US, Util.readToString(javaClass.getResourceAsStream(Constants.YOUTUBE_EMBED_SNIPPET_FILE)!!), scaleFactor, videoId, seekTime)
        } catch (e: IOException) {
            setVideoFrameError()
            return
        }

        Log.d(TAG, pageContent)

        mWebView.settings.useWideViewPort = true
        mWebView.settings.loadWithOverviewMode = true

        mWebView.loadDataWithBaseURL("https://www.youtube.com", pageContent,
                "text/html", null, null)

        enable()

        removeAllViews()
        addView(mWebView)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setVideoFrameTwitch(m: MediaLink) {
        val videoId = m.twitchVideoID

        Log.d(TAG, "Show Twitch video ID: " + videoId!!)

        val scaleFactor = 1.0f
        val pageContent: String
        try {
            pageContent = String.format(Locale.US, Util.readToString(javaClass.getResourceAsStream(Constants.TWITCH_EMBED_SNIPPET_FILE)!!), scaleFactor, videoId, seekTime)
        } catch (e: IOException) {
            setVideoFrameError()
            return
        }

        Log.d(TAG, pageContent)

        mWebView.settings.useWideViewPort = true
        mWebView.settings.loadWithOverviewMode = true

        mWebView.loadDataWithBaseURL("https://player.twitch.tv", pageContent,
                "text/html", null, null)

        mayHaveContentWarning = true

        enable()

        removeAllViews()
        addView(mWebView)
    }

    private fun updateTwitchSeekTime() {
        mWebView.evaluateJavascript("player.getCurrentTime()") { value ->
            try {
                seekTime = Math.floor(java.lang.Float.parseFloat(value).toDouble()).toInt()
            } catch (e: NumberFormatException) {
                // ignored
            }
        }
    }

    fun setVideoFrameOther(m: MediaLink) {
        val ll = LinearLayout(context)
        ll.orientation = LinearLayout.VERTICAL
        ll.gravity = Gravity.CENTER
        ll.background = ColorDrawable(ContextCompat.getColor(context, R.color.colorPrimaryDark))

        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.gravity = Gravity.CENTER
        lp.topMargin = resources.getDimensionPixelSize(R.dimen.half_fab_margin)

        val tv = TextView(context)
        tv.layoutParams = lp
        tv.setText(R.string.msg_cannot_play_video)

        ll.addView(tv)

        val btn = Button(context)
        btn.layoutParams = lp
        btn.setText(R.string.btn_open_browser)
        btn.background = ColorDrawable(ContextCompat.getColor(context, R.color.colorPrimary))
        btn.setPadding(resources.getDimensionPixelSize(R.dimen.half_fab_margin), 0, resources.getDimensionPixelSize(R.dimen.half_fab_margin), 0)
        btn.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(m.uri.toString()))
            context.startActivity(intent)
        }

        ll.addView(btn)

        removeAllViews()
        addView(ll)
    }

    fun setVideoNotAvailable() {
        val ll = LinearLayout(context)
        ll.orientation = LinearLayout.VERTICAL
        ll.gravity = Gravity.CENTER
        ll.background = ColorDrawable(ContextCompat.getColor(context, R.color.colorPrimaryDark))

        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.gravity = Gravity.CENTER
        lp.topMargin = resources.getDimensionPixelSize(R.dimen.half_fab_margin)

        val tv = TextView(context)
        tv.layoutParams = lp
        tv.setText(R.string.msg_no_video)

        ll.addView(tv)
        addView(ll)
    }

    private fun setVideoFrameError() {
        val ll = LinearLayout(context)
        ll.orientation = LinearLayout.VERTICAL
        ll.gravity = Gravity.CENTER
        ll.background = ColorDrawable(ContextCompat.getColor(context, R.color.colorBackgroundError))

        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.gravity = Gravity.CENTER
        lp.topMargin = resources.getDimensionPixelSize(R.dimen.half_fab_margin)

        val tv = TextView(context)
        tv.layoutParams = lp
        tv.setText(R.string.error_cannot_play_video)

        ll.addView(tv)

        removeAllViews()
        addView(ll)
    }

    fun enable() {
        // due to restrictions of JS eval, we have to continuously pull seek time on an interval
        mPeriodicUpdate = FlowableInterval(0, 5, TimeUnit.SECONDS, Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ updateTwitchSeekTime() }, { throwable -> Log.w(TAG, "Problem running background save interval: ", throwable) })

    }

    fun disable() {
        mPeriodicUpdate?.dispose()
        mWebView.pauseTimers()
    }

    fun setListener(listener: Listener) {
        mListener = listener
    }

    fun hasLoadedVideo(): Boolean {
        return mShownLink != null
    }

    // media controls

    fun toggle() {
        if (isPlaying)
            pause()
        else
            play()
    }

    fun play() {
        mWebView.evaluateJavascript("player.play()") {}
        isPlaying = true
    }

    fun pause() {
        mWebView.evaluateJavascript("player.pause()") {}
        isPlaying = false
    }

    fun skip(time: Number) {
        mWebView.evaluateJavascript("player.skip(${ time})") {}
    }

    // special control bindings for android tv
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        var ret = true
        when (keyCode) {
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> toggle()
            KeyEvent.KEYCODE_DPAD_CENTER -> {
                if (mayHaveContentWarning) {
                    mWebView.evaluateJavascript("confirmInteract()") {}
                    mayHaveContentWarning = false
                }
                else
                    toggle()
            }
            KeyEvent.KEYCODE_MEDIA_PLAY -> play()
            KeyEvent.KEYCODE_MEDIA_PAUSE -> pause()
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> skip(30)
            KeyEvent.KEYCODE_DPAD_RIGHT -> skip(30)
            KeyEvent.KEYCODE_MEDIA_REWIND -> skip(-30)
            KeyEvent.KEYCODE_DPAD_LEFT -> skip(-30)
            else -> ret = super.onKeyDown(keyCode, event)
        }

        return ret
    }

    interface Listener {
        fun onFullscreenToggleListener()
    }

    companion object {
        private val TAG = MultiVideoView::class.java.simpleName
    }
}
