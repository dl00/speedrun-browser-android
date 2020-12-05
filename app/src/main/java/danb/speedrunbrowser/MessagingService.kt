package danb.speedrunbrowser

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

import java.io.IOException
import java.net.URL
import java.util.Objects

import danb.speedrunbrowser.api.PushNotificationData
import danb.speedrunbrowser.api.objects.User
import danb.speedrunbrowser.utils.Constants
import danb.speedrunbrowser.utils.ItemType
import danb.speedrunbrowser.utils.Util
import okhttp3.OkHttpClient
import okhttp3.Request

class MessagingService : FirebaseMessagingService() {

    private val buildVariant: String
        get() = if (BuildConfig.DEBUG) "debug" else "release"

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        Log.d(TAG, "From: " + remoteMessage.from!!)

        // Check if message contains a notification payload.
        if (remoteMessage.from != null) {
            val rawData = remoteMessage.data
            Log.d(TAG, "Message Data Body: $rawData")

            val data = PushNotificationData(rawData)

            if (remoteMessage.from!!.startsWith("/topics/" + buildVariant + "_player")) {
                makePlayerNotification(data)
            } else if (remoteMessage.from!!.startsWith("/topics/" + buildVariant + "_game")) {
                makeGameNotification(data)
            }

        }
    }

    private fun makePlayerNotification(data: PushNotificationData) {

        val players = data.newRun.run.players

        if (players!!.isEmpty()) {
            Log.w(TAG, "Received notification of run with no players! Cancelling notification.")
            return
        }

        // download feature image from the internet
        // TODO: Utilize in-app cache in the future
        var featureImg: Bitmap? = null
        try {

            val playerImgUrl = URL(String.format(Constants.AVATAR_IMG_LOCATION, players[0].names!!["international"]))

            Log.d(TAG, "Download player img from:$playerImgUrl")

            val req = Request.Builder()
                    .url(playerImgUrl)
                    .build()

            val res = Objects.requireNonNull<OkHttpClient>(Util.getHTTPClient(this)).newCall(req).execute()

            if (!res.isSuccessful)
                throw IOException()


            featureImg = BitmapFactory.decodeStream(res.body!!.byteStream())
        } catch (e: IOException) {
            Log.e(TAG, "Could not download player feature img:", e)
        }

        val title = getString(R.string.notify_title_player_record, User.printPlayerNames(players), data.game.resolvedName)

        var categoryAndLevelName = data.category.name
        if (data.level != null)
            categoryAndLevelName += " - " + data.level.name


        val msg = getString(R.string.notify_msg_player_record, data.newRun.placeName + " place",
                categoryAndLevelName, data.newRun.run.times!!.time)

        val intent = Intent(this, SpeedrunBrowserActivity::class.java)
        intent.putExtra(SpeedrunBrowserActivity.EXTRA_FRAGMENT_CLASSPATH, PlayerDetailFragment::class.java.canonicalName)
        intent.putExtra(PlayerDetailFragment.ARG_PLAYER_ID, players[0].id)

        Util.postNotification(this, intent, players[0].id, title, msg, featureImg)
    }

    private fun makeGameNotification(data: PushNotificationData) {
        val players = data.newRun.run.players

        if (players!!.isEmpty()) {
            Log.w(TAG, "Received notification of run with no players! Cancelling notification.")
            return
        }

        // download feature image from the internet
        // TODO: Utilize in-app cache in the future
        var featureImg: Bitmap? = null
        try {

            val gameCoverUrl = data.game.assets.coverLarge!!.uri

            Log.d(TAG, "Download player img from:$gameCoverUrl")

            val req = Request.Builder()
                    .url(gameCoverUrl)
                    .build()

            val res = Objects.requireNonNull<OkHttpClient>(Util.getHTTPClient(this)).newCall(req).execute()

            if (!res.isSuccessful)
                throw IOException()


            featureImg = BitmapFactory.decodeStream(res.body!!.byteStream())
        } catch (e: IOException) {
            Log.e(TAG, "Could not download player feature img:", e)
        }

        val title = getString(R.string.notify_title_game_record, data.game.resolvedName, User.printPlayerNames(players))

        var categoryAndLevelName = data.category.name

        if (data.level != null)
            categoryAndLevelName += " - " + data.level.name

        val msg = getString(R.string.notify_msg_game_record, data.newRun.placeName + " place",
                categoryAndLevelName, data.newRun.run.times!!.time)

        val intent = Intent(this, SpeedrunBrowserActivity::class.java)
        intent.putExtra(SpeedrunBrowserActivity.EXTRA_FRAGMENT_CLASSPATH, GameDetailFragment::class.java.canonicalName)
        intent.putExtra(GameDetailFragment.ARG_GAME_ID, data.game.id)

        Util.postNotification(this, intent, data.game.id, title, msg, featureImg)
    }

    companion object {
        private val TAG = MessagingService::class.java.simpleName
    }
}
