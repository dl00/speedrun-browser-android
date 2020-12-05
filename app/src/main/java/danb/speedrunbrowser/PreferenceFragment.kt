package danb.speedrunbrowser

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import danb.speedrunbrowser.api.SpeedrunAPI
import danb.speedrunbrowser.utils.Util
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.functions.Consumer

class PreferenceFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        val context = preferenceManager.context
        val screen = preferenceManager.createPreferenceScreen(context)

        val apiKeyPref = EditTextPreference(context)
        apiKeyPref.key = "srcApiKey"
        apiKeyPref.title = "Speedrun.com API Key for Moderation"
        apiKeyPref.onPreferenceClickListener = Preference.OnPreferenceClickListener {

            val intent = Intent(requireContext(), WebViewActivity::class.java)
            intent.putExtra(WebViewActivity.ARG_URL, Uri.parse(SRC_API_KEY_URL).toString())
            intent.putExtra(WebViewActivity.ARG_LOAD_CLIP, true)

            startActivity(intent)

            Util.showMsgToast(context, resources.getString(R.string.msg_get_api_key))

            true
        }

        val setApiKeyIcon = {
            val key = preferenceManager.sharedPreferences.getString(PREF_SRC_API_KEY, "")
            if (key != null && key != "") {
                // check API key
                 SpeedrunAPI.make(context).getProfile(key)
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(Consumer { data ->
                            //Util.showMsgToast(context, getString(R.string.msg_welcome_user, data.data!!.resolvedName))
                            val d = ContextCompat.getDrawable(context, R.drawable.baseline_done_24)!!
                            d.setTint(ContextCompat.getColor(context, R.color.colorGood))
                            apiKeyPref.icon = d

                            preferenceManager.sharedPreferences.edit()
                                    .putString(PREF_SRC_USER_ID, data.data!!.id)
                                    .apply()

                        }, Consumer {
                            // user not loadable
                            //Util.showErrorToast(context, getString(R.string.error_api_key_invalid))
                            val d = ContextCompat.getDrawable(context, R.drawable.baseline_error_24)!!
                            d.setTint(ContextCompat.getColor(context, R.color.colorBad))
                            apiKeyPref.icon = d

                            return@Consumer
                        })
            }
            else {
                preferenceManager.sharedPreferences.edit()
                        .putString(PREF_SRC_USER_ID, "")
                        .apply()

                val d = ContextCompat.getDrawable(context, R.drawable.baseline_error_24)!!
                d.setTint(ContextCompat.getColor(context, R.color.colorBad))
                apiKeyPref.icon = d
            }

        }

        apiKeyPref.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _: Preference, _: Any ->
            setApiKeyIcon()

            true
        }

        setApiKeyIcon()

        screen.addPreference(apiKeyPref)

        val notifyPref = SwitchPreferenceCompat(context)
        notifyPref.key = "modNotify"
        notifyPref.title = "Enable moderation reminder notifications"

        screen.addPreference(notifyPref)

        preferenceScreen = screen
    }

    companion object {
        // SRC requires url to have username in the URL, which means we cannot just send the user directly to their page
        const val SRC_API_KEY_URL: String = "https://www.speedrun.com/api/auth"

        const val PREF_SRC_API_KEY: String = "srcApiKey"
        const val PREF_SRC_USER_ID: String = "srcUserID"
    }

}