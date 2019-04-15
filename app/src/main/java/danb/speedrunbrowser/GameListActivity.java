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
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.ListView;

import com.google.android.gms.security.ProviderInstaller;
import com.google.firebase.crash.FirebaseCrash;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentPagerAdapter;
import androidx.viewpager.widget.ViewPager;
import danb.speedrunbrowser.api.SpeedrunMiddlewareAPI;
import danb.speedrunbrowser.api.objects.Game;
import danb.speedrunbrowser.api.objects.LeaderboardRunEntry;
import danb.speedrunbrowser.api.objects.User;
import danb.speedrunbrowser.utils.AppDatabase;
import danb.speedrunbrowser.utils.AutoCompleteAdapter;
import danb.speedrunbrowser.utils.Util;
import danb.speedrunbrowser.views.SimpleTabStrip;
import io.reactivex.Observable;
import io.reactivex.ObservableSource;
import io.reactivex.Single;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.functions.Function;
import io.reactivex.schedulers.Schedulers;
import io.reactivex.subjects.PublishSubject;

/**
 * An activity representing a list of Games. This activity
 * has different presentations for handset and tablet-size devices. On
 * handsets, the activity presents a list of items, which when touched,
 * lead to a {@link ItemDetailActivity} representing
 * item details. On tablets, the activity presents the list of items and
 * item details side-by-side using two vertical panes.
 */
public class GameListActivity extends AppCompatActivity implements TextWatcher, ItemListFragment.OnFragmentInteractionListener, AdapterView.OnItemClickListener {
    private static final String TAG = GameListActivity.class.getSimpleName();

    private static final String SAVED_MAIN_PAGER = "main_pager";

    private AppDatabase mDB;

    private PublishSubject<String> mGameFilterSearchSubject;

    private EditText mGameFilter;
    private ListView mAutoCompleteResults;

    private SimpleTabStrip mTabs;
    private ViewPager mViewPager;

    private CompositeDisposable mDisposables;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_game_list);

        mDisposables = new CompositeDisposable();

        mDB = AppDatabase.make(this);

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
        mAutoCompleteResults = findViewById(R.id.listAutoCompleteResults);

        final PagerAdapter pagerAdapter = new PagerAdapter(getSupportFragmentManager());

        mViewPager.setAdapter(pagerAdapter);

        mTabs = findViewById(R.id.tabsType);
        mTabs.setup(mViewPager);

        mGameFilter.addTextChangedListener(this);

        mGameFilterSearchSubject = PublishSubject.create();

        AutoCompleteAdapter autoCompleteAdapter = new AutoCompleteAdapter(this, mDisposables);
        autoCompleteAdapter.setPublishSubject(mGameFilterSearchSubject);
        mAutoCompleteResults.setAdapter(autoCompleteAdapter);
        mAutoCompleteResults.setOnItemClickListener(this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mDisposables.dispose();
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

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);

        outState.putParcelable(SAVED_MAIN_PAGER, mViewPager.onSaveInstanceState());
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);

        if(savedInstanceState != null) {
            mViewPager.onRestoreInstanceState(savedInstanceState.getParcelable(SAVED_MAIN_PAGER));
        }
    }

    @Override
    public void onBackPressed() {
        if(mGameFilter.getText().length() == 0)
            finish();
        else
            mGameFilter.setText("");
    }

    protected boolean isTwoPane() {
        // The detail container view will be present only in the
        // large-screen layouts (res/values-w900dp).
        // If this view is present, then the
        // activity should be in two-pane mode.
        return findViewById(R.id.detail_container) != null;
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
        mGameFilterSearchSubject.onNext(q);
        mAutoCompleteResults.setVisibility(q.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void showGame(String id, Fragment fragment, ActivityOptions transitionOptions) {
        if(isTwoPane()) {
            Bundle arguments = new Bundle();
            arguments.putString(GameDetailFragment.ARG_GAME_ID, id);

            GameDetailFragment newFrag = new GameDetailFragment();
            newFrag.setArguments(arguments);

            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.detail_container, newFrag)
                    .commit();
        }
        else {
            Intent intent = new Intent(this, ItemDetailActivity.class);
            intent.putExtra(ItemDetailActivity.EXTRA_ITEM_TYPE, ItemListFragment.ItemType.GAMES);
            intent.putExtra(GameDetailFragment.ARG_GAME_ID, id);

            if(fragment != null && transitionOptions != null)
                startActivityFromFragment(fragment, intent, 0, transitionOptions.toBundle());
            else
                startActivity(intent);

        }
    }

    private void showPlayer(String id, Fragment fragment, ActivityOptions transitionOptions) {
        if(isTwoPane()) {
            Bundle arguments = new Bundle();
            arguments.putString(PlayerDetailFragment.ARG_PLAYER_ID, id);

            PlayerDetailFragment newFrag = new PlayerDetailFragment();
            newFrag.setArguments(arguments);

            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.detail_container, newFrag)
                    .commit();
        }
        else {
            Intent intent = new Intent(this, ItemDetailActivity.class);
            intent.putExtra(ItemDetailActivity.EXTRA_ITEM_TYPE, ItemListFragment.ItemType.PLAYERS);
            intent.putExtra(PlayerDetailFragment.ARG_PLAYER_ID, id);

            if(fragment != null && transitionOptions != null)
                startActivityFromFragment(fragment, intent, 0, transitionOptions.toBundle());
            else
                startActivity(intent);
        }
    }

    private void showRun(String id, Fragment fragment, ActivityOptions transitionOptions) {
        Intent intent = new Intent(this, RunDetailActivity.class);
        intent.putExtra(RunDetailActivity.EXTRA_RUN_ID, id);

        if(fragment != null && transitionOptions != null)
            startActivityFromFragment(fragment, intent, 0, transitionOptions.toBundle());
        else
            startActivity(intent);
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
            case RUNS:
                showRun(itemId, fragment, transitionOptions);
                break;
        }
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        Object item = parent.getAdapter().getItem(position);
        System.out.println(item);
        if(item instanceof User) {
            showPlayer(((User)item).id, null, null);
        }
        else if(item instanceof Game) {
            showGame(((Game)item).id, null, null);
        }
    }

    private class PagerAdapter extends FragmentPagerAdapter implements SimpleTabStrip.IconPagerAdapter {

        private ItemListFragment[] fragments;

        public PagerAdapter(@NonNull FragmentManager fm) {
            super(fm);

            fragments = new ItemListFragment[5];

            fragments[0] = new ItemListFragment();
            Bundle args = new Bundle();
            args.putSerializable(ItemListFragment.ARG_ITEM_TYPE, ItemListFragment.ItemType.GAMES);
            fragments[0].setArguments(args);

            fragments[1] = new ItemListFragment();
            args = new Bundle();
            args.putSerializable(ItemListFragment.ARG_ITEM_TYPE, ItemListFragment.ItemType.RUNS);
            fragments[1].setArguments(args);

            fragments[2] = new ItemListFragment();
            args = new Bundle();
            args.putSerializable(ItemListFragment.ARG_ITEM_TYPE, ItemListFragment.ItemType.RUNS);
            fragments[2].setArguments(args);

            fragments[3] = new ItemListFragment();
            args = new Bundle();
            args.putSerializable(ItemListFragment.ARG_ITEM_TYPE, ItemListFragment.ItemType.GAMES);
            fragments[3].setArguments(args);

            fragments[4] = new ItemListFragment();
            args = new Bundle();
            args.putSerializable(ItemListFragment.ARG_ITEM_TYPE, ItemListFragment.ItemType.PLAYERS);
            fragments[4].setArguments(args);

            for(int i = 0;i < getCount();i++)
                initializePage(i);
        }

        @NonNull
        @Override
        public Fragment getItem(int position) {
            return fragments[position];
        }

        @Override
        public void setPrimaryItem(@NonNull ViewGroup container, int position, @NonNull Object object) {
            if(fragments[position] != object) {
                fragments[position] = ((ItemListFragment)object);
                initializePage(position);
            }

            super.setPrimaryItem(container, position, object);
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
                    return getString(R.string.title_tab_latest_runs);
                case 2:
                    return getString(R.string.title_tab_recently_watched);
                case 3:
                    return getString(R.string.title_tab_subscribed_games);
                case 4:
                    return getString(R.string.title_tab_subscribed_players);
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
                    return getResources().getDrawable(R.drawable.baseline_play_circle_filled_24);
                case 2:
                    return getResources().getDrawable(R.drawable.baseline_list_24);
                case 3:
                    return getResources().getDrawable(R.drawable.baseline_videogame_asset_24);
                case 4:
                    return getResources().getDrawable(R.drawable.baseline_person_24);
                default:
                    return null;
            }
        }

        private void initializePage(int position) {

            switch(position) {
                case 0:
                    fragments[0].setItemsSource(new ItemListFragment.ItemSource() {
                        @Override
                        public Observable<SpeedrunMiddlewareAPI.APIResponse<Object>> list(int offset) {
                            return SpeedrunMiddlewareAPI.make().listGames(offset).map(new ItemListFragment.GenericMapper<Game>());
                        }
                    });
                    break;
                case 1:
                    fragments[1].setItemsSource(new ItemListFragment.ItemSource() {
                        @Override
                        public Observable<SpeedrunMiddlewareAPI.APIResponse<Object>> list(int offset) {
                            return SpeedrunMiddlewareAPI.make().listLatestRuns(offset).map(new ItemListFragment.GenericMapper<LeaderboardRunEntry>());
                        }
                    });
                    break;
                case 2:
                    fragments[2].setItemsSource(new ItemListFragment.ItemSource() {
                        @Override
                        public Observable<SpeedrunMiddlewareAPI.APIResponse<Object>> list(int offset) {
                            Single<List<AppDatabase.WatchHistoryEntry>> entries = mDB.watchHistoryDao()
                                    .getMany(offset)
                                    .subscribeOn(Schedulers.io());

                            return entries.flatMapObservable(new Function<List<AppDatabase.WatchHistoryEntry>, ObservableSource<SpeedrunMiddlewareAPI.APIResponse<Object>>>() {
                                @Override
                                public ObservableSource<SpeedrunMiddlewareAPI.APIResponse<Object>> apply(List<AppDatabase.WatchHistoryEntry> entries) throws Exception {

                                    if(entries.isEmpty())
                                        return Observable.just(new SpeedrunMiddlewareAPI.APIResponse<>());

                                    StringBuilder builder = new StringBuilder(entries.size());
                                    for(AppDatabase.WatchHistoryEntry whe : entries) {
                                        if(builder.length() != 0)
                                            builder.append(",");
                                        builder.append(whe.runId);
                                    }

                                    return SpeedrunMiddlewareAPI.make().listRuns(builder.toString()).map(new ItemListFragment.GenericMapper<LeaderboardRunEntry>());
                                }
                            });
                        }
                    });
                    break;
                case 3:
                    fragments[3].setItemsSource(new ItemListFragment.ItemSource() {
                        @Override
                        public Observable<SpeedrunMiddlewareAPI.APIResponse<Object>> list(int offset) {

                            // TODO: hack for now
                            if(offset != 0)
                                return Observable.just(new SpeedrunMiddlewareAPI.APIResponse<>());

                            Single<List<AppDatabase.Subscription>> subs = mDB.subscriptionDao()
                                    .listOfType("game", offset);

                            return subs.flatMapObservable(new Function<List<AppDatabase.Subscription>, ObservableSource<SpeedrunMiddlewareAPI.APIResponse<Object>>>() {
                                @Override
                                public ObservableSource<SpeedrunMiddlewareAPI.APIResponse<Object>> apply(List<AppDatabase.Subscription> subscriptions) throws Exception {

                                    if(subscriptions.isEmpty())
                                        return Observable.just(new SpeedrunMiddlewareAPI.APIResponse<>());

                                    List<String> ids = new ArrayList<>();

                                    for(AppDatabase.Subscription sub : subscriptions) {
                                        System.out.println(sub.getFCMTopic());
                                        String id = sub.resourceId.substring(0, sub.resourceId.indexOf('_'));

                                        if(!ids.isEmpty() && id.equals(ids.get(ids.size() - 1)))
                                            continue;

                                        ids.add(id);
                                    }

                                    StringBuilder builder = new StringBuilder(subscriptions.size());
                                    for(String id : ids) {
                                        if(builder.length() != 0)
                                            builder.append(",");
                                        builder.append(id);
                                    }

                                    return SpeedrunMiddlewareAPI.make().listGames(builder.toString()).map(new ItemListFragment.GenericMapper<Game>());
                                }
                            });
                        }
                    });
                    break;
                case 4:
                    fragments[4].setItemsSource(new ItemListFragment.ItemSource() {
                        @Override
                        public Observable<SpeedrunMiddlewareAPI.APIResponse<Object>> list(int offset) {
                            Single<List<AppDatabase.Subscription>> subs = mDB.subscriptionDao()
                                    .listOfType("player", offset);

                            return subs.flatMapObservable(new Function<List<AppDatabase.Subscription>, ObservableSource<SpeedrunMiddlewareAPI.APIResponse<Object>>>() {
                                @Override
                                public ObservableSource<SpeedrunMiddlewareAPI.APIResponse<Object>> apply(List<AppDatabase.Subscription> subscriptions) throws Exception {

                                    if(subscriptions.isEmpty())
                                        return Observable.just(new SpeedrunMiddlewareAPI.APIResponse<>());

                                    StringBuilder builder = new StringBuilder(subscriptions.size());
                                    for(AppDatabase.Subscription sub : subscriptions) {
                                        if(builder.length() != 0)
                                            builder.append(",");
                                        builder.append(sub.resourceId);
                                    }

                                    return SpeedrunMiddlewareAPI.make().listPlayers(builder.toString()).map(new ItemListFragment.GenericMapper<User>());
                                }
                            });
                        }
                    });
                    break;
            }
        }
    }
}
