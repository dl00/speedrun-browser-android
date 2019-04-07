package danb.speedrunbrowser;

import android.app.ActivityOptions;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.crashlytics.android.Crashlytics;
import com.google.android.gms.security.ProviderInstaller;
import com.google.firebase.crash.FirebaseCrash;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.RecyclerView;
import danb.speedrunbrowser.api.SpeedrunMiddlewareAPI;
import danb.speedrunbrowser.api.objects.Game;
import danb.speedrunbrowser.api.objects.User;
import danb.speedrunbrowser.utils.DownloadImageTask;
import danb.speedrunbrowser.utils.PreCachingGridLayoutManager;
import danb.speedrunbrowser.utils.Util;
import danb.speedrunbrowser.views.ProgressSpinnerView;
import io.reactivex.ObservableSource;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Consumer;
import io.reactivex.functions.Function;
import io.reactivex.subjects.PublishSubject;

/**
 * An activity representing a list of Games. This activity
 * has different presentations for handset and tablet-size devices. On
 * handsets, the activity presents a list of items, which when touched,
 * lead to a {@link GameDetailActivity} representing
 * item details. On tablets, the activity presents the list of items and
 * item details side-by-side using two vertical panes.
 */
public class GameListActivity extends AppCompatActivity implements TextWatcher {
    private static final String TAG = GameListActivity.class.getSimpleName();

    /**
     * The list of games we are presenting on the list to the user
     */
    private List<Game> mGames;
    private List<User> mPlayers;

    /**
     * A reference to the renderer for the game list items
     */
    private GameListAdapter mAdapter;


    private EditText mGameFilter;

    private TextView mGameResultsLabel;
    private TextView mPlayerResultsLabel;

    private ProgressSpinnerView mSpinner;
    private RecyclerView mGameListView;
    private HorizontalScrollView mPlayerHsv;
    private LinearLayout mPlayerListView;

    private PublishSubject<CharSequence> mGameFilterSearchSubject;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_game_list);

        FirebaseCrash.setCrashCollectionEnabled(!BuildConfig.DEBUG);

        Util.showNewFeaturesDialog(this);

        // might need to update certificates/connection modes on older android versions
        // TODO: this is the synchronous call, may block user interation when installing provider. Consider using async
        try {
            ProviderInstaller.installIfNeeded(getApplicationContext());
        } catch(Exception e) {
            Log.w(TAG, "Could not install latest certificates using Google Play Services");
        }

        mGameFilter = findViewById(R.id.editGameFilter);
        mSpinner = findViewById(R.id.spinner);
        mGameListView = findViewById(R.id.listGame);
        mPlayerHsv = findViewById(R.id.hsvPlayers);
        mPlayerListView = findViewById(R.id.layoutPlayerSearchResults);
        mGameResultsLabel = findViewById(R.id.txtGamesSearch);
        mPlayerResultsLabel = findViewById(R.id.txtPlayersSearch);

        mGameFilter.addTextChangedListener(this);

        mGameFilterSearchSubject = PublishSubject.create();

        mAdapter = new GameListAdapter();
        mGameListView.setAdapter(mAdapter);


        findViewById(android.R.id.content)
                .getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {

            int lastMeasuredWidth = 0;

            @Override
            public void onGlobalLayout() {
                int mw = findViewById(android.R.id.content).getMeasuredWidth();
                if (lastMeasuredWidth != mw)
                    setupRecyclerView(mGameListView);

                lastMeasuredWidth = mw;
            }
        });

        setupGameDownloader();

        // force initial load
        mGameFilterSearchSubject.onNext(mGameFilter.getText());
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.game_list, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if(item.getItemId() == R.id.menu_about) {
            showAbout();
            return true;
        }
        else if(item.getItemId() == R.id.menu_history) {
            showWatchHistory();
            return true;
        }

        return false;
    }

    protected boolean isTwoPane() {
        // The detail container view will be present only in the
        // large-screen layouts (res/values-w900dp).
        // If this view is present, then the
        // activity should be in two-pane mode.
        return findViewById(R.id.game_detail_container) != null;
    }

    private void setupRecyclerView(@NonNull RecyclerView recyclerView) {

        DisplayMetrics displayMetrics = getResources().getDisplayMetrics();

        // set number of columns based on screen width
        recyclerView.setLayoutManager(new PreCachingGridLayoutManager(this,
                Math.max((int)Math.ceil(
                        findViewById(android.R.id.content).getMeasuredWidth() / (512.0f)
                ), 3)));
    }

    private Disposable setupGameDownloader() {
        return mGameFilterSearchSubject
                .debounce(250, TimeUnit.MILLISECONDS)
                .switchMap(new Function<CharSequence, ObservableSource<?>>() {
                    @Override
                    public ObservableSource<?> apply(CharSequence q) throws Exception {
                        if(q.length() > 0) {
                            return SpeedrunMiddlewareAPI.make().autocomplete(q.toString());
                        }
                        else {
                            return SpeedrunMiddlewareAPI.make().listGames(0);
                        }
                    }
                })
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Consumer<Object>() {
                    @Override
                    public void accept(Object res) throws Exception {
                        List<Game> games = new ArrayList<>(0);
                        List<User> players = new ArrayList<>(0);
                        if (res instanceof SpeedrunMiddlewareAPI.APISearchResponse) {
                            games = ((SpeedrunMiddlewareAPI.APISearchResponse) res).search.games;
                            players = ((SpeedrunMiddlewareAPI.APISearchResponse) res).search.players;

                        } else if (res instanceof SpeedrunMiddlewareAPI.APIResponse) {
                            games = ((SpeedrunMiddlewareAPI.APIResponse<Game>) res).data;
                        }

                        mGames = games;
                        mPlayers = players;
                        mGameListView.scrollToPosition(0);

                        setPlayerSearchResults();
                        setGameSearchResults();
                    }
                }, new Consumer<Throwable>() {
                    @Override
                    public void accept(Throwable throwable) throws Exception {
                        Log.w(TAG, "Could not download autocomplete results:" + throwable.getMessage(), throwable);

                        try {
                            Util.showErrorToast(GameListActivity.this, getString(R.string.error_could_not_connect));
                            finish();
                        } catch(Exception e) {
                            Log.w(TAG, "Could not elegantly warn and exit app:", e);
                        }
                    }
                });
    }

    private void setPlayerSearchResults() {
        mPlayerListView.removeAllViews();

        if(mPlayers != null && !mPlayers.isEmpty()) {
            for(final User player : mPlayers) {
                TextView tv = new TextView(this);
                tv.setTextSize(18);
                player.applyTextView(tv);

                int padding = getResources().getDimensionPixelSize(R.dimen.half_fab_margin);
                tv.setPadding(padding, padding, padding, padding);

                tv.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Intent intent = new Intent(GameListActivity.this, PlayerDetailActivity.class);
                        intent.putExtra(PlayerDetailActivity.ARG_PLAYER, player);

                        startActivity(intent);
                    }
                });

                mPlayerListView.addView(tv);
            }

            mPlayerHsv.setVisibility(View.VISIBLE);
            mPlayerHsv.setHorizontalScrollBarEnabled(false);
            mPlayerHsv.scrollTo(0, 0);
            mPlayerResultsLabel.setVisibility(View.VISIBLE);
        }
        else {
            mPlayerHsv.setVisibility(View.GONE);
            mPlayerResultsLabel.setVisibility(View.GONE);
        }
    }

    private void setGameSearchResults() {
        mAdapter.notifyDataSetChanged();

        if(mPlayers != null && !mPlayers.isEmpty() && mGames != null && !mGames.isEmpty())
            mGameResultsLabel.setVisibility(View.VISIBLE);
        else
            mGameResultsLabel.setVisibility(View.GONE);

        if(mGames == null || mGames.isEmpty())
            mSpinner.setVisibility(View.GONE);
    }

    public void showAbout() {
        Intent intent = new Intent(this, AboutActivity.class);
        startActivity(intent);
    }

    public void showWatchHistory() {
        Intent intent = new Intent(this, HistoryActivity.class);
        startActivity(intent);
    }

    @Override
    public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

    }

    @Override
    public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
    }

    @Override
    public void afterTextChanged(Editable editable) {
        String q = mGameFilter.getText().toString().trim();

        if(q.isEmpty() || q.length() >= SpeedrunMiddlewareAPI.MIN_AUTOCOMPLETE_LENGTH) {
            mGameFilterSearchSubject.onNext(q);
            mSpinner.setVisibility(View.VISIBLE);
        }
    }
}
