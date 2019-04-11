package danb.speedrunbrowser.holders;

import android.content.Context;

import androidx.recyclerview.widget.RecyclerView;
import danb.speedrunbrowser.views.ProgressSpinnerView;

public class ProgressSpinnerViewHolder extends RecyclerView.ViewHolder {
    public ProgressSpinnerViewHolder(Context ctx) {
        super(new ProgressSpinnerView(ctx, null));
    }
}
