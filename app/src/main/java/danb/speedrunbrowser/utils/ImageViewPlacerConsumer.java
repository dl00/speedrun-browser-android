package danb.speedrunbrowser.utils;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.view.View;
import android.widget.ImageView;

import io.reactivex.functions.Consumer;

public class ImageViewPlacerConsumer implements Consumer<Bitmap> {

    private ImageView view;

    public ImageViewPlacerConsumer(ImageView view) {
        this.view = view;
        view.setTag(this);
        view.setImageDrawable(new ColorDrawable(Color.TRANSPARENT));
    }

    @Override
    public void accept(Bitmap bitmap) throws Exception {

        if(view.getTag() == this) {
            view.setImageDrawable(new BitmapDrawable(bitmap));

            // fade in gracefully
            int animTime = view.getResources().getInteger(
                    android.R.integer.config_shortAnimTime);

            view.setAlpha(0.0f);
            view.setVisibility(View.VISIBLE);

            view.animate()
                    .alpha(1.0f)
                    .setDuration(animTime)
                    .setListener(null);
        }
    }
}
