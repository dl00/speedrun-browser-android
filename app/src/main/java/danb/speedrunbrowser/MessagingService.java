package danb.speedrunbrowser;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.io.ByteArrayInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import androidx.core.app.NotificationCompat;
import danb.speedrunbrowser.api.PushNotificationData;
import danb.speedrunbrowser.api.objects.User;
import danb.speedrunbrowser.utils.Constants;
import danb.speedrunbrowser.utils.Util;
import okhttp3.Request;
import okhttp3.Response;

public class MessagingService extends FirebaseMessagingService {
    private static final String TAG = MessagingService.class.getSimpleName();

    public MessagingService() {
    }

    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);

        Log.d(TAG, "From: " + remoteMessage.getFrom());

        // Check if message contains a notification payload.
        if (remoteMessage.getData() != null && remoteMessage.getFrom() != null) {
            Map<String, String> rawData = remoteMessage.getData();
            Log.d(TAG, "Message Data Body: " + rawData);

            PushNotificationData data = new PushNotificationData(rawData);

            if(remoteMessage.getFrom().startsWith("/topics/debug_player")) {
                makePlayerNotification(data);
            }

        }
    }

    private void makePlayerNotification(PushNotificationData data) {

        List<User> players = data.new_run.run.players;

        if(players.isEmpty()) {
            Log.w(TAG, "Received notification of run with no players! Cancelling notification.");
            return;
        }

        // download feature image from the internet
        // TODO: Utilize in-app cache in the future
        Bitmap featureImg = null;
        try {

            URL playerImgUrl = new URL(String.format(Constants.AVATAR_IMG_LOCATION, players.get(0).names.get("international")));

            Log.d(TAG, "Download player img from:" + playerImgUrl);

            Request req = new Request.Builder()
                    .url(playerImgUrl)
                    .build();

            Response res = Objects.requireNonNull(Util.getHTTPClient()).newCall(req).execute();

            if(!res.isSuccessful())
                throw new IOException();


            featureImg = BitmapFactory.decodeStream(res.body().byteStream());
        } catch(IOException e) {
            Log.e(TAG, "Could not download player feature img:", e);
        }

        String playerNames = players.get(0).names.get("international");

        if(players.size() == 2)
            playerNames += " and " + players.get(1).names.get("international");
        else if(players.size() >= 3) {
            for(int i = 1;i < players.size() - 1;i++)
                playerNames += ", " + players.get(i).names.get("international");

            playerNames += ", and " + players.get(players.size() - 1).names.get("international");
        }

        String title = getString(R.string.notify_title_player_record, playerNames, data.game.getName());

        String categoryAndLevelName = data.category.name;

        if(data.level != null)
            categoryAndLevelName += " - " + data.level.name;

        String msg = getString(R.string.notify_msg_player_record, data.new_run.getPlaceName() + " place",
                data.game.getName(), categoryAndLevelName,
                data.old_run != null ? data.old_run.run.times.formatTime() : "", data.new_run.run.times.formatTime());

        Intent intent = new Intent(this, PlayerDetailActivity.class);
        intent.putExtra(PlayerDetailActivity.ARG_PLAYER_ID, players.get(0).id);

        Util.postNotification(this, intent, title, msg, featureImg);
    }

    private void makeGameNotification(PushNotificationData data, Bitmap featureImg) {
        // TODO
    }
}