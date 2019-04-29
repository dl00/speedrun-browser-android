package danb.speedrunbrowser.views;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.ValueCallback;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import danb.speedrunbrowser.R;
import danb.speedrunbrowser.api.objects.MediaLink;
import danb.speedrunbrowser.utils.Constants;
import danb.speedrunbrowser.utils.Util;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Consumer;
import io.reactivex.internal.operators.flowable.FlowableInterval;
import io.reactivex.schedulers.Schedulers;

public class MultiVideoView extends FrameLayout {

    private static final String TAG = MultiVideoView.class.getSimpleName();


    private MediaLink mShownLink;

    // twitch
    private WebView mTwitchWebView;

    private int mSeekTime;

    private Listener mListener;

    private Disposable mPeriodicUpdate;

    public MultiVideoView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();

        if(mPeriodicUpdate != null)
            mPeriodicUpdate.dispose();
    }

    public boolean loadVideo(MediaLink ml) {
        if(mTwitchWebView != null) {
            throw new UnsupportedOperationException("multi video view cannot load more than one video");
        }

        Log.d(TAG, "Trying to find YT/Twitch video: " + ml.uri);

        if (ml.isYoutube()) {
            setVideoFrameYT(ml);
        } else if (ml.isTwitch()) {
            setVideoFrameTwitch(ml);
        } else {
            return false;
        }

        mShownLink = ml;

        return true;
    }

    private void setVideoFrameYT(MediaLink m) {

        String videoId = m.getYoutubeVideoID();

        Log.d(TAG, "Show YT video ID: " + videoId);

        // start a web view and load custom content
        mTwitchWebView = new WebView(getContext());

        // configure the webview to support playing video
        mTwitchWebView.getSettings().setAppCacheMaxSize(10 * 1024 * 1024); // 50MB
        mTwitchWebView.getSettings().setJavaScriptEnabled(true);
        mTwitchWebView.getSettings().setMediaPlaybackRequiresUserGesture(false);

        float scaleFactor = 1.0f;
        String pageContent;
        try {
            pageContent = String.format(Locale.US, Util.readToString(getClass().getResourceAsStream(Constants.YOUTUBE_EMBED_SNIPPET_FILE)), scaleFactor, videoId, mSeekTime);
        } catch (IOException e) {
            setVideoFrameError();
            return;
        }

        Log.d(TAG, pageContent);

        mTwitchWebView.getSettings().setUseWideViewPort(true);
        mTwitchWebView.getSettings().setLoadWithOverviewMode(true);

        mTwitchWebView.loadDataWithBaseURL("https://www.youtube.com", pageContent,
                "text/html",
                null,
                null);

        // due to restrictions of JS eval, we have to continuously pull seek time on an interval
        mPeriodicUpdate = new FlowableInterval(0, 5, TimeUnit.SECONDS, Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Consumer<Long>() {
                    @Override
                    public void accept(Long aLong) throws Exception {
                        updateTwitchSeekTime();
                    }
                }, new Consumer<Throwable>() {
                    @Override
                    public void accept(Throwable throwable) throws Exception {
                        Log.w(TAG, "Problem running background save interval: ", throwable);
                    }
                });

        removeAllViews();
        addView(mTwitchWebView);
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void setVideoFrameTwitch(MediaLink m) {
        String videoId = m.getTwitchVideoID();

        Log.d(TAG, "Show Twitch video ID: " + videoId);

        // start a web view and load custom content
        mTwitchWebView = new WebView(getContext());

        // configure the webview to support playing video
        mTwitchWebView.getSettings().setAppCacheMaxSize(10 * 1024 * 1024); // 50MB
        mTwitchWebView.getSettings().setJavaScriptEnabled(true);
        mTwitchWebView.getSettings().setMediaPlaybackRequiresUserGesture(false);

        float scaleFactor = 1.0f;
        String pageContent;
        try {
            pageContent = String.format(Locale.US, Util.readToString(getClass().getResourceAsStream(Constants.TWITCH_EMBED_SNIPPET_FILE)), scaleFactor, videoId, mSeekTime);
        } catch (IOException e) {
            setVideoFrameError();
            return;
        }

        Log.d(TAG, pageContent);

        mTwitchWebView.getSettings().setUseWideViewPort(true);
        mTwitchWebView.getSettings().setLoadWithOverviewMode(true);

        mTwitchWebView.loadDataWithBaseURL("https://twitch.tv", pageContent,
                "text/html",
                null,
                null);

        // due to restrictions of JS eval, we have to continuously pull seek time on an interval
        mPeriodicUpdate = new FlowableInterval(0, 5, TimeUnit.SECONDS, Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Consumer<Long>() {
                    @Override
                    public void accept(Long aLong) throws Exception {
                        updateTwitchSeekTime();
                    }
                }, new Consumer<Throwable>() {
                    @Override
                    public void accept(Throwable throwable) throws Exception {
                        Log.w(TAG, "Problem running background save interval: ", throwable);
                    }
                });

        removeAllViews();
        addView(mTwitchWebView);
    }

    private void updateTwitchSeekTime() {
        if(mTwitchWebView != null) {
            mTwitchWebView.evaluateJavascript("player.getCurrentTime()", new ValueCallback<String>() {
                @Override
                public void onReceiveValue(String value) {
                    try {
                        mSeekTime = (int)Math.floor(Float.parseFloat(value));
                    }
                    catch(NumberFormatException e) {
                        // ignored
                    }
                }
            });
        }
    }

    public void setVideoFrameOther(final MediaLink m) {
        LinearLayout ll = new LinearLayout(getContext());
        ll.setOrientation(LinearLayout.VERTICAL);
        ll.setGravity(Gravity.CENTER);
        ll.setBackground(new ColorDrawable(getResources().getColor(R.color.colorPrimaryDark)));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.CENTER;
        lp.topMargin = getResources().getDimensionPixelSize(R.dimen.half_fab_margin);

        TextView tv = new TextView(getContext());
        tv.setLayoutParams(lp);
        tv.setText(R.string.msg_cannot_play_video);

        ll.addView(tv);

        Button btn = new Button(getContext());
        btn.setLayoutParams(lp);
        btn.setText(R.string.btn_open_browser);
        btn.setBackground(new ColorDrawable(getResources().getColor(R.color.colorPrimary)));
        btn.setPadding(getResources().getDimensionPixelSize(R.dimen.half_fab_margin), 0, getResources().getDimensionPixelSize(R.dimen.half_fab_margin), 0);
        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(m.uri.toString()));
                getContext().startActivity(intent);
            }
        });

        ll.addView(btn);

        removeAllViews();
        addView(ll);
    }

    public void setVideoNotAvailable() {
        LinearLayout ll = new LinearLayout(getContext());
        ll.setOrientation(LinearLayout.VERTICAL);
        ll.setGravity(Gravity.CENTER);
        ll.setBackground(new ColorDrawable(getResources().getColor(R.color.colorPrimaryDark)));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.CENTER;
        lp.topMargin = getResources().getDimensionPixelSize(R.dimen.half_fab_margin);

        TextView tv = new TextView(getContext());
        tv.setLayoutParams(lp);
        tv.setText(R.string.msg_no_video);

        ll.addView(tv);
        addView(ll);
    }

    private void setVideoFrameError() {
        LinearLayout ll = new LinearLayout(getContext());
        ll.setOrientation(LinearLayout.VERTICAL);
        ll.setGravity(Gravity.CENTER);
        ll.setBackground(new ColorDrawable(getResources().getColor(R.color.colorBackgroundError)));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.CENTER;
        lp.topMargin = getResources().getDimensionPixelSize(R.dimen.half_fab_margin);

        TextView tv = new TextView(getContext());
        tv.setLayoutParams(lp);
        tv.setText(R.string.error_cannot_play_video);

        ll.addView(tv);

        removeAllViews();
        addView(ll);
    }

    public void setListener(Listener listener) {
        mListener = listener;
    }

    public boolean hasLoadedVideo() {
        return mShownLink != null;
    }

    public int getSeekTime() {
        return mSeekTime;
    }

    public void setSeekTime(int seekTo) {
        mSeekTime = seekTo;
    }

    public interface Listener {
        void onFullscreenToggleListener();
    }
}
