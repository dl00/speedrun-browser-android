package danb.speedrunbrowser;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Point;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.Display;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import com.google.android.flexbox.FlexboxLayout;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import androidx.appcompat.app.AppCompatActivity;
import danb.speedrunbrowser.api.SpeedrunMiddlewareAPI;
import danb.speedrunbrowser.api.objects.Category;
import danb.speedrunbrowser.api.objects.Game;
import danb.speedrunbrowser.api.objects.LeaderboardRunEntry;
import danb.speedrunbrowser.api.objects.Level;
import danb.speedrunbrowser.api.objects.MediaLink;
import danb.speedrunbrowser.api.objects.Platform;
import danb.speedrunbrowser.api.objects.Region;
import danb.speedrunbrowser.api.objects.Run;
import danb.speedrunbrowser.api.objects.User;
import danb.speedrunbrowser.api.objects.Variable;
import danb.speedrunbrowser.utils.Analytics;
import danb.speedrunbrowser.utils.AppDatabase;
import danb.speedrunbrowser.utils.ConnectionErrorConsumer;
import danb.speedrunbrowser.utils.ImageLoader;
import danb.speedrunbrowser.utils.ImageViewPlacerConsumer;
import danb.speedrunbrowser.utils.NoopConsumer;
import danb.speedrunbrowser.utils.Util;
import danb.speedrunbrowser.views.MultiVideoView;
import danb.speedrunbrowser.views.ProgressSpinnerView;
import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Action;
import io.reactivex.functions.Consumer;
import io.reactivex.internal.operators.flowable.FlowableInterval;
import io.reactivex.schedulers.Schedulers;

public class RunDetailActivity extends AppCompatActivity implements MultiVideoView.Listener {
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

    /// amount of time to hold the screen in a certain rotation after pressing the fullscreen button
    private static final int SCREEN_LOCK_ROTATE_PERIOD = 5;

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
    ChipGroup mVariableChips;
    FlexboxLayout mPlayerNames;
    TextView mRunTime;

    TextView mRunComment;

    ListView mRunSplits;
    TextView mRunEmptySplits;

    /**
     * Video views
     */
    MultiVideoView mVideoFrame;

    AppDatabase mDB;

    Game mGame;
    Category mCategory;
    Level mLevel;
    Run mRun;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_run_detail);

        mDB = AppDatabase.Companion.make(this);

        mRootView = findViewById(R.id.contentLayout);
        mSpinner = findViewById(R.id.spinner);
        mGameInfoPane = findViewById(R.id.gameInfoHead);
        mRunFooterPane = findViewById(R.id.runFooter);
        mGameName = findViewById(R.id.txtGameName);
        mReleaseDate = findViewById(R.id.txtReleaseDate);
        mPlatformList = findViewById(R.id.txtPlatforms);
        mCover = findViewById(R.id.imgCover);
        mCategoryName = findViewById(R.id.txtCategoryName);
        mVariableChips = findViewById(R.id.chipsVariables);
        mPlayerNames = findViewById(R.id.txtPlayerNames);
        mRunTime = findViewById(R.id.txtRunTime);
        mVideoFrame = findViewById(R.id.videoFrame);

        mRunComment = findViewById(R.id.txtRunComment);

        mRunSplits = findViewById(R.id.runSplitsList);
        mRunEmptySplits = findViewById(R.id.emptySplits);

        Bundle args = getIntent().getExtras();

        assert args != null;

        if(args.getSerializable(EXTRA_RUN) != null) {
            mRun = (Run)args.getSerializable(EXTRA_RUN);

            mGame = (Game)args.getSerializable(EXTRA_GAME);
            mCategory = (Category)args.getSerializable(EXTRA_CATEGORY);
            mLevel = (Level)args.getSerializable(EXTRA_LEVEL);

            onDataReady();
        }
        else if(args.getString(EXTRA_RUN_ID) != null) {
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
                Util.INSTANCE.showErrorToast(this, getString(R.string.error_invalod_url, appLinkData));
            }
        }
        else {

            mGame = (Game) args.getSerializable(EXTRA_GAME);
            mCategory = (Category) args.getSerializable(EXTRA_CATEGORY);
            mLevel = (Level) args.getSerializable(EXTRA_LEVEL);
            mRun = (Run) args.getSerializable(EXTRA_RUN);

            onDataReady();
        }

        mGameInfoPane.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                viewGame();
            }
        });
    }

    private void loadRun(final String runId) {
        Log.d(TAG, "Download runId: " + runId);
        mDisposables.add(SpeedrunMiddlewareAPI.INSTANCE.make().listRuns(runId)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(new Consumer<SpeedrunMiddlewareAPI.APIResponse<LeaderboardRunEntry>>() {
                @Override
                public void accept(SpeedrunMiddlewareAPI.APIResponse<LeaderboardRunEntry> gameAPIResponse) throws Exception {

                    if (gameAPIResponse.getData() == null) {
                        // game was not able to be found for some reason?
                        Util.INSTANCE.showErrorToast(RunDetailActivity.this, getString(R.string.error_missing_game, runId));
                        return;
                    }

                    mRun = gameAPIResponse.getData().get(0).getRun();
                    mGame = mRun.getGame();
                    mCategory = mRun.getCategory();
                    mLevel = mRun.getLevel();

                    onDataReady();
                }
            }, new ConnectionErrorConsumer(this)));
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
            }, new Consumer<Throwable>() {
                @Override
                public void accept(Throwable throwable) throws Exception {
                    Log.w(TAG, "Problem running background save interval: ", throwable);
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

        Analytics.INSTANCE.logItemView(this, "run", mRun.getId());

        onConfigurationChanged(getResources().getConfiguration());

        // check watch history to set video start time
        mDisposables.add(mDB.watchHistoryDao().get(mRun.getId())
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Consumer<AppDatabase.WatchHistoryEntry>() {
                    @Override
                    public void accept(AppDatabase.WatchHistoryEntry historyEntry) throws Exception {
                        Log.d(TAG, "Got seek record for run: " + mRun.getId());
                        mVideoFrame.setSeekTime((int) historyEntry.getSeekPos());
                        onVideoReady();
                    }
                }, new NoopConsumer<Throwable>(), new Action() {
                    @Override
                    public void run() throws Exception {
                        System.out.println("No seek record for run: " + mRun.getId());
                        onVideoReady();
                    }
                }));
    }

    public void onVideoReady() {

        mVideoFrame.setListener(this);

        if (mRun.getVideos() == null || mRun.getVideos().getLinks() == null || mRun.getVideos().getLinks().isEmpty()) {
            mVideoFrame.setVideoNotAvailable();

            return;
        }

        // find the first available video recognized
        for(MediaLink ml : mRun.getVideos().getLinks()) {
            if(mVideoFrame.loadVideo(ml))
                break;
        }

        if(!mVideoFrame.hasLoadedVideo()) {
            Log.w(TAG, "Could not play a video for this run");
            // just record the fact that the video page was accessed
            mVideoFrame.setVideoFrameOther(mRun.getVideos().getLinks().get(0));
            writeWatchToDb(0);
        }
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

    private void recordStartPlaybackTime() {
        if(mVideoFrame.hasLoadedVideo())
            writeWatchToDb(mVideoFrame.getSeekTime());
    }

    private void writeWatchToDb(long seekTime) {

        Log.d(TAG, "Record seek time: " + seekTime);

        mDisposables.add(mDB.watchHistoryDao().record(new AppDatabase.WatchHistoryEntry(mRun.getId(), seekTime, 0))
                .subscribeOn(Schedulers.io())
                .subscribe());
    }

    private void setViewData() {
        mGameName.setText(mGame.getResolvedName());
        mReleaseDate.setText(mGame.getReleaseDate());

        // we have to join the string manually because it is java 7
        if(mGame.getPlatforms() != null) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < mGame.getPlatforms().size(); i++) {
                sb.append(mGame.getPlatforms().get(i).getName());
                if(i < mGame.getPlatforms().size() - 1)
                    sb.append(", ");
            }

            mPlatformList.setText(sb.toString());
        }
        else {
            mPlatformList.setText("");
        }

        mVariableChips.removeAllViews();

        StringBuilder fullCategoryName = new StringBuilder(mCategory.getName());
        if(mLevel != null)
            fullCategoryName.append(" \u2022 ").append(mLevel.getName());

        if(mGame.shouldShowPlatformFilter() && mRun.getSystem() != null) {
            Chip chip = new Chip(this);

            for(Platform p : mGame.getPlatforms()) {
                if(p.getId().equals(mRun.getSystem().getPlatform())) {
                    chip.setText(p.getName());
                    mVariableChips.addView(chip);
                    break;
                }
            }
        }

        if(mGame.shouldShowRegionFilter() && mRun.getSystem() != null) {
            Chip chip = new Chip(this);

            for(Region r : mGame.getRegions()) {
                if(r.getId().equals(mRun.getSystem().getRegion())) {
                    chip.setText(r.getName());
                    mVariableChips.addView(chip);
                    break;
                }
            }
        }

        if(mCategory.getVariables() != null) {
            for(Variable var : mCategory.getVariables()) {
                if(mRun.getValues().containsKey(var.getId()) && !var.isSubcategory() && var.getValues().containsKey(mRun.getValues().get(var.getId()))) {
                    Chip chip = new Chip(this);
                    chip.setText(new StringBuilder(var.getName()).append(": ").append(Objects.requireNonNull(var.getValues().get(mRun.getValues().get(var.getId()))).getLabel()));
                    mVariableChips.addView(chip);
                }
                else if(var.isSubcategory() && var.getValues().containsKey(mRun.getValues().get(var.getId()))) {
                    fullCategoryName.append(" \u2022 ").append(Objects.requireNonNull(var.getValues().get(mRun.getValues().get(var.getId()))).getLabel());
                }
            }
        }

        if(mGame.getAssets().getCoverLarge() != null)
            mDisposables.add(
                    new ImageLoader(this).loadImage(mGame.getAssets().getCoverLarge().getUri())
                            .subscribe(new ImageViewPlacerConsumer(mCover)));

        mPlayerNames.removeAllViews();
        for(final User player : mRun.getPlayers()) {
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

        mCategoryName.setText(fullCategoryName);
        mRunTime.setText(mRun.getTimes().getTime());

        mRunComment.setText(mRun.getComment());

        TextView emptyTv = new TextView(this);

        emptyTv.setText(R.string.empty_no_splits);
    }

    private void viewPlayer(User player) {
        Intent intent = new Intent(this, ItemDetailActivity.class);
        intent.putExtra(ItemDetailActivity.EXTRA_ITEM_TYPE, ItemListFragment.ItemType.PLAYERS);
        intent.putExtra(PlayerDetailFragment.ARG_PLAYER_ID, player.getId());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            intent.setFlags(Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT);
        }

        startActivity(intent);
    }

    private void viewGame() {
        Intent intent = new Intent(this, ItemDetailActivity.class);
        intent.putExtra(ItemDetailActivity.EXTRA_ITEM_TYPE, ItemListFragment.ItemType.GAMES);
        intent.putExtra(GameDetailFragment.ARG_GAME_ID, mGame.getId());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            intent.setFlags(Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT);
        }

        startActivity(intent);
    }

    @Override
    public void onFullscreenToggleListener() {
        if(getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE)
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        else
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);

        // prevent screen rotation from being locked
        mDisposables.add(Observable.timer(SCREEN_LOCK_ROTATE_PERIOD, TimeUnit.SECONDS)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(new Consumer<Long>() {
                @Override
                public void accept(Long aLong) throws Exception {
                    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
                }
            }));
    }
}
