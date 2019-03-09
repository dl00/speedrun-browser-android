package danb.speedrunbrowser;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.appcompat.widget.Toolbar;

import android.os.Looper;
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
import android.widget.ImageView;
import android.widget.TextView;

import com.google.android.gms.common.GooglePlayServicesNotAvailableException;
import com.google.android.gms.security.ProviderInstaller;

import danb.speedrunbrowser.api.SpeedrunMiddlewareAPI;
import danb.speedrunbrowser.utils.DownloadImageTask;
import danb.speedrunbrowser.api.objects.Game;
import danb.speedrunbrowser.utils.PreCachingGridLayoutManager;
import danb.speedrunbrowser.utils.Util;
import danb.speedrunbrowser.views.ProgressSpinnerView;
import io.reactivex.ObservableSource;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Consumer;
import io.reactivex.functions.Function;
import io.reactivex.subjects.PublishSubject;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

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

    /**
     * A reference to the renderer for the game list items
     */
    private GameListAdapter mAdapter;


    private EditText mGameFilter;

    private ProgressSpinnerView mSpinner;
    private RecyclerView mGameListView;

    private PublishSubject<CharSequence> mGameFilterSearchSubject;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_game_list);

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
                        if (res instanceof SpeedrunMiddlewareAPI.APISearchResponse) {
                            games = ((SpeedrunMiddlewareAPI.APISearchResponse) res).search.games;
                        } else if (res instanceof SpeedrunMiddlewareAPI.APIResponse) {
                            games = ((SpeedrunMiddlewareAPI.APIResponse<Game>) res).data;
                        }

                        mGames = games;
                        mGameListView.scrollToPosition(0);
                        mAdapter.notifyDataSetChanged();
                    }
                }, new Consumer<Throwable>() {
                    @Override
                    public void accept(Throwable throwable) throws Exception {
                        Log.w(TAG, "Could not download autocomplete results:" + throwable.getMessage(), throwable);

                        Util.showErrorToast(GameListActivity.this, getString(R.string.error_could_not_connect));

                        finish();
                    }
                });
    }

    public void showAbout() {
        Intent intent = new Intent(this, AboutActivity.class);
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

        if(q.isEmpty() || q.length() > SpeedrunMiddlewareAPI.MIN_AUTOCOMPLETE_LENGTH) {
            mGameFilterSearchSubject.onNext(q);
            mSpinner.setVisibility(View.VISIBLE);
        }
    }

    private class GameListAdapter extends RecyclerView.Adapter<GameListActivity.GameItemViewHolder> {

        private final LayoutInflater inflater;

        private final View.OnClickListener mOnClickListener = new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Game g = (Game) view.getTag();
                if (isTwoPane()) {
                    Bundle arguments = new Bundle();
                    arguments.putString(GameDetailFragment.ARG_GAME_ID, g.id);
                    GameDetailFragment fragment = new GameDetailFragment();
                    fragment.setArguments(arguments);
                    getSupportFragmentManager().beginTransaction()
                            .replace(R.id.game_detail_container, fragment)
                            .commit();
                } else {
                    Intent intent = new Intent(GameListActivity.this, GameDetailActivity.class);
                    intent.putExtra(GameDetailFragment.ARG_GAME_ID, g.id);

                    startActivity(intent);
                }
            }
        };

        public GameListAdapter() {
            inflater = (LayoutInflater)getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        }

        @Override
        public GameItemViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = inflater.inflate(R.layout.game_grid_content, parent, false);
            return new GameItemViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull GameItemViewHolder holder, int position) {
            holder.apply(mGames.get(position));
            holder.itemView.setOnClickListener(mOnClickListener);
            holder.itemView.setTag(mGames.get(position));
        }

        @Override
        public int getItemCount() {
            return mGames != null ? mGames.size() : 0;
        }
    }

    public class GameItemViewHolder extends RecyclerView.ViewHolder {

        private TextView mName;
        private TextView mDate;
        private TextView mRunnersCount;

        private ImageView mCover;

        GameItemViewHolder(View v) {
            super(v);

            mName = v.findViewById(R.id.txtGameName);
            mDate = v.findViewById(R.id.txtReleaseDate);
            mRunnersCount = v.findViewById(R.id.txtPlayerCount);
            mCover = v.findViewById(R.id.imgGameCover);
        }

        public void apply(Game game) {
            mName.setText(game.names.get("international"));
            mDate.setText(game.releaseDate);
            mRunnersCount.setText("");

            if(game.assets.coverLarge != null)
                new DownloadImageTask(GameListActivity.this, mCover)
                        .listener(new DownloadImageTask.OnCompleteListener() {
                            @Override
                            public void onComplete(View v) {
                                if(DownloadImageTask.getPendingCount() == 0)
                                    mSpinner.setVisibility(View.GONE);
                            }
                        })
                        .execute(game.assets.coverLarge.uri);
            else {}
            // TODO: In the extremely unlikely case there is no cover, might have to replace with dummy image
        }
    }
}
