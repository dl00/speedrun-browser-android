package danb.speedrunbrowser.utils

import android.app.AlertDialog
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.PorterDuff
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.util.Log
import android.view.Menu
import android.widget.Toast

import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.util.Random
import java.util.concurrent.TimeUnit

import androidx.core.app.NotificationCompat
import danb.speedrunbrowser.BuildConfig
import danb.speedrunbrowser.R
import io.reactivex.Single
import io.reactivex.functions.Consumer
import io.reactivex.plugins.RxJavaPlugins
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

object Util {

    private val NOTIFICATION_GROUP = "records"
    private var httpClient: OkHttpClient? = null

    fun showErrorToast(ctx: Context, msg: CharSequence) {
        Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()
    }

    fun showMsgToast(ctx: Context, msg: CharSequence) {
        Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()
    }

    fun getHTTPClient(): OkHttpClient? {

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

    fun postNotification(c: Context, intent: Intent, subjectId: String, title: String, message: String, largeIcon: Bitmap) {
        val requestId = System.currentTimeMillis().toInt()

        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TASK
        val pendingIntent = PendingIntent.getActivity(c, requestId, intent,
                PendingIntent.FLAG_ONE_SHOT)

        val channelId = c.getString(R.string.default_notification_channel_id)
        val notificationBuilder = NotificationCompat.Builder(c, channelId)
                .setSmallIcon(R.drawable.speedrun_com_trophy)
                .setLargeIcon(largeIcon)
                .setContentTitle(title)
                .setContentText(message)
                .setAutoCancel(true)
                .setGroup(Util.NOTIFICATION_GROUP)
                .setContentIntent(pendingIntent)

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

        val lastVersion = prefs.getString(Constants.PREF_LAST_APP_VERSION, "1.7")

        if (lastVersion == BuildConfig.VERSION_NAME) {
            return
        }

        val dialog = AlertDialog.Builder(ctx, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                .setTitle(R.string.dialog_title_release_notes)
                .setMessage(R.string.dialog_msg_release_notes)
                .setNeutralButton(R.string.button_got_it, null)
                .setPositiveButton(R.string.dialog_button_rate_now) { dialog, which ->
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + BuildConfig.APPLICATION_ID))
                    ctx.startActivity(intent)
                }
                .create()

        dialog.show()

        prefs.edit().putString(Constants.PREF_LAST_APP_VERSION, BuildConfig.VERSION_NAME).apply()
    }
}