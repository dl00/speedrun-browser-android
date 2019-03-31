package danb.speedrunbrowser;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.util.Map;

import androidx.core.app.NotificationCompat;
import danb.speedrunbrowser.api.PushNotificationData;

public class MessagingService extends FirebaseMessagingService {
    private static final String TAG = MessagingService.class.getSimpleName();

    public MessagingService() {
    }

    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);

        Log.d(TAG, "From: " + remoteMessage.getFrom());

        // Check if message contains a notification payload.
        if (remoteMessage.getData() != null) {
            Map<String, String> rawData = remoteMessage.getData();
            Log.d(TAG, "Message Data Body: " + rawData);

            PushNotificationData data = new PushNotificationData(rawData);

        }
    }

    private void makePlayerNotification(PushNotificationData data, Bitmap fetaureImg) {
        // download feature image from the internet

    }

    private void makeGameNotification(PushNotificationData data, Bitmap featureImg) {

    }
}
