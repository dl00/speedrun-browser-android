package danb.speedrunbrowser;

import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import danb.speedrunbrowser.api.SpeedrunAPI;
import danb.speedrunbrowser.utils.DownloadImageTask;
import danb.speedrunbrowser.api.objects.Game;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.Objects;

/**
 * An activity representing a list of Games. This activity
 * has different presentations for handset and tablet-size devices. On
 * handsets, the activity presents a list of items, which when touched,
 * lead to a {@link GameDetailActivity} representing
 * item details. On tablets, the activity presents the list of items and
 * item details side-by-side using two vertical panes.
 */
public class GameListActivity extends AppCompatActivity implements Callback<SpeedrunAPI.APIResponse<List<Game>>>, TextView.OnEditorActionListener {
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_game_list);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setTitle(getTitle());

        mGameFilter = findViewById(R.id.editGameFilter);
        assert mGameFilter != null;
        mGameFilter.setOnEditorActionListener(this);

        View recyclerView = findViewById(R.id.game_list);
        assert recyclerView != null;
        setupRecyclerView((RecyclerView) recyclerView);
    }

    protected boolean isTwoPane() {
        // The detail container view will be present only in the
        // large-screen layouts (res/values-w900dp).
        // If this view is present, then the
        // activity should be in two-pane mode.
        return findViewById(R.id.game_detail_container) != null;
    }

    private void setupRecyclerView(@NonNull RecyclerView recyclerView) {
        mAdapter = new GameListAdapter();
        recyclerView.setAdapter(mAdapter);
    }

    private void downloadGames(String nameFilter) {
        Log.d("Downloading games", nameFilter);
        SpeedrunAPI.make().listGames(nameFilter).enqueue(this);
    }

    @Override
    public void onResponse(@NonNull Call<SpeedrunAPI.APIResponse<List<Game>>> call, @NonNull Response<SpeedrunAPI.APIResponse<List<Game>>> response) {
        if(response.isSuccessful()) {
            mGames = Objects.requireNonNull(response.body()).data;
            mAdapter.notifyDataSetChanged();
        }
        else {
            try {
                Log.w(TAG, "Failed to download games: " + response.errorBody().string());
            }
            catch(IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void onFailure(@NonNull Call<SpeedrunAPI.APIResponse<List<Game>>> call, @NonNull Throwable t) {
        Log.w(TAG, "Could not download games", t);
    }

    @Override
    public boolean onEditorAction(TextView textView, int i, KeyEvent keyEvent) {
        try {
            downloadGames(mGameFilter.getText().toString());
        } catch(Exception e) {
            e.printStackTrace();
        }

        return true;
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
            View v = inflater.inflate(R.layout.game_list_content, parent, false);
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

    public static class GameItemViewHolder extends RecyclerView.ViewHolder {

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

            try {
                new DownloadImageTask(mCover).execute(new URL(game.assets.coverSmall.uri));
            } catch(MalformedURLException e) {
                // skip
            }
        }
    }
}
