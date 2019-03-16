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
import android.webkit.ValueCallback;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import com.google.android.flexbox.FlexboxLayout;
import com.google.android.youtube.player.YouTubeInitializationResult;
import com.google.android.youtube.player.YouTubePlayer;
import com.google.android.youtube.player.YouTubePlayerSupportFragment;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Observable;
import java.util.concurrent.TimeUnit;

import danb.speedrunbrowser.api.SpeedrunAPI;
import danb.speedrunbrowser.api.objects.Category;
import danb.speedrunbrowser.api.objects.Game;
import danb.speedrunbrowser.api.objects.Level;
import danb.speedrunbrowser.api.objects.MediaLink;
import danb.speedrunbrowser.api.objects.Run;
import danb.speedrunbrowser.api.objects.User;
import danb.speedrunbrowser.utils.AppDatabase;
import danb.speedrunbrowser.utils.Constants;
import danb.speedrunbrowser.utils.DownloadImageTask;
import danb.speedrunbrowser.utils.NoopConsumer;
import danb.speedrunbrowser.utils.Util;
import danb.speedrunbrowser.views.ProgressSpinnerView;
import io.reactivex.Scheduler;
import io.reactivex.Single;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Action;
import io.reactivex.functions.Consumer;
import io.reactivex.internal.operators.flowable.FlowableInterval;
import io.reactivex.schedulers.Schedulers;

public class RunDetailActivity extends AppCompatActivity implements YouTubePlayer.OnInitializedListener {
    private static final String TAG = RunDetailActivity.class.getSimpleName();

    public static final String EXTRA_GAME = "game";
    public static final String EXTRA_CATEGORY = "category";
    public static final String EXTRA_LEVEL = "level";
    public static final String EXTRA_RUN = "run";

    public static final String EXTRA_RUN_ID = "runId";

    public static final String SAVED_PLAYBACK_TIME = "playback_time";

    /// how often to save the current watch position/time to the watch history db
    private static final int BACKGROUND_SEEK_SAVE_START = 15;
    private static final int BACKGROUND_SEEK_SAVE_PERIOD = 30;

    LinearLayout mRootView;

    CompositeDisposable mDisposables = new CompositeDisposable();

    Disposable mDisposableBackgroundSaveInterval = null;

    /**
     * Game detail views
     */
    ProgressSpinnerView mSpinner;
    LinearLayout mGameInfoPane;
    LinearLayout mRunFooterPane;
    TextView mGameName;
    TextView mReleaseDate;
    TextView mPlatformList;

    ImageView mCover;

    TextView mCategoryName;
    FlexboxLayout mPlayerNames;
    TextView mRunTime;

    TextView mRunComment;

    ListView mRunSplits;
    TextView mRunEmptySplits;

    /**
     * Video views
     */
    FrameLayout mVideoFrame;
    YouTubePlayer mYoutubePlayer;
    WebView mTwitchWebView;

    AppDatabase mDB;

    Game mGame;
    Category mCategory;
    Level mLevel;
    Run mRun;

    MediaLink mShownLink;

    long mSeekPos = 0;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_run_detail);

        mDB = AppDatabase.make(this);

        mRootView = findViewById(R.id.contentLayout);
        mSpinner = findViewById(R.id.spinner);
        mGameInfoPane = findViewById(R.id.gameInfoHead);
        mRunFooterPane = findViewById(R.id.runFooter);
        mGameName = findViewById(R.id.txtGameName);
        mReleaseDate = findViewById(R.id.txtReleaseDate);
        mPlatformList = findViewById(R.id.txtPlatforms);
        mCover = findViewById(R.id.imgCover);
        mCategoryName = findViewById(R.id.txtCategoryName);
        mPlayerNames = findViewById(R.id.txtPlayerNames);
        mRunTime = findViewById(R.id.txtRunTime);
        mVideoFrame = findViewById(R.id.videoFrame);

        mRunComment = findViewById(R.id.txtRunComment);

        mRunSplits = findViewById(R.id.runSplitsList);
        mRunEmptySplits = findViewById(R.id.emptySplits);

        Bundle args = getIntent().getExtras();

        if(args.getString(EXTRA_RUN_ID) != null) {
            loadRun(args.getString(EXTRA_RUN_ID));
        }
        else if(getIntent().getData() != null) {
            Intent appLinkIntent = getIntent();
            Uri appLinkData = appLinkIntent.getData();

            List<String> segments = Objects.requireNonNull(appLinkData).getPathSegments();

            if(segments.size() >= 3) {
                final String runId = segments.get(2);

                loadRun(runId);
            }
            else {
                Util.showErrorToast(this, getString(R.string.error_invalod_url, appLinkData));
            }
        }
        else {

            mGame = (Game) args.getSerializable(EXTRA_GAME);
            mCategory = (Category) args.getSerializable(EXTRA_CATEGORY);
            mLevel = (Level) args.getSerializable(EXTRA_LEVEL);
            mRun = (Run) args.getSerializable(EXTRA_RUN);

            onDataReady();
        }
    }

    private void loadRun(final String runId) {
        Log.d(TAG, "Download runId: " + runId);
        mDisposables.add(SpeedrunAPI.make().getRun(runId)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(new Consumer<SpeedrunAPI.APIResponse<Run>>() {
                @Override
                public void accept(SpeedrunAPI.APIResponse<Run> gameAPIResponse) throws Exception {

                    if (gameAPIResponse.data == null) {
                        // game was not able to be found for some reason?
                        Util.showErrorToast(RunDetailActivity.this, getString(R.string.error_missing_game, runId));
                        return;
                    }

                    mRun = gameAPIResponse.data;
                    mGame = mRun.game;
                    mCategory = mRun.category;
                    mLevel = mRun.level;

                    onDataReady();
                }
            }, new Consumer<Throwable>() {
                @Override
                public void accept(Throwable throwable) throws Exception {

                    Log.e(TAG, "Could not download game data:", throwable);

                    Util.showErrorToast(RunDetailActivity.this, getString(R.string.error_missing_run, runId));
                }
            }));
    }

    @Override
    protected void onResume() {
        super.onResume();


        // set an interval to record the watch time
        mDisposableBackgroundSaveInterval = new FlowableInterval(BACKGROUND_SEEK_SAVE_START, BACKGROUND_SEEK_SAVE_PERIOD, TimeUnit.SECONDS, Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(new Consumer<Long>() {
                @Override
                public void accept(Long aLong) throws Exception {
                    recordStartPlaybackTime();
                }
            });
    }

    @Override
    protected void onPause() {
        super.onPause();

        mDisposableBackgroundSaveInterval.dispose();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mDisposables.dispose();
        mDB.close();
    }

    public void onDataReady() {

        setViewData();
        mSpinner.setVisibility(View.GONE);

        onConfigurationChanged(getResources().getConfiguration());

        // check watch history to set video start time
        mDisposables.add(mDB.watchHistoryDao().get(mRun.id)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Consumer<AppDatabase.WatchHistoryEntry>() {
                    @Override
                    public void accept(AppDatabase.WatchHistoryEntry historyEntry) throws Exception {
                        Log.d(TAG, "Got seek record for run: " + mRun.id);
                        mSeekPos = historyEntry.seekPos;
                        onVideoReady();
                    }
                }, new NoopConsumer<Throwable>(), new Action() {
                    @Override
                    public void run() throws Exception {
                        System.out.println("No seek record for run: " + mRun.id);
                        onVideoReady();
                    }
                }));
    }

    public void onVideoReady() {

        if (mRun.videos == null || mRun.videos.links == null || mRun.videos.links.isEmpty()) {
            LinearLayout ll = new LinearLayout(this);
            ll.setOrientation(LinearLayout.VERTICAL);
            ll.setGravity(Gravity.CENTER);
            ll.setBackground(new ColorDrawable(getResources().getColor(R.color.colorPrimaryDark)));

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.gravity = Gravity.CENTER;
            lp.topMargin = getResources().getDimensionPixelSize(R.dimen.half_fab_margin);

            TextView tv = new TextView(this);
            tv.setLayoutParams(lp);
            tv.setText(R.string.msg_no_video);

            ll.addView(tv);

            mVideoFrame.addView(ll);

            return;
        }

        mShownLink = mRun.videos.links.get(0);

        // find the first available youtube video we can recognize
        for (final MediaLink m : mRun.videos.links) {

            Log.d(TAG, "Trying to find YT/Twitch video: " + m.uri);

            if (m.isYoutube()) {
                setVideoFrameYT(m);
                break;
            } else if (m.isTwitch()) {
                setVideoFrameTwitch(m);
            } else {
                setVideoFrameOther(m);
                writeWatchToDb(0);
            }
        }

        if(!mShownLink.isYoutube() && !mShownLink.isTwitch()) {
            // just record the fact that the video page was accessed
            writeWatchToDb(0);
        }
    }

    public void setVideoFrameYT(MediaLink m) {
        Log.d(TAG, "Load YouTube video: " + m.uri);

        YouTubePlayerSupportFragment ytFrag = new YouTubePlayerSupportFragment();

        getSupportFragmentManager().beginTransaction()
                .replace(R.id.videoFrame, ytFrag)
                .commit();

        ytFrag.initialize(Constants.YOUTUBE_DEVELOPER_KEY, this);

        mVideoFrame.removeAllViews();
        mShownLink = m;
    }

    @SuppressLint("SetJavaScriptEnabled")
    public void setVideoFrameTwitch(MediaLink m) {
        String videoId = m.getTwitchVideoID();

        Log.d(TAG, "Show Twitch video ID: " + videoId);

        // start a web view and load custom content
        mTwitchWebView = new WebView(this);

        // configure the webview to support playing video
        mTwitchWebView.getSettings().setAppCacheMaxSize(10 * 1024 * 1024); // 50MB
        mTwitchWebView.getSettings().setJavaScriptEnabled(true);
        mTwitchWebView.getSettings().setMediaPlaybackRequiresUserGesture(false);

        float scaleFactor = 1.0f;
        String pageContent;
        try {
            pageContent = String.format(Locale.US, Util.readToString(getClass().getResourceAsStream(Constants.TWITCH_EMBED_SNIPPET_FILE)), scaleFactor, videoId, mSeekPos);
        } catch (IOException e) {

            Util.showErrorToast(this, getString(R.string.error_twitch));

            finish();
            return;
        }

        Log.d(TAG, pageContent);

        mTwitchWebView.getSettings().setUseWideViewPort(true);
        mTwitchWebView.getSettings().setLoadWithOverviewMode(true);

        mTwitchWebView.loadDataWithBaseURL("https://twitch.tv", pageContent,
                "text/html",
                null,
                null);

        mVideoFrame.removeAllViews();
        mVideoFrame.addView(mTwitchWebView);
        mShownLink = m;
    }

    public void setVideoFrameOther(final MediaLink m) {
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

        mVideoFrame.removeAllViews();
        mVideoFrame.addView(ll);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        if(mSpinner.getVisibility() == View.VISIBLE)
            return;

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
            mRunFooterPane.setVisibility(View.GONE);

        }
        else {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
            mRootView.setBackground(new ColorDrawable(getResources().getColor(R.color.colorPrimary)));

            // layout should always be vertical in this case
            mRootView.setOrientation(LinearLayout.VERTICAL);

            // show things
            mGameInfoPane.setVisibility(View.VISIBLE);
            mRunFooterPane.setVisibility(View.VISIBLE);
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
            mYoutubePlayer.loadVideo(mShownLink.getYoutubeVideoID(), (int) mSeekPos);
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

    private void recordStartPlaybackTime() {
        if(mYoutubePlayer != null) {
            writeWatchToDb(mYoutubePlayer.getCurrentTimeMillis());
        }
        else if(mTwitchWebView != null) {
            mTwitchWebView.evaluateJavascript("player.getCurrentTime()", new ValueCallback<String>() {
                @Override
                public void onReceiveValue(String value) {
                    try {
                        long seekPos = (long)Math.floor(Float.parseFloat(value));

                        writeWatchToDb(seekPos);
                    }
                    catch(NumberFormatException e) {
                        // ignored
                    }
                }
            });
        }
    }

    private void writeWatchToDb(long seekTime) {

        Log.d(TAG, "Record seek time: " + seekTime);

        mDisposables.add(mDB.watchHistoryDao().record(new AppDatabase.WatchHistoryEntry(mRun.id, seekTime))
                .subscribeOn(Schedulers.io())
                .subscribe());
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

        mPlayerNames.removeAllViews();
        for(final User player : mRun.players) {
            TextView tv = new TextView(this);
            tv.setTextSize(16);
            player.applyTextView(tv);

            int padding = getResources().getDimensionPixelSize(R.dimen.half_fab_margin);

            tv.setPadding(padding, padding, padding, padding);

            tv.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    viewPlayer(player);
                }
            });

            mPlayerNames.addView(tv);
        }

        mCategoryName.setText(mCategory.name);
        mRunTime.setText(mRun.times.formatTime());

        mRunComment.setText(mRun.comment);

        TextView emptyTv = new TextView(this);

        emptyTv.setText(R.string.empty_no_splits);
    }

    private void viewPlayer(User player) {
        Intent intent = new Intent(this, PlayerDetailActivity.class);
        intent.putExtra(PlayerDetailActivity.ARG_PLAYER_ID, player.id);

        startActivity(intent);
    }
}
