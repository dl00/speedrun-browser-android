package danb.speedrunbrowser;

import android.support.annotation.NonNull;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Objects;

import danb.speedrunbrowser.api.SpeedrunAPI;
import danb.speedrunbrowser.api.objects.Game;
import danb.speedrunbrowser.utils.DownloadImageTask;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * A fragment representing a single Game detail screen.
 * This fragment is either contained in a {@link GameListActivity}
 * in two-pane mode (on tablets) or a {@link GameDetailActivity}
 * on handsets.
 */
public class GameDetailFragment extends Fragment implements Callback<SpeedrunAPI.APIResponse<Game>> {

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

            Log.d(TAG, "Downloading game data: " + getArguments().getString(ARG_GAME_ID));
            SpeedrunAPI.make().getGame(getArguments().getString(ARG_GAME_ID)).enqueue(this);
        }
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.game_detail, container, false);

        mGameName = rootView.findViewById(R.id.txtGameName);
        mReleaseDate = rootView.findViewById(R.id.txtReleaseDate);
        mPlatformList = rootView.findViewById(R.id.txtPlatforms);

        mCover = rootView.findViewById(R.id.imgCover);

        setViewData();

        return rootView;
    }

    private void setViewData() {
        if(mGame != null && mGameName != null) {
            mGameName.setText(mGame.getName());
            mReleaseDate.setText(mGame.releaseDate);

            // we have to join the string manually because it is java 7
            StringBuilder sb = new StringBuilder();
            for (String s : mGame.platforms)
            {
                sb.append(s);
                sb.append(", ");
            }

            mPlatformList.setText(sb.toString());

            try {
                new DownloadImageTask(mCover).clear(false).execute(new URL(mGame.assets.coverLarge.uri));
            } catch (MalformedURLException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void onResponse(@NonNull Call<SpeedrunAPI.APIResponse<Game>> call, @NonNull Response<SpeedrunAPI.APIResponse<Game>> response) {
        if (response.isSuccessful()) {
            mGame = Objects.requireNonNull(response.body()).data;
            setViewData();
        }
    }

    @Override
    public void onFailure(@NonNull Call<SpeedrunAPI.APIResponse<Game>> call, @NonNull Throwable t) {
        // TODO: Handle gracefully
        Log.w(TAG, "Could not download game info!", t);
    }
}
