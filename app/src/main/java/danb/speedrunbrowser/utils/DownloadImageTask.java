package danb.speedrunbrowser.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.os.AsyncTask;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.net.URL;
import java.util.HashMap;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Created by daniel on 12/13/16.
 * Copyright 13013 Inc. All Rights Reserved.
 */

public class DownloadImageTask extends AsyncTask<URL, Integer, Bitmap> {

    private static final String TAG = DownloadImageTask.class.getSimpleName();

    private static final OkHttpClient client = Util.getHTTPClient();

    private static final HashMap<View, DownloadImageTask> pendingTasks = new HashMap<>();

    private final File cacheDir;

    // TODO: Remove application context
    private View view;

    private boolean doClear = true;

    private OnCompleteListener listener;

    public DownloadImageTask(Context ctx) {
        view = null;
        cacheDir = ctx.getCacheDir();
    }

    public DownloadImageTask(Context ctx, View into) {
        view = into;
        cacheDir = ctx.getCacheDir();
    }

    protected void onPreExecute() {
        if(doClear) {
            if(view instanceof ImageView)
                ((ImageView)view).setImageDrawable(
                    new ColorDrawable());
            else
                view.setBackground(null);
        }

        synchronized(pendingTasks) {
            if(pendingTasks.containsKey(view)) {
                pendingTasks.get(view).cancel(true);
            }

            pendingTasks.put(view, this);
        }
    }

    private File getCacheFile(URL url) {
        String fname = TextUtils.join(
                "~",
                new String[]{url.getHost(), String.valueOf(url.getPort()), url.getPath().replaceAll("/", "_")}
        );

        // save a copy to cache before we return
        return new File(cacheDir, fname);
    }

    private FileInputStream loadFromCache(URL url) {
        FileInputStream fi = null;
        try {
            File cf = getCacheFile(url);

            fi = new FileInputStream(getCacheFile(url));

            // if the tempfile was not written or closed, it may be empty. in which case return null
            if(fi.getChannel().size() == 0) {
                Log.w(TAG, "Found empty cache file: " + cf.toString());
                return null;
            }

            Log.d(TAG, "Had Cached Image Asset: " + url.toString());
        }
        catch(IOException _e) {}

        return fi;
    }

    private InputStream downloadImage(URL url) throws IOException {
        Log.d(TAG, "Download Image Asset: " + url.toString());

        Request req = new Request.Builder()
                .url(url)
                .build();

        Response res = client.newCall(req).execute();

        if(!res.isSuccessful())
            throw new IOException();


        byte[] data = res.body().bytes();

        FileOutputStream fo = new FileOutputStream(getCacheFile(url));

        try {
            fo.write(data);
            fo.close();
        } catch(Exception e) {
            // if something happens, try to delete the cache file. otherwise it could load invalid next time
            Log.w(TAG, "Could not save cache file for downloaded image", e);
            getCacheFile(url).delete();
        }

        return new ByteArrayInputStream(data);
    }

    @Override
    protected Bitmap doInBackground(URL... url) {
        try {

            // try to load from cache first
            InputStream strm = loadFromCache(url[0]);

            if(strm == null) {
                strm = downloadImage(url[0]);
            }

            Bitmap b = BitmapFactory.decodeStream(strm); // will trigger an IOException to log the message otherwise
            strm.close();

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
    protected void onPostExecute(final Bitmap result) {

        synchronized(pendingTasks) {
            if(pendingTasks.get(view) != this)
                return;

            pendingTasks.remove(view);
        }

        Log.v(TAG, "Placing image from background" + result);
        if(view != null) {
            if(view instanceof ImageView) {
                ((ImageView) view).setImageBitmap(result);

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
            else {

                View.OnLayoutChangeListener l = new View.OnLayoutChangeListener() {
                    @Override
                    public void onLayoutChange(View view, int i, int i1, int i2, int i3, int i4, int i5, int i6, int i7) {
                        // scale it to fit the dimensions of this view, preserving aspect ratio

                        Bitmap scaledResult = result;

                        double factor = Math.min(
                                (double)view.getWidth() / result.getWidth(),
                                (double)view.getHeight() / result.getHeight());

                        //Log.d(TAG, "Scaling Background Bitmap to fit: " + factor);
                        /*if(factor != 1) {
                            Log.d(TAG, "Do scale");
                            scaledResult = Bitmap.createScaledBitmap(result, (int) Math.ceil(result.getWidth() / factor), (int) Math.ceil(result.getHeight() / factor), false);
                        }*/

                        BitmapDrawable d = new BitmapDrawable(scaledResult);

                        d.setGravity(Gravity.CENTER);

                        view.setBackground(d);
                    }
                };

                l.onLayoutChange(view, 0,0,0,0,0,0,0,0);
                view.addOnLayoutChangeListener(l);
            }

            view.postInvalidate();

            if(listener != null)
                listener.onComplete(view);
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

    public DownloadImageTask listener(OnCompleteListener l) {
        listener = l;
        return this;
    }

    public static int getPendingCount() {
        return pendingTasks.size();
    }

    public interface OnCompleteListener {
        void onComplete(View v);
    }
}