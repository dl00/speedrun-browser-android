package danb.speedrunbrowser.models;

import android.content.Context;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;

import java.net.MalformedURLException;
import java.net.URL;

import androidx.recyclerview.widget.RecyclerView;
import danb.speedrunbrowser.R;
import danb.speedrunbrowser.api.objects.Game;
import danb.speedrunbrowser.api.objects.LeaderboardRunEntry;
import danb.speedrunbrowser.utils.Constants;
import danb.speedrunbrowser.utils.DownloadImageTask;

public class WatchRunViewHolder extends RecyclerView.ViewHolder {
    private static final String TAG = WatchRunViewHolder.class.getSimpleName();

    private RunViewHolder mLeaderboardHolder;

    private ImageView mPlayerImage;
    private ImageView mGameImage;

    public WatchRunViewHolder(View v) {
        super(v);

        mLeaderboardHolder = new RunViewHolder(v);

        mPlayerImage = v.findViewById(R.id.imgPlayerIcon);
        mGameImage = v.findViewById(R.id.imgGameIcon);
    }

    public void apply(Context context, Game game, LeaderboardRunEntry entry) {

        mLeaderboardHolder.apply(context, game, entry);



        if(!entry.run.players.isEmpty() && entry.run.players.get(0).name != null) {
            mPlayerImage.setVisibility(View.VISIBLE);
            try {
                new DownloadImageTask(context, mPlayerImage).execute(new URL(String.format(Constants.AVATAR_IMG_LOCATION, entry.run.players.get(0).name)));
            }
            catch(MalformedURLException e) {
                Log.w(TAG, "Could not generate player image URL:", e);
            }
        }
        else
            mPlayerImage.setVisibility(View.GONE);

        if(entry.run.game != null && entry.run.game.assets.coverLarge != null) {
            mGameImage.setVisibility(View.VISIBLE);
            try {
                new DownloadImageTask(context, mGameImage).execute(new URL(String.format(Constants.AVATAR_IMG_LOCATION, entry.run.game.assets.coverLarge.uri)));
            }
            catch(MalformedURLException e) {
                Log.w(TAG, "Could not use game cover asset URL is malformed:", e);
            }
        }
        else
            mGameImage.setVisibility(View.GONE);
    }
}
