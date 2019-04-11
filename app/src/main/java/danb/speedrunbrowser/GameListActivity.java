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

import java.util.List;
import java.util.concurrent.TimeUnit;

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
import danb.speedrunbrowser.utils.Util;
import danb.speedrunbrowser.views.SimpleTabStrip;
import io.reactivex.Observable;
import io.reactivex.ObservableSource;
import io.reactivex.Single;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Consumer;
import io.reactivex.functions.Function;
import io.reactivex.functions.Predicate;
import io.reactivex.schedulers.Schedulers;
import io.reactivex.subjects.PublishSubject;

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

    private static final int DEBOUNCE_SEARCH_DELAY = 500;

    private AppDatabase mDB;

    private PublishSubject<String> mGameFilterSearchSubject;
    private Disposable mGameFilterDisposable;

    private EditText mGameFilter;

    private SimpleTabStrip mTabs;
    private ViewPager mViewPager;

    private boolean mShowSubscribed;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_game_list);

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

        final PagerAdapter adapter = new PagerAdapter(getSupportFragmentManager());

        mViewPager.setAdapter(adapter);

        mTabs = findViewById(R.id.tabsType);
        mTabs.setup(mViewPager);

        mGameFilter.addTextChangedListener(this);



        mGameFilterSearchSubject = PublishSubject.create();

        mGameFilterDisposable = mGameFilterSearchSubject
            .distinctUntilChanged()
            .filter(new Predicate<String>() {
                @Override
                public boolean test(String s) throws Exception {
                    return s != null && (s.isEmpty() || s.length() >= SpeedrunMiddlewareAPI.MIN_AUTOCOMPLETE_LENGTH);
                }
            })
            .throttleLatest(DEBOUNCE_SEARCH_DELAY, TimeUnit.MILLISECONDS)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(new Consumer<String>() {
                @Override
                public void accept(String res) throws Exception {
                    System.out.println("Add search filter...");
                    adapter.setSearchFilter(res);
                }
            });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        mGameFilterDisposable.dispose();
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
        mGameFilterSearchSubject.onNext(q);
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

    private void showRun(String id, Fragment fragment, ActivityOptions transitionOptions) {
        Intent intent = new Intent(this, RunDetailActivity.class);
        intent.putExtra(RunDetailActivity.EXTRA_RUN_ID, id);
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
            case RUNS:
                showRun(itemId, fragment, transitionOptions);
                break;
        }
    }

    private class PagerAdapter extends FragmentPagerAdapter implements SimpleTabStrip.IconPagerAdapter {

        private ItemListFragment[] fragments;

        public PagerAdapter(@NonNull FragmentManager fm) {
            super(fm);

            fragments = new ItemListFragment[4];

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

            fragments[3] = new ItemListFragment();
            args = new Bundle();
            args.putSerializable(ItemListFragment.ARG_ITEM_TYPE, ItemListFragment.ItemType.RUNS);
            fragments[3].setArguments(args);

            setSearchFilter("");
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
                case 3:
                    return getString(R.string.title_tab_recently_watched);
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
                case 3:
                    return getResources().getDrawable(R.drawable.baseline_list_24);
                default:
                    return null;
            }
        }

        public void setSearchFilter(final String searchFilter) {
            if(mShowSubscribed) {
                fragments[0].setItemsSource(new ItemListFragment.ItemSource() {
                    @Override
                    public Observable<SpeedrunMiddlewareAPI.APIResponse<Object>> list(int offset) {
                        Single<List<AppDatabase.Subscription>> subs = mDB.subscriptionDao()
                            .listOfType(ItemListFragment.ItemType.GAMES.name, offset);

                        if(searchFilter != null && !searchFilter.isEmpty())
                            subs = mDB.subscriptionDao()
                                    .listOfTypeWithFilter(ItemListFragment.ItemType.GAMES.name, searchFilter, offset);

                        return subs.flatMapObservable(new Function<List<AppDatabase.Subscription>, ObservableSource<SpeedrunMiddlewareAPI.APIResponse<Object>>>() {
                                @Override
                                public ObservableSource<SpeedrunMiddlewareAPI.APIResponse<Object>> apply(List<AppDatabase.Subscription> subscriptions) throws Exception {

                                    if(subscriptions.isEmpty())
                                        return Observable.just(new SpeedrunMiddlewareAPI.APIResponse<>());

                                    StringBuilder builder = new StringBuilder(subscriptions.size());
                                    for(AppDatabase.Subscription sub : subscriptions) {
                                        if(builder.length() != 0)
                                            builder.append(",");
                                        builder.append(sub);
                                    }

                                    return SpeedrunMiddlewareAPI.make().listGames(builder.toString()).map(new ItemListFragment.GenericMapper<Game>());
                                }
                            });
                    }
                });

                fragments[1].setItemsSource(new ItemListFragment.ItemSource() {
                    @Override
                    public Observable<SpeedrunMiddlewareAPI.APIResponse<Object>> list(int offset) {
                        Single<List<AppDatabase.Subscription>> subs = mDB.subscriptionDao()
                                .listOfType(ItemListFragment.ItemType.GAMES.name, offset);

                        if(searchFilter != null && !searchFilter.isEmpty())
                            subs = mDB.subscriptionDao()
                                    .listOfTypeWithFilter(ItemListFragment.ItemType.GAMES.name, searchFilter, offset);

                            return subs.flatMapObservable(new Function<List<AppDatabase.Subscription>, ObservableSource<SpeedrunMiddlewareAPI.APIResponse<Object>>>() {
                                    @Override
                                    public ObservableSource<SpeedrunMiddlewareAPI.APIResponse<Object>> apply(List<AppDatabase.Subscription> subscriptions) throws Exception {

                                        if(subscriptions.isEmpty())
                                            return Observable.just(new SpeedrunMiddlewareAPI.APIResponse<>());

                                        StringBuilder builder = new StringBuilder(subscriptions.size());
                                        for(AppDatabase.Subscription sub : subscriptions) {
                                            if(builder.length() != 0)
                                                builder.append(",");
                                            builder.append(sub);
                                        }

                                        return SpeedrunMiddlewareAPI.make().listPlayers(builder.toString()).map(new ItemListFragment.GenericMapper<User>());
                                    }
                                });
                    }
                });

                fragments[2].setItemsSource(new ItemListFragment.ItemSource() {
                    @Override
                    public Observable<SpeedrunMiddlewareAPI.APIResponse<Object>> list(int offset) {
                        return SpeedrunMiddlewareAPI.make().listLatestRuns(offset).map(new ItemListFragment.GenericMapper<LeaderboardRunEntry>());
                    }
                });
            }
            else {
                fragments[0].setItemsSource(new ItemListFragment.ItemSource() {
                    @Override
                    public Observable<SpeedrunMiddlewareAPI.APIResponse<Object>> list(int offset) {

                        if(searchFilter != null && !searchFilter.isEmpty())
                            return SpeedrunMiddlewareAPI.make().autocomplete(searchFilter)
                                .map(new Function<SpeedrunMiddlewareAPI.APISearchResponse, SpeedrunMiddlewareAPI.APIResponse<Object>>() {
                                    @Override
                                    public SpeedrunMiddlewareAPI.APIResponse<Object> apply(SpeedrunMiddlewareAPI.APISearchResponse apiSearchResponse) throws Exception {
                                        SpeedrunMiddlewareAPI.APIResponse<Object> res = new SpeedrunMiddlewareAPI.APIResponse<>();
                                        if(apiSearchResponse.search.games != null)
                                            res.data.addAll(apiSearchResponse.search.games);

                                        return res;
                                    }
                                });
                        else
                        return SpeedrunMiddlewareAPI.make().listGames(offset).map(new ItemListFragment.GenericMapper<Game>());
                    }
                });

                fragments[1].setItemsSource(new ItemListFragment.ItemSource() {
                    @Override
                    public Observable<SpeedrunMiddlewareAPI.APIResponse<Object>> list(int offset) {
                        if(searchFilter != null && !searchFilter.isEmpty())
                        return SpeedrunMiddlewareAPI.make().autocomplete(searchFilter)
                                .map(new Function<SpeedrunMiddlewareAPI.APISearchResponse, SpeedrunMiddlewareAPI.APIResponse<Object>>() {
                                    @Override
                                    public SpeedrunMiddlewareAPI.APIResponse<Object> apply(SpeedrunMiddlewareAPI.APISearchResponse apiSearchResponse) throws Exception {
                                        SpeedrunMiddlewareAPI.APIResponse<Object> res = new SpeedrunMiddlewareAPI.APIResponse<>();
                                        if(apiSearchResponse.search.games != null)
                                            res.data.addAll(apiSearchResponse.search.players);

                                        return res;
                                    }
                                });
                        else
                            return Observable.just(new SpeedrunMiddlewareAPI.APIResponse<>());
                    }
                });

                fragments[2].setItemsSource(new ItemListFragment.ItemSource() {
                    @Override
                    public Observable<SpeedrunMiddlewareAPI.APIResponse<Object>> list(int offset) {
                        return SpeedrunMiddlewareAPI.make().listLatestRuns(offset).map(new ItemListFragment.GenericMapper<LeaderboardRunEntry>());
                    }
                });
            }

            fragments[3].setItemsSource(new ItemListFragment.ItemSource() {
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
        }
    }
}
