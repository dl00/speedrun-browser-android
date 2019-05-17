package danb.speedrunbrowser.holders;

import android.content.Context;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;
import danb.speedrunbrowser.R;
import danb.speedrunbrowser.api.objects.Game;
import danb.speedrunbrowser.api.objects.LeaderboardRunEntry;
import danb.speedrunbrowser.utils.ImageLoader;
import danb.speedrunbrowser.utils.ImageViewPlacerConsumer;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;

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
        mGameImage = v.findViewById(R.id.imgGameIcon);
    }

    public void apply(Context context, CompositeDisposable disposables, Game game, LeaderboardRunEntry entry) {

        mLeaderboardHolder.apply(context, disposables, game, entry);

        if(entry.getRun().getGame().getNames() != null) {
            String gameAndCategoryText = entry.getRun().getGame().getNames().get("international");

            gameAndCategoryText += " \u2022 " + entry.getRun().getCategory().getName();

            if(entry.getRun().getLevel() != null)
                gameAndCategoryText += " \u2022 " + entry.getRun().getLevel().getName();

            mGameName.setText(gameAndCategoryText);
        }

        if(entry.getRun().getGame() != null && entry.getRun().getGame().getAssets() != null && entry.getRun().getGame().getAssets().getCoverLarge() != null) {
            mGameImage.setVisibility(View.VISIBLE);
            disposables.add(new ImageLoader(context).loadImage(entry.getRun().getGame().getAssets().getCoverLarge().getUri())
                    .subscribe(new ImageViewPlacerConsumer(mGameImage)));
        }
        else
            mGameImage.setVisibility(View.GONE);
    }
}
