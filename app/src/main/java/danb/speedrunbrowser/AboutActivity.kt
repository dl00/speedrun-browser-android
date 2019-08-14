package danb.speedrunbrowser

import android.app.AlertDialog
import android.content.DialogInterface
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.TextView

import androidx.appcompat.app.AppCompatActivity
import danb.speedrunbrowser.utils.Util

class AboutActivity : AppCompatActivity(), View.OnClickListener {

    private lateinit var mLinkSpeedrunComTrophy: ImageView
    private lateinit var mLinkSpeedrunComLogo: ImageView

    private lateinit var mAppTitle: TextView

    private lateinit var mLinkRateThis: TextView
    private lateinit var mLinkComplain: TextView
    private lateinit var mLinkTermsAndConditions: TextView
    private lateinit var mLinkPrivacyPolicy: TextView
    private lateinit var mLinkOpenSourceLicenses: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about)

        mAppTitle = findViewById(R.id.txtAppTitle)
        mLinkSpeedrunComTrophy = findViewById(R.id.linkSpeedrunComTrophy)
        mLinkSpeedrunComLogo = findViewById(R.id.linkSpeedrunComWebsite)
        mLinkComplain = findViewById(R.id.linkComplain)
        mLinkRateThis = findViewById(R.id.linkRateThis)
        mLinkPrivacyPolicy = findViewById(R.id.linkPrivacyPolicy)
        mLinkTermsAndConditions = findViewById(R.id.linkTermsAndConditions)
        mLinkOpenSourceLicenses = findViewById(R.id.linkOpenSourceLicenses)

        mLinkSpeedrunComTrophy.setOnClickListener(this)
        mLinkSpeedrunComLogo.setOnClickListener(this)
        mLinkRateThis.setOnClickListener(this)
        mLinkComplain.setOnClickListener(this)
        mLinkTermsAndConditions.setOnClickListener(this)
        mLinkPrivacyPolicy.setOnClickListener(this)
        mLinkOpenSourceLicenses.setOnClickListener(this)

        mAppTitle.text = "${mAppTitle.text} v${BuildConfig.VERSION_NAME}"
    }

    private fun openLink(link: Uri) {
        val intent = Intent(Intent.ACTION_VIEW, link)
        startActivity(intent)
    }

    private fun viewOpenSourceLicenses() {
        val licenseText: String
        try {
            licenseText = Util.readToString(
                    javaClass.getResourceAsStream(OPENSOURCE_LICENSES_FILE)!!)
        } catch (e: Exception) {
            // this basically should not happen
            Log.e(TAG, "Could not load license information from JAR file:", e)
            return
        }

        val dialog = AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                .setMessage(licenseText)
                .setNeutralButton(android.R.string.ok, null)
                .create()

        dialog.show()
    }

    override fun onClick(v: View) {
        if (v === mLinkSpeedrunComTrophy || v === mLinkSpeedrunComLogo)
            openLink(SPEEDRUN_COM_ABOUT)
        else if (v === mLinkComplain) {
            val dialog = AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                    .setTitle(R.string.dialog_title_send_email)
                    .setMessage(R.string.dialog_msg_send_email)
                    .setPositiveButton(R.string.dialog_button_send_email) { _, _ ->
                        startActivity(Intent.createChooser(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$COMPLAIN_EMAIL?subject=$COMPLAIN_EMAIL_TITLE")), "Send using"))
                    }
                    .setNeutralButton(android.R.string.cancel, null)
                    .create()

            dialog.show()
        }
        else if (v === mLinkRateThis)
            openLink(PLAY_STORE_APP)
        else if (v === mLinkTermsAndConditions)
            openLink(TERMS_AND_CONDITIONS)
        else if (v === mLinkPrivacyPolicy)
            openLink(PRIVACY_POLICY)
        else if (v === mLinkOpenSourceLicenses)
            viewOpenSourceLicenses()
    }

    companion object {
        private val TAG = LeaderboardFragment::class.java.simpleName

        private val SPEEDRUN_COM_ABOUT = Uri.parse("https://www.speedrun.com/about")

        private val PLAY_STORE_APP = Uri.parse("https://play.google.com/store/apps/details?id=danb.speedrunbrowser")

        private val COMPLAIN_EMAIL = Uri.parse("playstore@danb.email")
        private val COMPLAIN_EMAIL_TITLE = Uri.parse("[SR BROWSER] Problem Report")

        private val TERMS_AND_CONDITIONS = Uri.parse("https://speedrun-browser-4cc82.firebaseapp.com/terms.html")
        private val PRIVACY_POLICY = Uri.parse("https://speedrun-browser-4cc82.firebaseapp.com/privacy-policy.html")

        private const val OPENSOURCE_LICENSES_FILE = "/assets/licenses.txt"
    }
}
