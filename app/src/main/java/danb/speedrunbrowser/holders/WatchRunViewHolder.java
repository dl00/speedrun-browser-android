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

        if(entry.run.game.names != null) {
            String gameAndCategoryText = entry.run.game.names.get("international");

            gameAndCategoryText += " \u2022 " + entry.run.category.name;

            if(entry.run.level != null)
                gameAndCategoryText += " \u2022 " + entry.run.level.name;

            mGameName.setText(gameAndCategoryText);
        }

        if(entry.run.game != null && entry.run.game.assets != null && entry.run.game.assets.coverLarge != null) {
            mGameImage.setVisibility(View.VISIBLE);
            disposables.add(new ImageLoader(context).loadImage(entry.run.game.assets.coverLarge.uri)
                    .subscribe(new ImageViewPlacerConsumer(mGameImage)));
        }
        else
            mGameImage.setVisibility(View.GONE);
    }
}
