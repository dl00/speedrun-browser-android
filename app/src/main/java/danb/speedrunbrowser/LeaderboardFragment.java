package danb.speedrunbrowser;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;

import danb.speedrunbrowser.api.SpeedrunMiddlewareAPI;
import danb.speedrunbrowser.api.objects.Category;
import danb.speedrunbrowser.api.objects.Game;
import danb.speedrunbrowser.api.objects.Leaderboard;
import danb.speedrunbrowser.api.objects.LeaderboardRunEntry;
import danb.speedrunbrowser.api.objects.Level;
import danb.speedrunbrowser.api.objects.Run;
import danb.speedrunbrowser.utils.DownloadImageTask;
import danb.speedrunbrowser.utils.Util;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Consumer;

/**
 * A fragment representing a single leadboard, containing a list a records.
 */
public class LeaderboardFragment extends Fragment implements Consumer<SpeedrunMiddlewareAPI.APIResponse<Leaderboard>> {
    private static final String TAG = LeaderboardFragment.class.getSimpleName();

    /**
     * The fragment argument representing the item ID that this fragment
     * represents.
     */
    public static final String ARG_GAME = "game";
    public static final String ARG_CATEGORY = "category";
    public static final String ARG_LEVEL = "level";

    private Game mGame;
    private Category mCategory;
    private Level mLevel;

    private Leaderboard mLeaderboard;

    private RecyclerView mLeaderboardList;

    private LeaderboardAdapter mRunsListAdapter;

    /**
     * Mandatory empty constructor for the fragment manager to instantiate the
     * fragment (e.g. upon screen orientation changes).
     */
    public LeaderboardFragment() {
        mGame = new Game();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mGame = (Game)getArguments().getSerializable(ARG_GAME);
        mCategory = (Category)getArguments().getSerializable(ARG_CATEGORY);
        mLevel = (Level)getArguments().getSerializable(ARG_LEVEL);

        assert mGame != null;
        assert mCategory != null;

        String leaderboardId = calculateLeaderboardId();

        if(!leaderboardId.isEmpty())
            loadLeaderboard(leaderboardId);
    }

    private String calculateLeaderboardId() {
        // the leaderboard id is a mishmash of category and level concatenated, if available/appropriate
        String leaderboardId = mCategory.id;

        if (mLevel != null) {
            leaderboardId += mLevel.id;
        }

        return leaderboardId;
    }

    public Disposable loadLeaderboard(final String leaderboardId) {

        Log.d(TAG, "Loading leaderboard: " + leaderboardId);

        return SpeedrunMiddlewareAPI.make().listLeaderboards(leaderboardId)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.leaderboard_list, container, false);

        mLeaderboardList = rootView.findViewById(R.id.leaderboardList);


        mRunsListAdapter = new LeaderboardAdapter();
        mLeaderboardList.setAdapter(mRunsListAdapter);

        Button viewRulesButton = rootView.findViewById(R.id.viewRulesButton);

        viewRulesButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                viewRules();
            }
        });

        Log.d(TAG, "create leaderboard list");

        return rootView;
    }

    public Game getGame() {
        return mGame;
    }

    // show game rules as a Alert Dialog
    private void viewRules() {
        String rulesText = mCategory.rules.trim();

        if(rulesText.isEmpty())
            rulesText = getString(R.string.msg_no_rules_content);

        AlertDialog dialog = new AlertDialog.Builder(getContext(), AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                .setMessage(rulesText)
                .setNeutralButton(android.R.string.ok, null)
                .create();

        dialog.show();
    }

    @Override
    public void accept(SpeedrunMiddlewareAPI.APIResponse<Leaderboard> leaderboardAPIResponse) throws Exception {
        List<Leaderboard> leaderboards = leaderboardAPIResponse.data;

        if(leaderboards.isEmpty()) {
            // not found
            Util.showErrorToast(getContext(), getString(R.string.error_missing_leaderboard, calculateLeaderboardId()));
        }

        mLeaderboard = leaderboards.get(0);

        Log.d(TAG, "Downloaded " + mLeaderboard.runs.size() + " runs!");
        if(mRunsListAdapter != null) {
            Log.d(TAG, "Runs list adapter not created/available");
            mRunsListAdapter.notifyDataSetChanged();
        }
    }

    public class LeaderboardAdapter extends RecyclerView.Adapter<LeaderboardFragment.RunViewHolder> {

        private final LayoutInflater inflater;

        private final View.OnClickListener mOnClickListener = new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                LeaderboardRunEntry re = (LeaderboardRunEntry) view.getTag();

                Intent intent = new Intent(getContext(), RunDetailActivity.class);
                intent.putExtra(RunDetailActivity.EXTRA_RUN, re.run);
                startActivity(intent);
            }
        };

        public LeaderboardAdapter() {
            inflater = (LayoutInflater)getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        }

        @NonNull
        @Override
        public RunViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = inflater.inflate(R.layout.leaderboard_list_content, parent, false);
            return new RunViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull RunViewHolder holder, int position) {
            LeaderboardRunEntry run = mLeaderboard.runs.get(position);

            holder.apply(getContext(), mGame, run);
            holder.itemView.setOnClickListener(mOnClickListener);
            holder.itemView.setTag(run);
        }

        @Override
        public int getItemCount() {
            return mLeaderboard != null ? mLeaderboard.runs.size() : 0;
        }
    }

    public static class RunViewHolder extends RecyclerView.ViewHolder {

        private TextView mPlayerName;
        private TextView mRunTime;
        private TextView mRunDate;
        private TextView mRank;

        private ImageView mRankImg;

        public RunViewHolder(View v) {
            super(v);

            mPlayerName = v.findViewById(R.id.txtPlayerName);
            mRunTime = v.findViewById(R.id.txtRunTime);
            mRunDate = v.findViewById(R.id.txtRunDate);
            mRank = v.findViewById(R.id.txtRank);
            mRankImg = v.findViewById(R.id.imgRank);
        }

        public void apply(Context context, Game game, LeaderboardRunEntry entry) {
            mPlayerName.setText(entry.run.players.get(0).id);
            mRunTime.setText(entry.run.times.formatTime());
            mRunDate.setText(entry.run.date);
            mRank.setText(entry.getPlaceName());

            if(entry.place == 1 && game.assets.trophy1st != null) {
                new DownloadImageTask(context, mRankImg).execute(game.assets.trophy1st.uri);
            }
            if(entry.place == 2 && game.assets.trophy2nd != null) {
                new DownloadImageTask(context, mRankImg).execute(game.assets.trophy2nd.uri);
            }
            if(entry.place == 3 && game.assets.trophy3rd != null) {
                new DownloadImageTask(context, mRankImg).execute(game.assets.trophy3rd.uri);
            }
            if(entry.place == 4 && game.assets.trophy4th != null) {
                new DownloadImageTask(context, mRankImg).execute(game.assets.trophy4th.uri);
            }
            else
                mRankImg.setImageDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
    }
}
