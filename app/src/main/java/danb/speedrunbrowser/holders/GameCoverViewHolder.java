package danb.speedrunbrowser.holders;

import android.content.Context;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;
import danb.speedrunbrowser.R;
import danb.speedrunbrowser.api.objects.Game;
import danb.speedrunbrowser.utils.ImageLoader;
import danb.speedrunbrowser.utils.ImageViewPlacerConsumer;
import io.reactivex.disposables.CompositeDisposable;

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

    public void apply(Context ctx, CompositeDisposable disposables, Game game) {
        mName.setText(game.names.get("international"));
        mDate.setText(game.releaseDate);
        mRunnersCount.setText("");

        if(game.assets.coverLarge != null)
            disposables.add(new ImageLoader(ctx).loadImage(game.assets.coverLarge.uri)
                .subscribe(new ImageViewPlacerConsumer(mCover)));
        else {}
        // TODO: In the extremely unlikely case there is no cover, might have to replace with dummy image
    }
}
