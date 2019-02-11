package danb.speedrunbrowser;

import android.annotation.SuppressLint;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.webkit.WebView;
import android.widget.FrameLayout;

import com.google.android.youtube.player.YouTubeInitializationResult;
import com.google.android.youtube.player.YouTubePlayer;
import com.google.android.youtube.player.YouTubePlayerSupportFragment;

import danb.speedrunbrowser.api.objects.MediaLink;
import danb.speedrunbrowser.api.objects.Run;
import danb.speedrunbrowser.utils.Constants;

public class RunDetailActivity extends AppCompatActivity implements YouTubePlayer.OnInitializedListener {
    private static final String TAG = RunDetailActivity.class.getSimpleName();

    public static final String EXTRA_RUN = "run";

    FrameLayout mVideoFrame;

    Run mRun;

    MediaLink mShownLink;

    YouTubePlayer mYoutubePlayer;
    WebView mTwitchWebView;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_run_detail);

        mVideoFrame = findViewById(R.id.videoFrame);

        Bundle args = getIntent().getExtras();

        mRun = (Run)args.getSerializable(EXTRA_RUN);

        if(!mRun.videos.links.isEmpty()) {

            mShownLink = mRun.videos.links.get(0);

            // find the first available youtube video we can recognize
            for(MediaLink m : mRun.videos.links) {

                Log.d(TAG, "Trying to find YT/Twitch video: " + m.uri);

                if(m.isYoutube()) {
                    Log.d(TAG, "Load YouTube video: " + m.uri);

                    YouTubePlayerSupportFragment ytFrag = new YouTubePlayerSupportFragment();

                    getSupportFragmentManager().beginTransaction()
                            .replace(R.id.videoFrame, ytFrag)
                            .commit();

                    ytFrag.initialize(Constants.YOUTUBE_DEVELOPER_KEY, this);

                    mShownLink = m;
                    break;
                }
                else if(m.isTwitch()) {
                    Log.d(TAG, "Load Twitch video: " + m.uri);

                    // start a web view and load custom content
                    mTwitchWebView = new WebView(this);
                    String videoId = m.getTwitchVideoID();

                    // configure the webview to support playing video
                    mTwitchWebView.getSettings().setAppCacheMaxSize(10 * 1024 * 1024); // 50MB
                    mTwitchWebView.getSettings().setJavaScriptEnabled(true);
                    mTwitchWebView.getSettings().setMediaPlaybackRequiresUserGesture(false);


                    mTwitchWebView.loadDataWithBaseURL("https://twitch.tv", String.format(Constants.TWITCH_EMBED_SNIPPET, videoId),
                            "text/html",
                            null,
                            null);

                    mVideoFrame.addView(mTwitchWebView);
                }
            }
        }
    }

    // youtube initialization
    @Override
    public void onInitializationSuccess(YouTubePlayer.Provider provider, YouTubePlayer youTubePlayer, boolean wasRestored) {
        if(!wasRestored) {
            Log.d(TAG, "Show video ID: " + mShownLink.getYoutubeVideoID());

            mYoutubePlayer = youTubePlayer;
            mYoutubePlayer.loadVideo(mShownLink.getYoutubeVideoID());
        }
    }

    @Override
    public void onInitializationFailure(YouTubePlayer.Provider provider, YouTubeInitializationResult youTubeInitializationResult) {

        Log.w(TAG, "Could not initialize youtube player: " + youTubeInitializationResult.name());

        // this might happen if the installed youtube app is out of date or there is some problem
        // with the person's phone
        if(youTubeInitializationResult.isUserRecoverableError()) {
            youTubeInitializationResult.getErrorDialog(this, 0).show();
        }
    }
}
