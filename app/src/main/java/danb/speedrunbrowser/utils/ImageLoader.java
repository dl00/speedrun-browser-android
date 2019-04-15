package danb.speedrunbrowser.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.text.TextUtils;
import android.util.Log;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

import io.reactivex.Single;
import io.reactivex.SingleEmitter;
import io.reactivex.SingleOnSubscribe;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class ImageLoader {

    private static final String TAG = ImageLoader.class.getSimpleName();

    private Context ctx;

    private File cacheDir;

    private OkHttpClient client;

    public ImageLoader(Context ctx) {
        cacheDir = ctx.getCacheDir();
        client = Util.getHTTPClient();

        cleanCache();
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
            // TODO properly throw this error
            //throw new IOException();
            Log.w(TAG, "failed to download image: " + res.body().string());


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

    public Single<Bitmap> loadImage(final URL url) {
        return Single.create(new SingleOnSubscribe<Bitmap>() {
            @Override
            public void subscribe(SingleEmitter<Bitmap> emitter) throws Exception {
                try {
                    // try to load from cache first
                    InputStream strm = loadFromCache(url);

                    if(strm == null) {
                        strm = downloadImage(url);
                    }

                    Bitmap b = BitmapFactory.decodeStream(strm); // will trigger an IOException to log the message otherwise
                    strm.close();

                    emitter.onSuccess(b);
                }
                catch(Exception e) {
                    Log.w(TAG, "Could not download image: ", e);
                }
            }
        })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread());
    }

    /// TODO Not implemented
    public void cleanCache() {

    }
}
