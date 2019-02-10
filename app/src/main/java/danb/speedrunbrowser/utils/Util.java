package danb.speedrunbrowser.utils;

import android.content.Context;
import android.widget.Toast;

public class Util {
    public static void showErrorToast(Context ctx, CharSequence msg) {
        Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show();
    }
}
