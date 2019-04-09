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
import danb.speedrunbrowser.api.objects.User;
import danb.speedrunbrowser.utils.Constants;
import danb.speedrunbrowser.utils.DownloadImageTask;

public class PlayerViewHolder extends RecyclerView.ViewHolder {
    private static final String TAG = WatchRunViewHolder.class.getSimpleName();

    private ImageView mSubscribedIndicator;
    private ImageView mPlayerImage;
    private TextView mPlayerName;

    public PlayerViewHolder(View v) {
        super(v);

        mSubscribedIndicator = v.findViewById(R.id.imgSubscribedIndicator);
        mPlayerImage = v.findViewById(R.id.imgPlayerIcon);
        mPlayerName = v.findViewById(R.id.txtPlayerName);
    }

    public void apply(Context context, User user, boolean subscribed) {

        mSubscribedIndicator.setVisibility(subscribed ? View.VISIBLE : View.INVISIBLE);

        if(user.names != null && user.names.get("international") != null) {
            mPlayerImage.setVisibility(View.VISIBLE);
            try {
                new DownloadImageTask(context, mPlayerImage).execute(new URL(String.format(Constants.AVATAR_IMG_LOCATION, user.names.get("international"))));
            }
            catch(MalformedURLException e) {
                Log.w(TAG, "Could not generate player image URL:", e);
            }
        }
        else
            mPlayerImage.setVisibility(View.GONE);

        user.applyTextView(mPlayerName);
    }
}
