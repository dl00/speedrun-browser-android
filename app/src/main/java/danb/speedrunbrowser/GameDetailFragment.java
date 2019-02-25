package danb.speedrunbrowser;

import android.content.Context;
import androidx.annotation.NonNull;
import android.os.Bundle;
import androidx.fragment.app.Fragment;
import androidx.viewpager.widget.ViewPager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import danb.speedrunbrowser.api.SpeedrunMiddlewareAPI;
import danb.speedrunbrowser.api.objects.Game;
import danb.speedrunbrowser.utils.DownloadImageTask;
import danb.speedrunbrowser.utils.Util;
import danb.speedrunbrowser.views.CategoryTabStrip;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Consumer;

/**
 * A fragment representing a single Game detail screen.
 * This fragment is either contained in a {@link GameListActivity}
 * in two-pane mode (on tablets) or a {@link GameDetailActivity}
 * on handsets.
 */
public class GameDetailFragment extends Fragment {

    public static final String TAG = GameDetailFragment.class.getSimpleName();

    /**
     * The fragment argument representing the item ID that this fragment
     * represents.
     */
    public static final String ARG_GAME_ID = "game_id";

    /**
     * The dummy content this fragment is presenting.
     */
    private Game mGame;


    /**
     * Game detail view views
     */
    TextView mGameName;
    TextView mReleaseDate;
    TextView mPlatformList;

    ImageView mCover;
    ImageView mBackground;

    CategoryTabStrip mCategoryTabStrip;
    ViewPager mLeaderboardPager;

    /**
     * Mandatory empty constructor for the fragment manager to instantiate the
     * fragment (e.g. upon screen orientation changes).
     */
    public GameDetailFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if(getArguments() == null) {
            Log.e(TAG, "No arguments provided");
            return;
        }

        if (getArguments().containsKey(ARG_GAME_ID)) {
            // Load the dummy content specified by the fragment
            // arguments. In a real-world scenario, use a Loader
            // to load content from a content provider.

            final String gameId = getArguments().getString(ARG_GAME_ID);
            loadGame(gameId);
        }
    }

    public Disposable loadGame(final String gameId) {
        Log.d(TAG, "Downloading game data: " + gameId);
        return SpeedrunMiddlewareAPI.make().listGames(gameId)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Consumer<SpeedrunMiddlewareAPI.APIResponse<Game>>() {
                    @Override
                    public void accept(SpeedrunMiddlewareAPI.APIResponse<Game> gameAPIResponse) throws Exception {

                        if (gameAPIResponse.data.isEmpty()) {
                            // game was not able to be found for some reason?
                            Util.showErrorToast(getContext(), getString(R.string.error_missing_game, gameId));
                            return;
                        }

                        mGame = gameAPIResponse.data.get(0);
                        setViewData();
                    }
                }, new Consumer<Throwable>() {
                    @Override
                    public void accept(Throwable throwable) throws Exception {

                        Log.e(TAG, "Could not download game data:", throwable);

                        Util.showErrorToast(getContext(), getString(R.string.error_missing_game, gameId));
                    }
                });
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.game_detail, container, false);

        mGameName = rootView.findViewById(R.id.txtGameName);
        mReleaseDate = rootView.findViewById(R.id.txtReleaseDate);
        mPlatformList = rootView.findViewById(R.id.txtPlatforms);

        mCover = rootView.findViewById(R.id.imgCover);
        mBackground = rootView.findViewById(R.id.imgBackground);

        mLeaderboardPager = rootView.findViewById(R.id.pageLeaderboard);
        mCategoryTabStrip = rootView.findViewById(R.id.tabCategories);

        if(mGame != null)
            mCategoryTabStrip.setup(mGame, mLeaderboardPager, getChildFragmentManager());

        setViewData();

        return rootView;
    }

    private void setViewData() {
        if(mGame != null && mGameName != null) {
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

            // leaderboards
            if(mCategoryTabStrip != null)
                mCategoryTabStrip.setup(mGame, mLeaderboardPager, getChildFragmentManager());

            Context ctx;
            if((ctx = getContext()) != null) {
                if(mGame.assets.coverLarge != null)
                    new DownloadImageTask(ctx, mCover).clear(false).execute(mGame.assets.coverLarge.uri);

                if(mGame.assets.background != null && mBackground != null)
                    new DownloadImageTask(ctx, mBackground).execute(mGame.assets.background.uri);
            }
        }
    }
}
