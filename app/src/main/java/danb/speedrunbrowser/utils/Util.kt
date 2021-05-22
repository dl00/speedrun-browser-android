@file:Suppress("DEPRECATION")

package danb.speedrunbrowser.utils

import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Looper
import android.util.Log
import android.widget.Toast

import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

import androidx.core.app.NotificationCompat
import androidx.core.app.TaskStackBuilder
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability
import danb.speedrunbrowser.*
import danb.speedrunbrowser.api.objects.User
import danb.speedrunbrowser.stats.GameStatisticsFragment
import io.noties.markwon.Markwon
import io.noties.markwon.image.ImagesPlugin
import io.noties.markwon.linkify.LinkifyPlugin
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.plugins.RxJavaPlugins
import okhttp3.OkHttpClient
import okhttp3.Response
import java.util.*

object Util {

    private const val NOTIFICATION_GROUP = "records"
    private var httpClient: OkHttpClient? = null

    fun formatRank(place: Int?): String {
        if(place == null)
            return "-"

        if (place / 10 % 10 == 1)
            return place.toString() + "th"

        return when (place % 10) {
            1 -> place.toString() + "st"
            2 -> place.toString() + "nd"
            3 -> place.toString() + "rd"
            else -> place.toString() + "th"
        }
    }

    fun showErrorToast(ctx: Context, msg: CharSequence) {
        Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()
    }

    fun showMsgToast(ctx: Context, msg: CharSequence) {
        Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()
    }

    fun getHTTPClient(context: Context): OkHttpClient? {

        if (httpClient != null)
            return httpClient

        // there is a lot of potential for lost network packets/application transitions to trigger an errer.
        // this will make sure the application does not crash because of this
        RxJavaPlugins.setErrorHandler { throwable -> Log.w("RXJAVA_UNDELIVERED", throwable) }

        try {

            httpClient = OkHttpClient.Builder()
                    // lower timeout--the middleware should respond pretty quickly
                    .readTimeout(15, TimeUnit.SECONDS)
                    .connectTimeout(3, TimeUnit.SECONDS)
                    // add a request header to identify app request
                    .addInterceptor { chain ->
                        val newReq = chain.request().newBuilder()
                                .addHeader("User-Agent", "SpeedrunAndroidMiddlewareClient/" + BuildConfig.VERSION_NAME + " (report@danb.email)")
                                .build()

                        chain.proceed(newReq)
                    }
                    .addInterceptor { chain ->
                        val res = chain.proceed(chain.request())

                        try {
                            val minVersion = res.header(Constants.MIN_VERSION_SERVER_HEADER)
                            if (!shownOodDialog && (minVersion != null && Integer.parseInt(minVersion) > BuildConfig.VERSION_CODE)) {
                                shownOodDialog = true
                                // open an upgrade warning dialog
                                AndroidSchedulers.mainThread().scheduleDirect {

                                    val appUpdateManager = AppUpdateManagerFactory.create(context)


                                    val appUpdateInfoTask = appUpdateManager.appUpdateInfo

                                    appUpdateInfoTask.addOnSuccessListener { appUpdateInfo ->
                                        if (appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE
                                                && appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)) {
                                            // Request an immediate update.
                                            appUpdateManager.startUpdateFlowForResult(
                                                    appUpdateInfo,
                                                    AppUpdateType.IMMEDIATE,
                                                    context as Activity,
                                                    1)
                                        } else {
                                            // cannot update the usual method for some reason
                                            AlertDialog.Builder(context, if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) android.R.style.Theme_DeviceDefault_Dialog_Alert
                                                    else AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                                                    .setIcon(R.drawable.baseline_info_white_24)
                                                    .setTitle(R.string.dialog_title_old_version)
                                                    .setMessage(R.string.dialog_msg_old_version)
                                                    .setPositiveButton(R.string.dialog_button_play_store) { _, _ ->
                                                        openPlayStorePage(context)
                                                    }
                                                    .setNeutralButton(R.string.ignore, null)
                                                    .show()
                                        }
                                    }
                                }
                            }
                        } catch (e: java.lang.Exception) {}

                        res
                    }
                    .build()

            return httpClient
        } catch (e: Exception) {
            return null
        }

    }

    // reads all the contents of a file to string
    @Throws(IOException::class)
    fun readToString(`in`: InputStream): String {
        val buf = BufferedReader(InputStreamReader(`in`))

        var line: String? = buf.readLine()
        val sb = StringBuilder()

        while (line != null) {
            sb.append(line).append("\n")
            line = buf.readLine()
        }

        return sb.toString()
    }

    fun postNotification(c: Context, intent: Intent, subjectId: String, title: String, message: String, largeIcon: Bitmap?) {
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TASK

        val pi = TaskStackBuilder.create(c).run {
            addNextIntentWithParentStack(intent)

            // Get the PendingIntent containing the entire back stack
            getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT)
        }

        val channelId = c.getString(R.string.default_notification_channel_id)
        val notificationBuilder = NotificationCompat.Builder(c, channelId)
                .setSmallIcon(R.drawable.speedrun_com_trophy)
                .setLargeIcon(largeIcon)
                .setContentTitle(title)
                .setContentText(message)
                .setAutoCancel(true)
                .setGroup(NOTIFICATION_GROUP)
                .setContentIntent(pi)

        val notificationManager = c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Since android Oreo notification channel is needed.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId,
                    c.getString(R.string.notification_channel_title),
                    NotificationManager.IMPORTANCE_DEFAULT)
            notificationManager.createNotificationChannel(channel)
        }

        notificationManager.notify(subjectId.hashCode(), notificationBuilder.build())

        Analytics.logDeliverNotification(c, subjectId)
    }

    fun showNewFeaturesDialog(ctx: Context) {
        val prefs = ctx.getSharedPreferences(Constants.SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE)

        val lastVersion = prefs.getString(Constants.PREF_LAST_APP_VERSION, null)

        if (lastVersion == BuildConfig.VERSION_NAME) {
            return
        }

        val db = AlertDialog.Builder(ctx, if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) android.R.style.Theme_DeviceDefault_Dialog_Alert
            else AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                .setTitle(R.string.dialog_title_release_notes)
                .setMessage(when(lastVersion) {
                    null -> R.string.dialog_msg_first_run
                    else -> R.string.dialog_msg_release_notes
                })
                .setNeutralButton(R.string.button_got_it, null)

        if(lastVersion != null) {
            db.setPositiveButton(R.string.dialog_button_rate_now) { _, _ ->
                openPlayStorePage(ctx)
            }
        }

        db.create().show()

        val edit = prefs.edit()
                .putString(Constants.PREF_LAST_APP_VERSION, BuildConfig.VERSION_NAME)

        if (prefs.getInt(Constants.PREF_FIRST_APP_CODE, -1) == -1)
            edit.putInt(Constants.PREF_FIRST_APP_CODE, BuildConfig.VERSION_CODE)

        edit.apply()
    }

    fun showInfoDialog(context: Context, text: String) {
        AlertDialog.Builder(context, if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) android.R.style.Theme_DeviceDefault_Dialog_Alert
            else AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                .setIcon(R.drawable.baseline_info_white_24)
                .setMessage(text)
                .setNeutralButton(android.R.string.ok, null)
                .show()
    }

    fun openInInternalBrowser(ctx: Context, uri: Uri): Intent {
        val intent = Intent(ctx, WebViewActivity::class.java)
        intent.putExtra(WebViewActivity.ARG_URL, uri.toString())

        return intent
    }

    fun openInBrowser(ctx: Context, uri: Uri): Intent {
        val intent = Intent(Intent.ACTION_VIEW)
        intent.setData(uri)
        return intent
    }

    private fun openPlayStorePage(context: Context, packageName: String? = null) {
        val pn = packageName ?: context.packageName

        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${pn}")))
        } catch (anfe: ActivityNotFoundException) {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=${pn}")))
        }
    }

    fun openShare(ctx: Context) {
        val intent = Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT,
                        ctx.getString(R.string.msg_share_app))



        ctx.startActivity(Intent.createChooser(intent, ctx.getString(R.string.msg_share_run_explain)))
    }

    fun createMarkwon(context: Context): Markwon {
        return Markwon.builder(context)
                .usePlugin(ImagesPlugin.create())
                .usePlugin(LinkifyPlugin.create())
                .build()
    }

    private var shownOodDialog = false
}
