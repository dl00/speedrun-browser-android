package danb.speedrunbrowser;

import android.content.Context;
import androidx.annotation.NonNull;

import android.content.DialogInterface;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.viewpager.widget.ViewPager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import danb.speedrunbrowser.api.SpeedrunMiddlewareAPI;
import danb.speedrunbrowser.api.objects.Category;
import danb.speedrunbrowser.api.objects.Game;
import danb.speedrunbrowser.api.objects.Level;
import danb.speedrunbrowser.api.objects.Variable;
import danb.speedrunbrowser.utils.Analytics;
import danb.speedrunbrowser.utils.AppDatabase;
import danb.speedrunbrowser.utils.ConnectionErrorConsumer;
import danb.speedrunbrowser.utils.ImageLoader;
import danb.speedrunbrowser.utils.ImageViewPlacerConsumer;
import danb.speedrunbrowser.utils.LeaderboardPagerAdapter;
import danb.speedrunbrowser.utils.SubscriptionChanger;
import danb.speedrunbrowser.utils.Util;
import danb.speedrunbrowser.views.CategoryTabStrip;
import danb.speedrunbrowser.views.ProgressSpinnerView;
import io.reactivex.CompletableSource;
import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Action;
import io.reactivex.functions.Consumer;
import io.reactivex.functions.Function;
import io.reactivex.schedulers.Schedulers;

/**
 * A fragment representing a single Game detail screen.
 * This fragment is either contained in a {@link GameListActivity}
 * in two-pane mode (on tablets) or a {@link ItemDetailActivity}
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
     * Saved state options
     */
    private static final String SAVED_GAME = "game";
    private static final String SAVED_PAGER = "pager";

    private AppDatabase mDB;

    /**
     * The dummy content this fragment is presenting.
     */
    private Game mGame;
    private Variable.VariableSelections mVariableSelections;
    private GameSubscription mSubscription;

    private Menu mMenu;

    /**
     * Game detail view views
     */
    private ProgressSpinnerView mSpinner;
    private View mGameHeader;

    private CompositeDisposable mDisposables;

    private TextView mReleaseDate;
    private TextView mPlatformList;

    private Button mFiltersButton;

    private ImageView mCover;
    private ImageView mBackground;

    private CategoryTabStrip mCategoryTabStrip;
    private ViewPager mLeaderboardPager;

    private Category mStartPositionCategory;
    private Level mStartPositionLevel;

    /**
     * Mandatory empty constructor for the fragment manager to instantiate the
     * fragment (e.g. upon screen orientation changes).
     */
    public GameDetailFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setHasOptionsMenu(true);

        mDB = AppDatabase.make(getContext());

        mDisposables = new CompositeDisposable();

        if(getArguments() == null) {
            Log.e(TAG, "No arguments provided");
            return;
        }

        if (getArguments().containsKey(ARG_GAME_ID) && savedInstanceState == null) {
            // Load the dummy content specified by the fragment
            // arguments. In a real-world scenario, use a Loader
            // to load content from a content provider.

            final String gameId = getArguments().getString(ARG_GAME_ID);
            loadGame(gameId);
        }
        else {
            mGame = (Game)savedInstanceState.getSerializable(SAVED_GAME);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mDisposables.dispose();
        mDB.close();
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        inflater.inflate(R.menu.game, menu);
        mMenu = menu;
        setMenu();
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if(item.getItemId() == R.id.menu_subscribe) {
            openSubscriptionDialog();
            return true;
        }

        return false;
    }

    private void setMenu() {
        if(mMenu == null)
            return;

        MenuItem subscribeItem = mMenu.findItem(R.id.menu_subscribe);
        subscribeItem.setVisible(mSubscription != null);
        if(mSubscription != null && !mSubscription.isEmpty()) {
            subscribeItem.setIcon(R.drawable.baseline_star_24);
            subscribeItem.setTitle(R.string.menu_unsubscribe);
        }
        else {
            subscribeItem.setIcon(R.drawable.baseline_star_border_24);
            subscribeItem.setTitle(R.string.menu_subscribe);
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
                        loadSubscription();
                        setViewData();

                        Analytics.logItemView(getContext(), "game", gameId);

                    }
                }, new ConnectionErrorConsumer(getContext()));
    }

    private void loadSubscription() {
        System.out.println("Load subscription " + mGame.id);
        mDisposables.add(mDB.subscriptionDao().listOfTypeWithIDPrefix("game", mGame.id)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Consumer<List<AppDatabase.Subscription>>() {
                    @Override
                    public void accept(List<AppDatabase.Subscription> subscriptions) throws Exception {
                        System.out.println(subscriptions);
                        mSubscription = new GameSubscription(mGame, subscriptions);
                        setMenu();
                    }
                }));
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        if(savedInstanceState != null) {
            mLeaderboardPager.onRestoreInstanceState(savedInstanceState.getParcelable(SAVED_PAGER));

            Log.d(TAG, "Loaded from saved instance state");
        }
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.game_detail, container, false);

        mSpinner = rootView.findViewById(R.id.spinner);
        mGameHeader = rootView.findViewById(R.id.gameInfoHead);

        mReleaseDate = rootView.findViewById(R.id.txtReleaseDate);
        mPlatformList = rootView.findViewById(R.id.txtPlatforms);

        mFiltersButton = rootView.findViewById(R.id.filtersButton);

        mCover = rootView.findViewById(R.id.imgCover);
        mBackground = rootView.findViewById(R.id.imgBackground);

        mLeaderboardPager = rootView.findViewById(R.id.pageLeaderboard);

        mCategoryTabStrip = rootView.findViewById(R.id.tabCategories);

        if(mGame != null) {
            setupTabStrip();
        }

        mFiltersButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openFiltersDialog();
            }
        });

        setViewData();

        return rootView;
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);

        LeaderboardPagerAdapter pa = mCategoryTabStrip.getPagerAdapter();

        outState.putParcelable(SAVED_PAGER, mLeaderboardPager.onSaveInstanceState());
        outState.putSerializable(SAVED_GAME, mGame);
    }

    private void setupTabStrip() {
        if(mGame.categories.get(0).variables.isEmpty())
            mFiltersButton.setVisibility(View.GONE);
        else if(mVariableSelections == null)
            mVariableSelections = new Variable.VariableSelections();

        mCategoryTabStrip.setup(mGame, mVariableSelections, mLeaderboardPager, getChildFragmentManager());

        if(mStartPositionCategory != null)
            mCategoryTabStrip.selectLeaderboard(mStartPositionCategory, mStartPositionLevel);
    }

    private void setViewData() {
        if(mGame != null) {

            Objects.requireNonNull(getActivity()).setTitle(mGame.getName());

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
            if(mCategoryTabStrip != null) {
                setupTabStrip();
            }

            Context ctx;
            if((ctx = getContext()) != null) {

                ImageLoader il = new ImageLoader(ctx);

                if(mGame.assets.coverLarge != null)
                    mDisposables.add(il.loadImage(mGame.assets.coverLarge.uri)
                        .subscribe(new ImageViewPlacerConsumer(mCover)));
                if(mGame.assets.background != null && mBackground != null)
                    mDisposables.add(il.loadImage(mGame.assets.background.uri)
                            .subscribe(new ImageViewPlacerConsumer(mBackground)));
            }

            mSpinner.setVisibility(View.GONE);
            mGameHeader.setVisibility(View.VISIBLE);

        }
    }

    private void openFiltersDialog() {
        FiltersDialog dialog = new FiltersDialog(getContext(),
                mCategoryTabStrip.getPagerAdapter().getCategoryOfIndex(mLeaderboardPager.getCurrentItem()).variables, mVariableSelections);

        dialog.show();

        dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialog) {
                mCategoryTabStrip.getPagerAdapter().notifyFilterChanged();
            }
        });
    }

    private void openSubscriptionDialog() {

        final GameSubscribeDialog dialog = new GameSubscribeDialog(getContext(), (GameSubscription)mSubscription.clone());

        dialog.show();

        dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface _dialog) {

                final Set<AppDatabase.Subscription> newSubs = dialog.getSubscriptions().getBaseSubscriptions();
                Set<AppDatabase.Subscription> oldSubs = mSubscription.getBaseSubscriptions();
                Set<AppDatabase.Subscription> delSubs = new HashSet<>(oldSubs);

                delSubs.removeAll(newSubs);
                newSubs.removeAll(oldSubs);

                System.out.println("Started the subscription changer: " + newSubs + ", " + delSubs);

                if(newSubs.isEmpty() && delSubs.isEmpty()) {
                    // no change
                    return;
                }

                ProgressSpinnerView psv = new ProgressSpinnerView(getContext(), null);
                psv.setDirection(ProgressSpinnerView.Direction.RIGHT);
                psv.setScale(0.5f);

                mMenu.findItem(R.id.menu_subscribe).setActionView(psv);

                final SubscriptionChanger sc = new SubscriptionChanger(getContext(), mDB);

                // change all the subscriptions async in one go
                mDisposables.add(Observable.fromIterable(delSubs)
                        .flatMapCompletable(new Function<AppDatabase.Subscription, CompletableSource>() {
                            @Override
                            public CompletableSource apply(AppDatabase.Subscription subscription) throws Exception {
                                return sc.unsubscribeFrom(subscription);
                            }
                        })
                        .mergeWith(Observable.fromIterable(newSubs)
                            .flatMapCompletable(new Function<AppDatabase.Subscription, CompletableSource>() {
                                @Override
                                public CompletableSource apply(AppDatabase.Subscription subscription) throws Exception {
                                    return sc.subscribeTo(subscription);
                                }
                            }))
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(new Action() {
                            @Override
                            public void run() throws Exception {
                                mMenu.findItem(R.id.menu_subscribe).setActionView(null);
                                Util.showMsgToast(getContext(), getString(R.string.success_subscription));
                                mSubscription = dialog.getSubscriptions();
                                setMenu();
                            }
                        }));
            }
        });
    }

    public static class GameSubscription extends HashSet<String> {
        private Game game;

        public GameSubscription(Game game) {
            this.game = game;
        }

        public GameSubscription(Game game, Collection<AppDatabase.Subscription> subs) {
            this.game = game;

            for(AppDatabase.Subscription sub : subs) {
                add(sub.resourceId.substring(sub.resourceId.indexOf('_') + 1));
            }
        }

        public Set<AppDatabase.Subscription> getBaseSubscriptions() {

            Set<AppDatabase.Subscription> subs = new HashSet<>(size());

            for(String sub : this) {
                subs.add(new AppDatabase.Subscription("game", game.id + "_" + sub, game.getName().toLowerCase()));
            }

            return subs;
        }

        public Game getGame() {
            return game;
        }
    }
}
