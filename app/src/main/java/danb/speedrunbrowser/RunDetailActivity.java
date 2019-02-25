package danb.speedrunbrowser;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Point;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.PersistableBundle;
import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Display;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.youtube.player.YouTubeInitializationResult;
import com.google.android.youtube.player.YouTubePlayer;
import com.google.android.youtube.player.YouTubePlayerSupportFragment;

import java.util.Locale;

import danb.speedrunbrowser.api.objects.Game;
import danb.speedrunbrowser.api.objects.MediaLink;
import danb.speedrunbrowser.api.objects.Run;
import danb.speedrunbrowser.utils.Constants;
import danb.speedrunbrowser.utils.DownloadImageTask;

public class RunDetailActivity extends AppCompatActivity implements YouTubePlayer.OnInitializedListener {
    private static final String TAG = RunDetailActivity.class.getSimpleName();

    public static final String EXTRA_RUN = "run";
    public static final String EXTRA_GAME = "game";

    public static final String SAVED_PLAYBACK_TIME = "playback_time";

    LinearLayout mRootView;

    /**
     * Game detail views
     */
    LinearLayout mGameInfoPane;
    TextView mGameName;
    TextView mReleaseDate;
    TextView mPlatformList;

    ImageView mCover;

    TextView mCategoryName;
    TextView mPlayerName;
    TextView mRunTime;

    /**
     * Video views
     */
    FrameLayout mVideoFrame;
    YouTubePlayer mYoutubePlayer;
    WebView mTwitchWebView;

    Game mGame;
    Run mRun;

    MediaLink mShownLink;

    long mStartPlaybackTime = 0;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_run_detail);

        mRootView = findViewById(R.id.contentLayout);
        mGameInfoPane = findViewById(R.id.gameInfoHead);
        mGameName = findViewById(R.id.txtGameName);
        mReleaseDate = findViewById(R.id.txtReleaseDate);
        mPlatformList = findViewById(R.id.txtPlatforms);
        mCover = findViewById(R.id.imgCover);
        mCategoryName = findViewById(R.id.txtCategoryName);
        mPlayerName = findViewById(R.id.txtPlayerName);
        mRunTime = findViewById(R.id.txtRunTime);
        mVideoFrame = findViewById(R.id.videoFrame);

        Bundle args = getIntent().getExtras();

        mGame = (Game)args.getSerializable(EXTRA_GAME);
        mRun = (Run)args.getSerializable(EXTRA_RUN);

        if(savedInstanceState != null) {
            mStartPlaybackTime = savedInstanceState.getLong(SAVED_PLAYBACK_TIME);
        }

        if(!mRun.videos.links.isEmpty()) {

            mShownLink = mRun.videos.links.get(0);

            // find the first available youtube video we can recognize
            for(final MediaLink m : mRun.videos.links) {

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
                    String videoId = m.getTwitchVideoID();

                    Log.d(TAG, "Show Twitch video ID: " + videoId);

                    // start a web view and load custom content
                    mTwitchWebView = new WebView(this);

                    // configure the webview to support playing video
                    mTwitchWebView.getSettings().setAppCacheMaxSize(10 * 1024 * 1024); // 50MB
                    mTwitchWebView.getSettings().setJavaScriptEnabled(true);
                    mTwitchWebView.getSettings().setMediaPlaybackRequiresUserGesture(false);

                    float scaleFactor = 1.0f;
                    String pageContent = String.format(Locale.US, Constants.TWITCH_EMBED_SNIPPET, scaleFactor, videoId);

                    Log.d(TAG, pageContent);

                    mTwitchWebView.getSettings().setUseWideViewPort(true);
                    mTwitchWebView.getSettings().setLoadWithOverviewMode(true);

                    mTwitchWebView.loadDataWithBaseURL("https://twitch.tv", pageContent,
                            "text/html",
                            null,
                            null);

                    mVideoFrame.addView(mTwitchWebView);
                }
                else {

                    LinearLayout ll = new LinearLayout(this);
                    ll.setOrientation(LinearLayout.VERTICAL);
                    ll.setGravity(Gravity.CENTER);
                    ll.setBackground(new ColorDrawable(getResources().getColor(R.color.colorPrimaryDark)));

                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    lp.gravity = Gravity.CENTER;
                    lp.topMargin = getResources().getDimensionPixelSize(R.dimen.half_fab_margin);

                    TextView tv = new TextView(this);
                    tv.setLayoutParams(lp);
                    tv.setText(R.string.msg_cannot_play_video);

                    ll.addView(tv);

                    Button btn = new Button(this);
                    btn.setLayoutParams(lp);
                    btn.setText(R.string.btn_open_browser);
                    btn.setBackground(new ColorDrawable(getResources().getColor(R.color.colorPrimary)));
                    btn.setPadding(getResources().getDimensionPixelSize(R.dimen.half_fab_margin), 0, getResources().getDimensionPixelSize(R.dimen.half_fab_margin), 0);
                    btn.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(m.uri.toString()));
                            startActivity(intent);
                        }
                    });

                    ll.addView(btn);

                    mVideoFrame.addView(ll);

                }
            }
        }

        onConfigurationChanged(getResources().getConfiguration());
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        if(newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
            mRootView.setBackground(new ColorDrawable(getResources().getColor(android.R.color.black)));

            // if the screen's aspect ratio is < 16:9, re-orient the text view so it still centers properly
            Display displ = getWindowManager().getDefaultDisplay();
            Point size = new Point();
            displ.getSize(size);

            if((float)size.x / size.y < 16.0f / 9)
                mRootView.setOrientation(LinearLayout.HORIZONTAL);

            // hide things
            mGameInfoPane.setVisibility(View.GONE);

        }
        else {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
            mRootView.setBackground(new ColorDrawable(getResources().getColor(R.color.colorPrimary)));

            // layout should always be vertical in this case
            mRootView.setOrientation(LinearLayout.VERTICAL);

            // show things
            mGameInfoPane.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState, PersistableBundle outPersistentState) {
        super.onSaveInstanceState(outState, outPersistentState);

        long playbackTime = 0;
        if(mYoutubePlayer != null) {
            mYoutubePlayer.getCurrentTimeMillis();
        }
        else if(mTwitchWebView != null) {
            // TODO
        }

        outState.putLong(SAVED_PLAYBACK_TIME, playbackTime);
    }

    // youtube initialization
    @Override
    public void onInitializationSuccess(YouTubePlayer.Provider provider, YouTubePlayer youTubePlayer, boolean wasRestored) {
        if(!wasRestored) {
            Log.d(TAG, "Show YT video ID: " + mShownLink.getYoutubeVideoID());

            mYoutubePlayer = youTubePlayer;
            mYoutubePlayer.setShowFullscreenButton(false);
            mYoutubePlayer.loadVideo(mShownLink.getYoutubeVideoID(), (int)mStartPlaybackTime);
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

    private void setViewData() {
        mGameName.setText(mGame.getName());
        mReleaseDate.setText(mGame.releaseDate);

        // we have to join the string manually because it is java 7
        StringBuilder sb = new StringBuilder();
        for (int i = 0;i < mGame.platforms.size();i++) {
            sb.append(mGame.platforms.get(i).getName());
            if(i < mGame.platforms.size() - 1)
                sb.append(", ");
        }

        mPlatformList.setText(sb.toString());

        if(mGame.assets.coverLarge != null)
            new DownloadImageTask(this, mCover).clear(false).execute(mGame.assets.coverLarge.uri);

        //if(mGame.assets.background != null && mBackground != null)
        //    new DownloadImageTask(this, mBackground).execute(mGame.assets.background.uri);
    }
}
