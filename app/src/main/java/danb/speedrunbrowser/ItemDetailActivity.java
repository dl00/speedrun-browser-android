package danb.speedrunbrowser;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;

import java.util.List;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import danb.speedrunbrowser.api.SpeedrunMiddlewareAPI;
import danb.speedrunbrowser.api.objects.Run;
import danb.speedrunbrowser.api.objects.WhatIsEntry;
import danb.speedrunbrowser.utils.ConnectionErrorConsumer;
import danb.speedrunbrowser.utils.Util;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Consumer;
import io.reactivex.schedulers.Schedulers;

public class ItemDetailActivity extends AppCompatActivity implements Consumer<SpeedrunMiddlewareAPI.APIResponse<WhatIsEntry>> {
    private static final String TAG = ItemDetailActivity.class.getSimpleName();

    public static final String EXTRA_ITEM_TYPE = "item_type";

    private Disposable whatIsQuery;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_item_detail);

        // Show the Up button in the action bar.
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        // savedInstanceState is non-null when there is fragment state
        // saved from previous configurations of this activity
        // (e.g. when rotating the screen from portrait to landscape).
        // In this case, the fragment will automatically be re-added
        // to its container so we don't need to manually add it.
        // For more information, see the Fragments API guide at:
        //
        // http://developer.android.com/guide/components/fragments.html
        //
        if (savedInstanceState == null) {
            // Create the detail fragment and add it to the activity
            // using a fragment transaction.
            Bundle args = getIntent().getExtras();

            Intent intent = getIntent();

            ItemListFragment.ItemType type = (ItemListFragment.ItemType)args.getSerializable(EXTRA_ITEM_TYPE);

            Fragment frag = null;

            if(type != null) {
                switch(type) {
                    case GAMES:
                        frag = new GameDetailFragment();
                        break;
                    case PLAYERS:
                        frag = new PlayerDetailFragment();

                }
            }
            else if(intent.getData() != null) {
                List<String> segs = intent.getData().getPathSegments();

                String id = segs.get(segs.size() - 1);

                Log.d(TAG, "Decoded game or player ID: " + id + ", from URL: " + intent.getData());

                // use the whatis api to resolve the type of object
                whatIsQuery = SpeedrunMiddlewareAPI.make().whatAreThese(id)
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(this, new ConnectionErrorConsumer(this));
            }
            else {
                Log.w(TAG, "Could not find game ID argument");

                Util.showErrorToast(this, getString(R.string.error_could_not_find, "No data provided"));

                finish();
            }

            if(frag != null) {
                showFragment(frag, args);
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if(whatIsQuery != null)
            whatIsQuery.dispose();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) {
            // This ID represents the Home or Up button. In the case of this
            // activity, the Up button is shown. For
            // more details, see the Navigation pattern on Android Design:
            //
            // http://developer.android.com/design/patterns/navigation.html#up-vs-back
            //
            navigateUpTo(new Intent(this, GameListActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void accept(SpeedrunMiddlewareAPI.APIResponse<WhatIsEntry> whatIsEntryAPIResponse) throws Exception {

        Bundle args = new Bundle();
        Fragment frag = null;

        WhatIsEntry entry = whatIsEntryAPIResponse.data.get(0);

        if(entry != null) {
            if(entry.type.equals("game")) {
                args.putString(GameDetailFragment.ARG_GAME_ID, entry.id);
                frag = new GameDetailFragment();
            }
            else if(entry.type.equals("player")) {
                args.putString(PlayerDetailFragment.ARG_PLAYER_ID, entry.id);
                frag = new PlayerDetailFragment();
            }
            else if(entry.type.equals("run")) {
                // wrong activity
                showRun(entry.id);
            }
        }

        if(frag != null)
            showFragment(frag, args);
    }

    private void showRun(String id) {
        Intent intent = new Intent(this, RunDetailActivity.class);
        intent.putExtra(RunDetailActivity.EXTRA_RUN_ID, id);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
    }

    private void showFragment(Fragment frag, Bundle args) {
        frag.setArguments(args);
        getSupportFragmentManager().beginTransaction()
                .add(R.id.detail_container, frag)
                .commit();
    }
}
