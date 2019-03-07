package danb.speedrunbrowser;

import android.animation.Animator;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.flexbox.FlexboxLayout;

import java.util.List;
import java.util.Objects;

import danb.speedrunbrowser.api.SpeedrunMiddlewareAPI;
import danb.speedrunbrowser.api.objects.Category;
import danb.speedrunbrowser.api.objects.Game;
import danb.speedrunbrowser.api.objects.Leaderboard;
import danb.speedrunbrowser.api.objects.LeaderboardRunEntry;
import danb.speedrunbrowser.api.objects.Level;
import danb.speedrunbrowser.api.objects.Run;
import danb.speedrunbrowser.api.objects.User;
import danb.speedrunbrowser.api.objects.Variable;
import danb.speedrunbrowser.utils.DownloadImageTask;
import danb.speedrunbrowser.utils.Util;
import danb.speedrunbrowser.views.ProgressSpinnerView;
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

    private Variable.VariableSelections mVariableSelections;

    private ProgressSpinnerView mProgressSpinner;

    private LinearLayout mContentLayout;

    private Leaderboard mLeaderboard;
    private List<LeaderboardRunEntry> mFilteredLeaderboardRuns;

    private RecyclerView mLeaderboardList;

    private TextView mEmptyRuns;

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

        Bundle args = Objects.requireNonNull(getArguments());

        mGame = (Game)args.getSerializable(ARG_GAME);
        mCategory = (Category)args.getSerializable(ARG_CATEGORY);
        mLevel = (Level)args.getSerializable(ARG_LEVEL);

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
            leaderboardId += "_" + mLevel.id;
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

        mProgressSpinner = rootView.findViewById(R.id.progress);
        mContentLayout = rootView.findViewById(R.id.contentLayout);

        mLeaderboardList = rootView.findViewById(R.id.leaderboardList);
        mEmptyRuns = rootView.findViewById(R.id.emptyRuns);

        mRunsListAdapter = new LeaderboardAdapter();
        mLeaderboardList.setAdapter(mRunsListAdapter);

        Button viewRulesButton = rootView.findViewById(R.id.viewRulesButton);

        viewRulesButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                viewRules();
            }
        });

        notifyFilterChanged();

        Log.d(TAG, "create leaderboard list");

        return rootView;
    }

    @Override
    public void onResume() {
        super.onResume();

        if(mLeaderboard != null) {
            mProgressSpinner.setVisibility(View.GONE);
            mContentLayout.setVisibility(View.VISIBLE);

            if(mFilteredLeaderboardRuns.isEmpty()) {
                mEmptyRuns.setVisibility(View.VISIBLE);
            }
        }
    }

    public Game getGame() {
        return mGame;
    }

    // show game rules as a Alert Dialog
    private void viewRules() {

        String rulesText = "";

        if(mCategory.rules != null)
            rulesText = mCategory.rules.trim();

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
            return;
        }

        mLeaderboard = leaderboards.get(0);
        notifyFilterChanged();

        Log.d(TAG, "Downloaded " + mLeaderboard.runs.size() + " runs!");
        if(mRunsListAdapter != null) {
            Log.d(TAG, "Runs list adapter not created/available");
            mRunsListAdapter.notifyDataSetChanged();

            if(getContext() != null)
                animateLeaderboardIn();
        }
    }

    private void animateLeaderboardIn() {
        int animTime = getResources().getInteger(
                android.R.integer.config_shortAnimTime);

        int translationDistance = getResources().getDimensionPixelSize(R.dimen.anim_slide_transition_distance);

        mProgressSpinner.animate()
                .alpha(0.0f)
                .translationY(-translationDistance)
                .setDuration(animTime)
                .setListener(new Animator.AnimatorListener() {
                    @Override
                    public void onAnimationStart(Animator animator) {}

                    @Override
                    public void onAnimationEnd(Animator animator) {
                        mProgressSpinner.stop();
                    }

                    @Override
                    public void onAnimationCancel(Animator animator) {}

                    @Override
                    public void onAnimationRepeat(Animator animator) {}
                });

        mContentLayout.setAlpha(0.0f);
        mContentLayout.setVisibility(View.VISIBLE);

        mContentLayout.setTranslationY(translationDistance);

        mContentLayout.animate()
                .alpha(1.0f)
                .setDuration(animTime)
                .translationY(0)
                .setListener(null);
    }

    private static User resolvePlayer(Leaderboard lb, User pid) {
        // find the matching player
        User player = null;

        if(pid.id != null) {
            player = lb.players.get(pid.id);

            if(player == null) {
                player = pid;
            }
        }
        else {
            player = pid;
        }

        return player;
    }

    public void setFilter(Variable.VariableSelections selections) {
        mVariableSelections = selections;
        notifyFilterChanged();
    }

    public void notifyFilterChanged() {
        if(mLeaderboard != null) {
            if(mVariableSelections != null)
                mFilteredLeaderboardRuns = mVariableSelections.filterLeaderboardRuns(mLeaderboard);
            else
                mFilteredLeaderboardRuns = mLeaderboard.runs;

            if(mRunsListAdapter != null) {
                mRunsListAdapter.notifyDataSetChanged();

                if(mFilteredLeaderboardRuns.isEmpty()) {
                    mEmptyRuns.setVisibility(View.VISIBLE);
                }
                else {
                    mEmptyRuns.setVisibility(View.GONE);
                }
            }
        }
    }

    public class LeaderboardAdapter extends RecyclerView.Adapter<LeaderboardFragment.RunViewHolder> {

        private final LayoutInflater inflater;

        private final View.OnClickListener mOnClickListener = new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                LeaderboardRunEntry re = (LeaderboardRunEntry) view.getTag();

                Run run = re.run;

                // map actual player data in
                for(int i = 0;i < run.players.size();i++) {
                    run.players.set(i, resolvePlayer(mLeaderboard, run.players.get(i)));
                }

                Intent intent = new Intent(getContext(), RunDetailActivity.class);
                intent.putExtra(RunDetailActivity.EXTRA_GAME, mGame);
                intent.putExtra(RunDetailActivity.EXTRA_CATEGORY, mCategory);
                intent.putExtra(RunDetailActivity.EXTRA_LEVEL, mLevel);
                intent.putExtra(RunDetailActivity.EXTRA_RUN, run);
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
            LeaderboardRunEntry run = mFilteredLeaderboardRuns.get(position);

            holder.apply(getContext(), mGame, run, mLeaderboard);
            holder.itemView.setOnClickListener(mOnClickListener);
            holder.itemView.setTag(run);
        }

        @Override
        public int getItemCount() {
            return mFilteredLeaderboardRuns != null ? mFilteredLeaderboardRuns.size() : 0;
        }
    }

    public static class RunViewHolder extends RecyclerView.ViewHolder {

        private FlexboxLayout mPlayerNames;
        private TextView mRunTime;
        private TextView mRunDate;
        private TextView mRank;

        private ImageView mRankImg;

        public RunViewHolder(View v) {
            super(v);

            mPlayerNames = v.findViewById(R.id.txtPlayerNames);
            mRunTime = v.findViewById(R.id.txtRunTime);
            mRunDate = v.findViewById(R.id.txtRunDate);
            mRank = v.findViewById(R.id.txtRank);
            mRankImg = v.findViewById(R.id.imgRank);
        }

        public void apply(Context context, Game game, LeaderboardRunEntry entry, Leaderboard lb) {

            mPlayerNames.removeAllViews();
            boolean first = true;
            for(User pid : entry.run.players) {

                // find the matching player
                User player = resolvePlayer(lb, pid);

                TextView tv = new TextView(context);
                tv.setTextSize(16);
                player.applyTextView(tv);

                if(!first) {
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                    lp.setMargins(context.getResources().getDimensionPixelSize(R.dimen.half_fab_margin), 0, 0, 0);
                    tv.setLayoutParams(lp);
                }
                else
                    first = false;


                mPlayerNames.addView(tv);
            }

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
