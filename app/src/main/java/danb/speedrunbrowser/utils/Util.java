package danb.speedrunbrowser.utils;

import android.content.Context;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;

import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class Util {
    public static void showErrorToast(Context ctx, CharSequence msg) {
        Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show();
    }

    public static OkHttpClient getHTTPClient() {

        try {

            return new OkHttpClient.Builder()
                    // lower timeout--the middleware should respond pretty quickly
                    .readTimeout(15, TimeUnit.SECONDS)
                    .connectTimeout(3, TimeUnit.SECONDS)
                    // add a request header to identify app request
                    .addInterceptor(new Interceptor() {
                        @Override
                        public Response intercept(Chain chain) throws IOException {
                            Request newReq = chain.request().newBuilder()
                                    .addHeader("User-Agent", "SpeedrunAndroidMiddlewareClient (report@danb.email)")
                                    .build();

                            return chain.proceed(newReq);
                        }
                    })
                    .build();
        }
        catch(Exception e) {
            return null;
        }
    }

    // reads all the contents of a file to string
    public static String readToString(InputStream in) throws IOException {
        BufferedReader buf = new BufferedReader(new InputStreamReader(in));

        String line = buf.readLine();
        StringBuilder sb = new StringBuilder();

        while(line != null){
            sb.append(line).append("\n");
            line = buf.readLine();
        }

        return sb.toString();
    }
}
