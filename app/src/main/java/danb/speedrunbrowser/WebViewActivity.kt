package danb.speedrunbrowser

import android.app.Activity
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.webkit.WebView

class WebViewActivity : Activity() {

    private lateinit var webView: WebView;

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        webView = WebView(this)

        webView.loadUrl(intent.extras!!.getString(ARG_URL)!!)

        if (intent.extras!!.getBoolean(ARG_LOAD_CLIP)) {
            (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).addPrimaryClipChangedListener {
                finish()
            }
        }

        setContentView(webView)
    }

    companion object {
        private val TAG = WebViewActivity::class.java.simpleName

        val ARG_URL = "url"
        val ARG_LOAD_CLIP = "loadClip"
    }
}