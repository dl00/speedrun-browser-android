package danb.speedrunbrowser.holders;

import android.content.Context;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import java.net.MalformedURLException;
import java.net.URL;

import androidx.recyclerview.widget.RecyclerView;
import danb.speedrunbrowser.GameListActivity;
import danb.speedrunbrowser.R;
import danb.speedrunbrowser.api.objects.Game;
import danb.speedrunbrowser.utils.DownloadImageTask;

public class GameCoverViewHolder extends RecyclerView.ViewHolder {
    private TextView mName;
    private TextView mDate;
    private TextView mRunnersCount;

    private ImageView mCover;

    public GameCoverViewHolder(View v) {
        super(v);

        mName = v.findViewById(R.id.txtGameName);
        mDate = v.findViewById(R.id.txtReleaseDate);
        mRunnersCount = v.findViewById(R.id.txtPlayerCount);
        mCover = v.findViewById(R.id.imgGameCover);
    }

    public void apply(Context ctx, Game game) {
        mName.setText(game.names.get("international"));
        mDate.setText(game.releaseDate);
        mRunnersCount.setText("");

        if(game.assets.coverLarge != null)
            new DownloadImageTask(ctx, mCover)
                    .execute(game.assets.coverLarge.uri);
        else {}
        // TODO: In the extremely unlikely case there is no cover, might have to replace with dummy image
    }
}
