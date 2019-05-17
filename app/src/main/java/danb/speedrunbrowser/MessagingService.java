package danb.speedrunbrowser;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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

            if(remoteMessage.getFrom().startsWith("/topics/" + getBuildVariant() + "_player")) {
                makePlayerNotification(data);
            }
            else if(remoteMessage.getFrom().startsWith("/topics/" + getBuildVariant() + "_game")) {
                makeGameNotification(data);
            }

        }
    }

    private void makePlayerNotification(PushNotificationData data) {

        List<User> players = data.getNew_run().getRun().getPlayers();

        if(players.isEmpty()) {
            Log.w(TAG, "Received notification of run with no players! Cancelling notification.");
            return;
        }

        // download feature image from the internet
        // TODO: Utilize in-app cache in the future
        Bitmap featureImg = null;
        try {

            URL playerImgUrl = new URL(String.format(Constants.AVATAR_IMG_LOCATION, players.get(0).getNames().get("international")));

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

        String title = getString(R.string.notify_title_player_record, User.Companion.printPlayerNames(players), data.getGame().getResolvedName());

        String categoryAndLevelName = data.getCategory().getName();
        if(data.getLevel() != null)
            categoryAndLevelName += " - " + data.getLevel().getName();

        String msg = getString(R.string.notify_msg_player_record, data.getNew_run().getPlaceName() + " place",
                data.getGame().getResolvedName(), categoryAndLevelName,
                data.getOld_run() != null ? data.getOld_run().getRun().getTimes().getTime() : "", data.getNew_run().getRun().getTimes().getTime());

        Intent intent = new Intent(this, ItemDetailActivity.class);
        intent.putExtra(ItemDetailActivity.EXTRA_ITEM_TYPE, ItemListFragment.ItemType.PLAYERS);
        intent.putExtra(PlayerDetailFragment.ARG_PLAYER_ID, players.get(0).getId());

        Util.postNotification(this, intent, players.get(0).getId(), title, msg, featureImg);
    }

    private void makeGameNotification(PushNotificationData data) {
        List<User> players = data.getNew_run().getRun().getPlayers();

        if(players.isEmpty()) {
            Log.w(TAG, "Received notification of run with no players! Cancelling notification.");
            return;
        }

        // download feature image from the internet
        // TODO: Utilize in-app cache in the future
        Bitmap featureImg = null;
        try {

            URL gameCoverUrl = data.getGame().getAssets().getCoverLarge().getUri();

            Log.d(TAG, "Download player img from:" + gameCoverUrl);

            Request req = new Request.Builder()
                    .url(gameCoverUrl)
                    .build();

            Response res = Objects.requireNonNull(Util.getHTTPClient()).newCall(req).execute();

            if(!res.isSuccessful())
                throw new IOException();


            featureImg = BitmapFactory.decodeStream(res.body().byteStream());
        } catch(IOException e) {
            Log.e(TAG, "Could not download player feature img:", e);
        }

        String title = getString(R.string.notify_title_game_record, data.getGame().getResolvedName(), User.Companion.printPlayerNames(players));

        String categoryAndLevelName = data.getCategory().getName();

        if(data.getLevel() != null)
            categoryAndLevelName += " - " + data.getLevel().getName();

        String msg = getString(R.string.notify_msg_game_record, data.getNew_run().getPlaceName() + " place",
                data.getGame().getResolvedName(), categoryAndLevelName,
                data.getOld_run() != null ? data.getOld_run().getRun().getTimes().getTime() : "", data.getNew_run().getRun().getTimes().getTime());

        Intent intent = new Intent(this, ItemDetailActivity.class);
        intent.putExtra(ItemDetailActivity.EXTRA_ITEM_TYPE, ItemListFragment.ItemType.GAMES);
        intent.putExtra(GameDetailFragment.ARG_GAME_ID, data.getGame().getId());

        Util.postNotification(this, intent, data.getGame().getId(), title, msg, featureImg);
    }

    private String getBuildVariant() {
        return BuildConfig.DEBUG ? "debug" : "release";
    }
}
