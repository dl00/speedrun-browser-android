package danb.speedrunbrowser;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;

import danb.speedrunbrowser.api.SpeedrunMiddlewareAPI;
import danb.speedrunbrowser.api.objects.Game;
import danb.speedrunbrowser.api.objects.Leaderboard;
import danb.speedrunbrowser.api.objects.LeaderboardRunEntry;
import danb.speedrunbrowser.utils.DownloadImageTask;
import danb.speedrunbrowser.utils.Util;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Consumer;

/**
 * A fragment representing a single leadboard, containing a list a records.
 */
public class LeaderboardFragment extends Fragment {

    private static final String TAG = LeaderboardFragment.class.getSimpleName();

    /**
     * The fragment argument representing the item ID that this fragment
     * represents.
     */
    public static final String ARG_CATEGORY_ID = "category_id";
    public static final String ARG_LEVEL_ID = "level_id";

    private final Game mGame;
    private Leaderboard mLeaderboard;

    private RecyclerView mLeaderboardList;

    private LeaderboardAdapter mRunsListAdapter;

    /**
     * Mandatory empty constructor for the fragment manager to instantiate the
     * fragment (e.g. upon screen orientation changes).
     */
    @SuppressLint("ValidFragment")
    public LeaderboardFragment(Game game) {
        mGame = game;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // the leaderboard id is a mishmash of category and level concatenated, if available/appropriate
        String leaderboardId = "";

        if (getArguments() != null && getArguments().containsKey(ARG_CATEGORY_ID)) {
            leaderboardId += getArguments().getString(ARG_CATEGORY_ID);
        }

        if (getArguments() != null && getArguments().containsKey(ARG_LEVEL_ID)) {
            leaderboardId += getArguments().getString(ARG_LEVEL_ID);
        }

        if(!leaderboardId.isEmpty())
            loadLeaderboard(leaderboardId);
    }

    public Disposable loadLeaderboard(final String leaderboardId) {

        Log.d(TAG, "Loading leaderboard: " + leaderboardId);

        return SpeedrunMiddlewareAPI.make().listLeaderboards(leaderboardId)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Consumer<SpeedrunMiddlewareAPI.APIResponse<Leaderboard>>() {
                    @Override
                    public void accept(SpeedrunMiddlewareAPI.APIResponse<Leaderboard> leaderboardAPIResponse) throws Exception {
                        List<Leaderboard> leaderboards = leaderboardAPIResponse.data;

                        if(leaderboards.isEmpty()) {
                            // not found
                            Util.showErrorToast(getContext(), leaderboardId);
                        }

                        mLeaderboard = leaderboards.get(0);

                        Log.d(TAG, "Downloaded " + mLeaderboard.runs.size() + " runs!");
                        if(mRunsListAdapter != null)
                            mRunsListAdapter.notifyDataSetChanged();
                    }
                });
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.leaderboard_list, container, false);

        mLeaderboardList = rootView.findViewById(R.id.leaderboardList);


        mRunsListAdapter = new LeaderboardAdapter();
        mLeaderboardList.setAdapter(mRunsListAdapter);

        return rootView;
    }

    public Game getGame() {
        return mGame;
    }

    public class LeaderboardAdapter extends RecyclerView.Adapter<LeaderboardFragment.RunViewHolder> {

        private final LayoutInflater inflater;

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
            holder.apply(getContext(), mGame, mLeaderboard.runs.get(position));
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

            try {
                Log.d(TAG, "TRYING TO DOWNLOAD TROPHY IMGS " + entry.place);
                if(entry.place == 1 && game.assets.trophy_1st != null) {
                    new DownloadImageTask(context, mRankImg).execute(new URL(game.assets.trophy_1st.uri));
                }
                if(entry.place == 2 && game.assets.trophy_2nd != null) {
                    new DownloadImageTask(context, mRankImg).execute(new URL(game.assets.trophy_2nd.uri));
                }
                if(entry.place == 3 && game.assets.trophy_3rd != null) {
                    new DownloadImageTask(context, mRankImg).execute(new URL(game.assets.trophy_3rd.uri));
                }
                if(entry.place == 4 && game.assets.trophy_4th != null) {
                    new DownloadImageTask(context, mRankImg).execute(new URL(game.assets.trophy_4th.uri));
                }
            } catch(MalformedURLException e) {
                Log.w(TAG, "Invalid rank image URL:", e);
            }
        }
    }
}
