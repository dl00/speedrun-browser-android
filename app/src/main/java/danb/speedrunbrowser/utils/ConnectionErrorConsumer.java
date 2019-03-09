package danb.speedrunbrowser.utils;

import android.content.Context;
import android.util.Log;

import danb.speedrunbrowser.R;

public class ConnectionErrorConsumer implements io.reactivex.functions.Consumer<Throwable> {
    private static final String TAG = ConnectionErrorConsumer.class.getSimpleName();

    Context ctx;

    public ConnectionErrorConsumer(Context ctx) {
        this.ctx = ctx;
    }

    @Override
    public void accept(Throwable throwable) {

        Log.w(TAG, "Could not download dashboard:", throwable);
        Util.showErrorToast(ctx, ctx.getString(R.string.error_could_not_connect));
    }
}
