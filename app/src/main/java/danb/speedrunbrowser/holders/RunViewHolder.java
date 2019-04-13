package danb.speedrunbrowser.holders;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.flexbox.FlexboxLayout;

import androidx.recyclerview.widget.RecyclerView;
import danb.speedrunbrowser.R;
import danb.speedrunbrowser.api.objects.Game;
import danb.speedrunbrowser.api.objects.LeaderboardRunEntry;
import danb.speedrunbrowser.api.objects.User;
import danb.speedrunbrowser.utils.ImageLoader;
import danb.speedrunbrowser.utils.ImageViewPlacerConsumer;
import io.reactivex.disposables.CompositeDisposable;

public class RunViewHolder extends RecyclerView.ViewHolder {

    private FlexboxLayout mPlayerNames;
    private TextView mRunTime;
    private TextView mRunDate;
    private TextView mRank;

    private ImageView mRankImg;

    public RunViewHolder(View v) {
        super(v);

        mPlayerNames = v.findViewById(R.id.txtPlayerNames);
        mRunTime = v.findViewById(R.id.txtRunTime);
        mRunDate = v.findViewById(R.id.txtRunDate);
        mRank = v.findViewById(R.id.txtRank);
        mRankImg = v.findViewById(R.id.imgRank);
    }

    public void apply(Context context, CompositeDisposable disposables, Game game, LeaderboardRunEntry entry) {

        mPlayerNames.removeAllViews();
        boolean first = true;
        for(User player : entry.run.players) {

            TextView tv = new TextView(context);
            tv.setTextSize(16);
            player.applyTextView(tv);

            if(!first) {
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                lp.setMargins(context.getResources().getDimensionPixelSize(R.dimen.half_fab_margin), 0, 0, 0);
                tv.setLayoutParams(lp);
            }
            else
                first = false;

            mPlayerNames.addView(tv);
        }

        mRunTime.setText(entry.run.times.formatTime());
        mRunDate.setText(entry.run.date);
        mRank.setText(entry.getPlaceName());

        ImageLoader il = new ImageLoader(context);

        if(game.assets != null) {
            if(entry.place == 1 && game.assets.trophy1st != null) {
                disposables.add(il.loadImage(game.assets.trophy1st.uri)
                    .subscribe(new ImageViewPlacerConsumer(mRankImg)));
            }
            if(entry.place == 2 && game.assets.trophy2nd != null) {
                disposables.add(il.loadImage(game.assets.trophy2nd.uri)
                        .subscribe(new ImageViewPlacerConsumer(mRankImg)));
            }
            if(entry.place == 3 && game.assets.trophy3rd != null) {
                disposables.add(il.loadImage(game.assets.trophy3rd.uri)
                        .subscribe(new ImageViewPlacerConsumer(mRankImg)));
            }
            if(entry.place == 4 && game.assets.trophy4th != null) {
                disposables.add(il.loadImage(game.assets.trophy4th.uri)
                        .subscribe(new ImageViewPlacerConsumer(mRankImg)));
            }
            else
                mRankImg.setImageDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
    }
}
