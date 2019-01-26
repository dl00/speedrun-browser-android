package danb.speedrunbrowser.utils;

import android.app.Application;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.ColorDrawable;
import android.os.AsyncTask;
import android.util.Log;
import android.widget.ImageView;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.URL;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Created by daniel on 12/13/16.
 * Copyright 13013 Inc. All Rights Reserved.
 */

public class DownloadImageTask extends AsyncTask<URL, Integer, Bitmap> {

    private static final String TAG = DownloadImageTask.class.getSimpleName();

    private static final OkHttpClient client = new OkHttpClient();

    ImageView view;

    boolean doClear = true;

    public DownloadImageTask() {
        view = null;
    }

    public DownloadImageTask(ImageView into) {
        view = into;
    }

    protected void onPreExecute() {
        if(doClear) {
            view.setImageDrawable(
                    new ColorDrawable());
        }
    }

    @Override
    protected Bitmap doInBackground(URL... url) {
        try {

            Request req = new Request.Builder()
                    .url(url[0])
                    .build();

            Response res = client.newCall(req).execute();

            if(!res.isSuccessful())
                throw new IOException();


            ResponseBody body = res.body();

            Bitmap b = BitmapFactory.decodeStream(body.byteStream()); // will trigger an IOException to log the message otherwise

            res.body().close();
            return b;
        }
        catch(InterruptedIOException e) {
            return null; // nothing to do when this happens
        }
        catch(IOException e) {
            Log.e(TAG, "Failed to download bitmap at URL: ", e);
            return null;
        }
        catch(OutOfMemoryError e) {
            Log.e(TAG, "Out of Memory occured with map tiles! Shrink tile size needed.");
            return null;
        }
    }

    @Override
    protected void onPostExecute(Bitmap result) {
        Log.v(TAG, "Placing image from background" + result);
        if(view != null) {
            view.setImageBitmap(result);
            //view.invalidate();
            view.postInvalidate();
        }
    }

    public DownloadImageTask into(ImageView v) {
        view = v;
        return this;
    }

    public DownloadImageTask clear(boolean c) {
        doClear = c;
        return this;
    }
}