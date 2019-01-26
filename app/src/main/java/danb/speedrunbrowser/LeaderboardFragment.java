package danb.speedrunbrowser;

import android.content.Context;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import danb.speedrunbrowser.api.SpeedrunAPI;
import danb.speedrunbrowser.api.objects.Game;
import danb.speedrunbrowser.api.objects.Leaderboard;
import danb.speedrunbrowser.api.objects.LeaderboardRunEntry;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * A fragment representing a single leadboard, containing a list a records.
 */
public class LeaderboardFragment extends Fragment implements Callback<SpeedrunAPI.APIResponse<Leaderboard>> {
    /**
     * The fragment argument representing the item ID that this fragment
     * represents.
     */
    public static final String ARG_GAME_ID = "game_id";
    public static final String ARG_CATEGORY_ID = "category_id";

    /**
     * The dummy content this fragment is presenting.
     */
    private Leaderboard mLeaderboard;
    private Game game;

    /**
     * Mandatory empty constructor for the fragment manager to instantiate the
     * fragment (e.g. upon screen orientation changes).
     */
    public LeaderboardFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getArguments().containsKey(ARG_GAME_ID) && getArguments().containsKey(ARG_CATEGORY_ID)) {
            // Load the dummy content specified by the fragment
            // arguments. In a real-world scenario, use a Loader
            // to load content from a content provider.
            SpeedrunAPI.make().getLeaderboard(getArguments().getString(ARG_GAME_ID), getArguments().getString(ARG_CATEGORY_ID)).enqueue(this);
        }
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.game_detail, container, false);


        return rootView;
    }

    @Override
    public void onResponse(@NonNull Call<SpeedrunAPI.APIResponse<Leaderboard>> call, @NonNull Response<SpeedrunAPI.APIResponse<Leaderboard>> response) {
    }

    @Override
    public void onFailure(@NonNull Call<SpeedrunAPI.APIResponse<Leaderboard>> call, @NonNull Throwable t) {

    }

    public class LeaderboardAdapter extends RecyclerView.Adapter<LeaderboardFragment.RunViewHolder> {

        private final LayoutInflater inflater;

        private final Context context;

        public LeaderboardAdapter(Context context) {
            this.context = context;
            inflater = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        }

        @NonNull
        @Override
        public RunViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = inflater.inflate(R.layout.game_list_content, parent, false);
            return new RunViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull RunViewHolder holder, int position) {
            holder.apply(context, mLeaderboard.runs.get(position));
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

        public void apply(Context context, LeaderboardRunEntry entry) {
            mPlayerName.setText(entry.run.players.get(0).getName());
            mRunTime.setText(entry.run.times.formatTime());
            mRunDate.setText(entry.run.date);
            mRank.setText(entry.place);

            /*String rankImgStr = null;

            switch(entry.place) {
                case 1:
                    rankImgStr =
            }*/
        }
    }
}
