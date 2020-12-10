package danb.speedrunbrowser

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.webkit.WebView

class WebViewActivity : Activity() {

    private lateinit var webView: WebView
    private lateinit var url: String

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        webView = WebView(this)

        webView.settings.javaScriptEnabled = true

        url = intent.extras!!.getString(ARG_URL)!!

        Log.d("WebViewActivity", "Show page: $url")

        if (intent.extras!!.getBoolean(ARG_LOAD_CLIP)) {
            (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).addPrimaryClipChangedListener {
                finish()
            }
        }

        setContentView(webView)
    }

    override fun onResume() {
        super.onResume()
        webView.loadUrl(url)
    }

    companion object {
        private val TAG = WebViewActivity::class.java.simpleName

        const val ARG_URL = "url"
        const val ARG_LOAD_CLIP = "loadClip"
    }
}