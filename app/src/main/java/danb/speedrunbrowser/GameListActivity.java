package danb.speedrunbrowser;

import android.app.ActivityOptions;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.EditText;

import com.google.android.gms.security.ProviderInstaller;
import com.google.firebase.crash.FirebaseCrash;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentPagerAdapter;
import androidx.viewpager.widget.ViewPager;
import danb.speedrunbrowser.utils.Util;
import danb.speedrunbrowser.views.SimpleTabStrip;

/**
 * An activity representing a list of Games. This activity
 * has different presentations for handset and tablet-size devices. On
 * handsets, the activity presents a list of items, which when touched,
 * lead to a {@link GameDetailActivity} representing
 * item details. On tablets, the activity presents the list of items and
 * item details side-by-side using two vertical panes.
 */
public class GameListActivity extends AppCompatActivity implements TextWatcher, ItemListFragment.OnFragmentInteractionListener {
    private static final String TAG = GameListActivity.class.getSimpleName();

    private EditText mGameFilter;

    private SimpleTabStrip mTabs;
    private ViewPager mViewPager;

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

        mViewPager = findViewById(R.id.pager);
        mViewPager.setAdapter(new PagerAdapter(getSupportFragmentManager()));

        mTabs = findViewById(R.id.tabsType);
        mTabs.setup(mViewPager);

        mGameFilter.addTextChangedListener(this);
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

        if(mViewPager.getAdapter() != null)
            ((PagerAdapter)mViewPager.getAdapter()).setSearchFilter(q);
    }

    private void showGame(String id, Fragment fragment, ActivityOptions transitionOptions) {
        if(isTwoPane()) {
            Bundle arguments = new Bundle();
            arguments.putString(GameDetailFragment.ARG_GAME_ID, id);

            GameDetailFragment newFrag = new GameDetailFragment();
            newFrag.setArguments(arguments);

            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.game_detail_container, fragment)
                    .commit();
        }
        else {
            Intent intent = new Intent(this, GameDetailActivity.class);
            intent.putExtra(GameDetailFragment.ARG_GAME_ID, id);

            startActivityFromFragment(fragment, intent, 0, transitionOptions.toBundle());

        }
    }

    private void showPlayer(String id, Fragment fragment, ActivityOptions transitionOptions) {
        Intent intent = new Intent(this, PlayerDetailActivity.class);
        intent.putExtra(PlayerDetailActivity.ARG_PLAYER_ID, id);
        startActivityFromFragment(fragment, intent, 0, transitionOptions.toBundle());
    }

    @Override
    public void onItemSelected(ItemListFragment.ItemType itemType, String itemId, Fragment fragment, ActivityOptions transitionOptions) {
        switch(itemType) {
            case GAMES:
                showGame(itemId, fragment, transitionOptions);
                break;
            case PLAYERS:
                showPlayer(itemId, fragment, transitionOptions);
                break;
        }
    }

    private class PagerAdapter extends FragmentPagerAdapter implements SimpleTabStrip.IconPagerAdapter {

        private ItemListFragment[] fragments;

        public PagerAdapter(@NonNull FragmentManager fm) {
            super(fm);

            fragments = new ItemListFragment[3];

            fragments[0] = new ItemListFragment();
            Bundle args = new Bundle();
            args.putSerializable(ItemListFragment.ARG_ITEM_TYPE, ItemListFragment.ItemType.GAMES);
            fragments[0].setArguments(args);

            fragments[1] = new ItemListFragment();
            args = new Bundle();
            args.putSerializable(ItemListFragment.ARG_ITEM_TYPE, ItemListFragment.ItemType.PLAYERS);
            fragments[1].setArguments(args);

            fragments[2] = new ItemListFragment();
            args = new Bundle();
            args.putSerializable(ItemListFragment.ARG_ITEM_TYPE, ItemListFragment.ItemType.RUNS);
            fragments[2].setArguments(args);
        }

        @NonNull
        @Override
        public Fragment getItem(int position) {
            return fragments[position];
        }

        @Override
        public int getCount() {
            return fragments.length;
        }

        @Nullable
        @Override
        public CharSequence getPageTitle(int position) {
            switch(position) {
                case 0:
                    return getString(R.string.title_tab_games);
                case 1:
                    return getString(R.string.title_tab_players);
                case 2:
                    return getString(R.string.title_tab_latest_runs);
                default:
                    return "";
            }
        }

        @Override
        public Drawable getPageIcon(int position) {
            switch(position) {
                case 0:
                    return getResources().getDrawable(R.drawable.baseline_videogame_asset_24);
                case 1:
                    return getResources().getDrawable(R.drawable.baseline_person_24);
                case 2:
                    return getResources().getDrawable(R.drawable.baseline_play_circle_filled_24);
                default:
                    return null;
            }
        }

        public void setSearchFilter(String searchFilter) {
            for(ItemListFragment frag : fragments) {
                frag.setSearchFilter(searchFilter);
            }
        }
    }
}
