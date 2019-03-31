package danb.speedrunbrowser.utils;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.Menu;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

import androidx.core.app.NotificationCompat;
import danb.speedrunbrowser.R;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class Util {
    public static void showErrorToast(Context ctx, CharSequence msg) {
        Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show();
    }

    public static void showMsgToast(Context ctx, CharSequence msg) {
        Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show();
    }

    public static OkHttpClient getHTTPClient() {

        try {

            return new OkHttpClient.Builder()
                    // lower timeout--the middleware should respond pretty quickly
                    .readTimeout(15, TimeUnit.SECONDS)
                    .connectTimeout(3, TimeUnit.SECONDS)
                    // add a request header to identify app request
                    .addInterceptor(new Interceptor() {
                        @Override
                        public Response intercept(Chain chain) throws IOException {
                            Request newReq = chain.request().newBuilder()
                                    .addHeader("User-Agent", "SpeedrunAndroidMiddlewareClient (report@danb.email)")
                                    .build();

                            return chain.proceed(newReq);
                        }
                    })
                    .build();
        }
        catch(Exception e) {
            return null;
        }
    }

    // reads all the contents of a file to string
    public static String readToString(InputStream in) throws IOException {
        BufferedReader buf = new BufferedReader(new InputStreamReader(in));

        String line = buf.readLine();
        StringBuilder sb = new StringBuilder();

        while(line != null){
            sb.append(line).append("\n");
            line = buf.readLine();
        }

        return sb.toString();
    }

    public static void postNotification(Context c, Intent intent, String title, String message, Bitmap largeIcon) {
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(c, 0, intent,
                PendingIntent.FLAG_ONE_SHOT);

        String channelId = c.getString(R.string.default_notification_channel_id);
        NotificationCompat.Builder notificationBuilder =
                new NotificationCompat.Builder(c, channelId)
                        .setSmallIcon(R.drawable.speedrun_com_trophy)
                        .setLargeIcon(largeIcon)
                        .setContentTitle(title)
                        .setContentText(message)
                        .setAutoCancel(true)
                        .setContentIntent(pendingIntent);

        NotificationManager notificationManager =
                (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);

        // Since android Oreo notification channel is needed.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(channelId,
                    c.getString(R.string.notification_channel_title),
                    NotificationManager.IMPORTANCE_DEFAULT);
            notificationManager.createNotificationChannel(channel);
        }

        notificationManager.notify(0, notificationBuilder.build());
    }
}
