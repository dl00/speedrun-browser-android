package danb.speedrunbrowser.holders;

import android.content.Context;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

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

    private TextView mGameName;
    private ImageView mPlayerImage;
    private ImageView mGameImage;

    public WatchRunViewHolder(View v) {
        super(v);

        mLeaderboardHolder = new RunViewHolder(v);

        mGameName = v.findViewById(R.id.txtGameName);
        mPlayerImage = v.findViewById(R.id.imgPlayerIcon);
        mGameImage = v.findViewById(R.id.imgGameIcon);
    }

    public void apply(Context context, Game game, LeaderboardRunEntry entry) {

        mLeaderboardHolder.apply(context, game, entry);

        if(entry.run.game.names != null)
            mGameName.setText(entry.run.game.names.get("international"));

        if(!entry.run.players.isEmpty() && entry.run.players.get(0).names != null && entry.run.players.get(0).names.get("international") != null) {
            mPlayerImage.setVisibility(View.VISIBLE);
            try {
                new DownloadImageTask(context, mPlayerImage).execute(new URL(String.format(Constants.AVATAR_IMG_LOCATION, entry.run.players.get(0).names.get("international"))));
            }
            catch(MalformedURLException e) {
                Log.w(TAG, "Could not generate player image URL:", e);
            }
        }
        else
            mPlayerImage.setVisibility(View.GONE);

        if(entry.run.game != null && entry.run.game.assets != null && entry.run.game.assets.coverLarge != null) {
            mGameImage.setVisibility(View.VISIBLE);
            new DownloadImageTask(context, mGameImage).execute(entry.run.game.assets.coverLarge.uri);
        }
        else
            mGameImage.setVisibility(View.GONE);
    }
}
