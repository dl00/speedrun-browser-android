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
        for(User player : entry.getRun().getPlayers()) {

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

        mRunTime.setText(entry.getRun().getTimes().getTime());
        mRunDate.setText(entry.getRun().getDate());
        mRank.setText(entry.getPlaceName());

        ImageLoader il = new ImageLoader(context);

        if(game.getAssets() != null) {
            if(entry.getPlace() == 1 && game.getAssets().getTrophy1st() != null) {
                disposables.add(il.loadImage(game.getAssets().getTrophy1st().getUri())
                    .subscribe(new ImageViewPlacerConsumer(mRankImg)));
            }
            if(entry.getPlace() == 2 && game.getAssets().getTrophy2nd() != null) {
                disposables.add(il.loadImage(game.getAssets().getTrophy2nd().getUri())
                        .subscribe(new ImageViewPlacerConsumer(mRankImg)));
            }
            if(entry.getPlace() == 3 && game.getAssets().getTrophy3rd() != null) {
                disposables.add(il.loadImage(game.getAssets().getTrophy3rd().getUri())
                        .subscribe(new ImageViewPlacerConsumer(mRankImg)));
            }
            if(entry.getPlace() == 4 && game.getAssets().getTrophy4th() != null) {
                disposables.add(il.loadImage(game.getAssets().getTrophy4th().getUri())
                        .subscribe(new ImageViewPlacerConsumer(mRankImg)));
            }
            else
                mRankImg.setImageDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
    }
}
