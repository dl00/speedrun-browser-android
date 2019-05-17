package danb.speedrunbrowser;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.messaging.FirebaseMessaging;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import danb.speedrunbrowser.api.SpeedrunMiddlewareAPI;
import danb.speedrunbrowser.api.objects.LeaderboardRunEntry;
import danb.speedrunbrowser.api.objects.User;
import danb.speedrunbrowser.utils.Analytics;
import danb.speedrunbrowser.utils.AppDatabase;
import danb.speedrunbrowser.utils.ConnectionErrorConsumer;
import danb.speedrunbrowser.utils.Constants;
import danb.speedrunbrowser.utils.ImageLoader;
import danb.speedrunbrowser.utils.ImageViewPlacerConsumer;
import danb.speedrunbrowser.utils.SubscriptionChanger;
import danb.speedrunbrowser.utils.Util;
import danb.speedrunbrowser.views.ProgressSpinnerView;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.functions.Action;
import io.reactivex.functions.Consumer;
import io.reactivex.schedulers.Schedulers;

public class PlayerDetailFragment extends Fragment implements View.OnClickListener {
    private static final String TAG = PlayerDetailFragment.class.getSimpleName();

    public static final String ARG_PLAYER = "player";
    public static final String ARG_PLAYER_ID = "player_id";

    private CompositeDisposable mDisposables = new CompositeDisposable();

    private AppDatabase mDB;

    private User mPlayer;
    private AppDatabase.Subscription mSubscription;

    private Menu mMenu;

    private ProgressSpinnerView mSpinner;
    private View mPlayerHead;
    private View mScrollBests;
    private View mFrameBests;

    private ImageView mPlayerIcon;
    private TextView mPlayerName;

    private ImageView mIconTwitch;
    private ImageView mIconTwitter;
    private ImageView mIconYoutube;
    private ImageView mIconZSR;

    private LinearLayout mBestsFrame;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setHasOptionsMenu(true);

        mDB = AppDatabase.Companion.make(getContext());

        Objects.requireNonNull(getActivity()).setTitle(R.string.title_loading);

        Bundle args = getArguments();

        if(args != null && (mPlayer = (User)args.getSerializable(ARG_PLAYER)) != null) {
            loadSubscription(mPlayer.getId());
            setViewData();
        }
        else if(args != null && args.getString(ARG_PLAYER_ID) != null) {
            String pid = args.getString(ARG_PLAYER_ID);
            loadSubscription(pid);
            loadPlayer(pid);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        mDisposables.dispose();
        mDB.close();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {

        View rootView = inflater.inflate(R.layout.activity_player_detail, container, false);

        mSpinner = rootView.findViewById(R.id.spinner);
        mPlayerHead = rootView.findViewById(R.id.layoutPlayerHeader);
        mScrollBests = rootView.findViewById(R.id.scrollPlayerBests);
        mFrameBests = rootView.findViewById(R.id.framePlayerBests);

        mPlayerIcon = rootView.findViewById(R.id.imgAvatar);
        mPlayerName = rootView.findViewById(R.id.txtPlayerName);
        mIconTwitch = rootView.findViewById(R.id.iconTwitch);
        mIconTwitter = rootView.findViewById(R.id.iconTwitter);
        mIconYoutube = rootView.findViewById(R.id.iconYoutube);
        mIconZSR = rootView.findViewById(R.id.iconZSR);
        mBestsFrame = rootView.findViewById(R.id.bestsLayout);

        mIconTwitch.setOnClickListener(this);
        mIconTwitter.setOnClickListener(this);
        mIconYoutube.setOnClickListener(this);
        mIconZSR.setOnClickListener(this);

        setViewData();

        return rootView;
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        inflater.inflate(R.menu.player, menu);
        mMenu = menu;
        setMenu();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if(item.getItemId() == R.id.menu_subscribe) {
            toggleSubscribed();
            return true;
        }

        return false;
    }

    private void loadSubscription(final String playerId) {
        mDisposables.add(mDB.subscriptionDao().get(playerId)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(new Consumer<AppDatabase.Subscription>() {
                @Override
                public void accept(AppDatabase.Subscription subscription) throws Exception {
                    mSubscription = subscription;
                    setMenu();
                }
            }));
    }

    private void loadPlayer(final String playerId) {
        Log.d(TAG, "Download playerId: " + playerId);

        /// TODO: ideally this would be zipped/run in parallel
        mDisposables.add(SpeedrunMiddlewareAPI.INSTANCE.make().listPlayers(playerId)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(new Consumer<SpeedrunMiddlewareAPI.APIResponse<User>>() {
                @Override
                public void accept(SpeedrunMiddlewareAPI.APIResponse<User> gameAPIResponse) throws Exception {

                    if (gameAPIResponse.getData() == null || gameAPIResponse.getData().isEmpty()) {
                        // game was not able to be found for some reason?
                        Util.INSTANCE.showErrorToast(getContext(), getString(R.string.error_missing_game, playerId));
                        return;
                    }

                    mPlayer = gameAPIResponse.getData().get(0);

                    setViewData();


                    Analytics.INSTANCE.logItemView(getContext(), "player", playerId);

                }
            }, new ConnectionErrorConsumer(getContext())));
    }

    private boolean toggleSubscribed() {

        MenuItem subscribeMenuItem = mMenu.findItem(R.id.menu_subscribe);

        ProgressSpinnerView psv = new ProgressSpinnerView(getContext(), null);
        psv.setDirection(ProgressSpinnerView.Direction.RIGHT);
        psv.setScale(0.5f);

        subscribeMenuItem.setActionView(psv);
        final Consumer<Throwable> errorAction = new Consumer<Throwable>() {
            @Override
            public void accept(Throwable throwable) throws Exception {
                Log.w(TAG, "Could not add or remove subscription record from DB:", throwable);
            }
        };

        SubscriptionChanger sc = new SubscriptionChanger(getContext(), mDB);

        if(mSubscription != null) {
            mDisposables.add(sc.unsubscribeFrom(mSubscription)
                    .subscribe(new Action() {
                        @Override
                        public void run() throws Exception {
                            mSubscription = null;
                            setMenu();
                            Util.INSTANCE.showMsgToast(getContext(), getString(R.string.success_subscription));
                        }
                    }));

            return true;
        }
        else if(mPlayer != null) {
            mSubscription = new AppDatabase.Subscription("player", mPlayer.getId(), mPlayer.getName().toLowerCase());

            mDisposables.add(sc.subscribeTo(mSubscription)
                    .subscribe(new Action() {
                        @Override
                        public void run() throws Exception {
                            setMenu();
                            Util.INSTANCE.showMsgToast(getContext(), getString(R.string.success_subscription));
                        }
                    }));

            return true;
        }

        return false;
    }

    private void setMenu() {
        if(mMenu == null)
            return;

        MenuItem subscribeItem = mMenu.findItem(R.id.menu_subscribe);
        subscribeItem.setActionView(null);
        if(mSubscription != null) {
            subscribeItem.setIcon(R.drawable.baseline_star_24);
            subscribeItem.setTitle(R.string.menu_unsubscribe);
        }
        else {
            subscribeItem.setIcon(R.drawable.baseline_star_border_24);
            subscribeItem.setTitle(R.string.menu_subscribe);
        }
    }

    private void setViewData() {
        if(mPlayer != null) {
            ImageLoader il = new ImageLoader(Objects.requireNonNull(getContext()));

            Objects.requireNonNull(getActivity()).setTitle(mPlayer.getName());

            // find out if we are subscribed
            setMenu();

            mPlayer.applyTextView(mPlayerName);

            mIconTwitch.setVisibility(mPlayer.getTwitch() != null ? View.VISIBLE : View.GONE);
            mIconTwitter.setVisibility(mPlayer.getTwitter() != null ? View.VISIBLE : View.GONE);
            mIconYoutube.setVisibility(mPlayer.getYoutube() != null ? View.VISIBLE : View.GONE);
            mIconZSR.setVisibility(mPlayer.getSpeedrunslive() != null ? View.VISIBLE : View.GONE);


            if(!mPlayer.isGuest()) {
                try {
                    mBestsFrame.setVisibility(View.VISIBLE);
                    mDisposables.add(il.loadImage(new URL(String.format(Constants.AVATAR_IMG_LOCATION, mPlayer.getNames().get("international"))))
                            .subscribe(new ImageViewPlacerConsumer(mPlayerIcon)));
                } catch (MalformedURLException e) {
                    Log.w(TAG, "Chould not show player logo:", e);
                    mBestsFrame.setVisibility(View.GONE);
                }
            }
            else
                mBestsFrame.setVisibility(View.GONE);

            populateBestsFrame(il);

            mSpinner.setVisibility(View.GONE);
            mPlayerHead.setVisibility(View.VISIBLE);

            if(mScrollBests != null)
                mScrollBests.setVisibility(View.VISIBLE);
            if(mFrameBests != null)
                mFrameBests.setVisibility(View.VISIBLE);
        }
    }

    @SuppressLint("SetTextI18n")
    private void populateBestsFrame(ImageLoader il) {
        if(mPlayer.getBests() == null)
            return;

        List<User.UserGameBests> playerGameBests = new ArrayList<>(mPlayer.getBests().values());

        Collections.sort(playerGameBests, new Comparator<User.UserGameBests>() {
            @Override
            public int compare(User.UserGameBests o1, User.UserGameBests o2) {
                // find the min time
                LeaderboardRunEntry r1 = o1.getNewestRun();
                LeaderboardRunEntry r2 = o2.getNewestRun();

                if(r1 != null && r1.getRun().getDate() != null && r2 != null && r2.getRun().getDate() != null)
                    return -r1.getRun().getDate().compareTo(r2.getRun().getDate());
                else
                    return 0;
            }
        });

        for(User.UserGameBests gameBests : playerGameBests) {
            View gameLayout = getLayoutInflater().inflate(R.layout.content_game_personal_bests, null);

            ((TextView)gameLayout.findViewById(R.id.txtGameName)).setText(gameBests.getNames().get("international"));

            if(gameBests.getAssets().getCoverLarge() != null) {
                ImageView imgView = gameLayout.findViewById(R.id.imgGameCover);
                mDisposables.add(il.loadImage(gameBests.getAssets().getCoverLarge().getUri())
                    .subscribe(new ImageViewPlacerConsumer(imgView)));
            }

            List<PersonalBestRunRow> runsToAdd = new ArrayList<>();

            for(User.UserCategoryBest categoryBest : gameBests.getCategories().values()) {

                if(categoryBest.getLevels() != null && !categoryBest.getLevels().isEmpty()) {
                    for(User.UserLevelBest levelBest : categoryBest.getLevels().values()) {
                        PersonalBestRunRow rr = new PersonalBestRunRow(categoryBest.getName(), levelBest.getName(), levelBest.getRun());
                        runsToAdd.add(rr);
                    }
                }
                else {
                    PersonalBestRunRow rr = new PersonalBestRunRow(categoryBest.getName(), null, categoryBest.getRun());
                    runsToAdd.add(rr);
                }
            }

            // sort these runs by date, descending
            Collections.sort(runsToAdd, new Comparator<PersonalBestRunRow>() {
                @Override
                public int compare(PersonalBestRunRow o1, PersonalBestRunRow o2) {
                    if(o1.re.getRun().getDate() == null || o2.re.getRun().getDate() == null)
                        return 0;
                    return -o1.re.getRun().getDate().compareTo(o2.re.getRun().getDate());
                }
            });

            TableLayout bestTable = gameLayout.findViewById(R.id.tablePersonalBests);

            for(final PersonalBestRunRow row : runsToAdd) {
                TableRow rowPersonalBest = (TableRow)getLayoutInflater().inflate(R.layout.content_row_personal_best, null);
                ((TextView)rowPersonalBest.findViewById(R.id.txtRunCategory)).setText(row.label);

                ImageView placeImg = rowPersonalBest.findViewById(R.id.imgPlace);

                if(row.re.getPlace() == 1 && gameBests.getAssets().getTrophy1st() != null) {
                    mDisposables.add(il.loadImage(gameBests.getAssets().getTrophy1st().getUri())
                            .subscribe(new ImageViewPlacerConsumer(placeImg)));
                }
                if(row.re.getPlace() == 2 && gameBests.getAssets().getTrophy2nd() != null) {
                    mDisposables.add(il.loadImage(gameBests.getAssets().getTrophy2nd().getUri())
                            .subscribe(new ImageViewPlacerConsumer(placeImg)));
                }
                if(row.re.getPlace() == 3 && gameBests.getAssets().getTrophy3rd() != null) {
                    mDisposables.add(il.loadImage(gameBests.getAssets().getTrophy3rd().getUri())
                            .subscribe(new ImageViewPlacerConsumer(placeImg)));
                }
                if(row.re.getPlace() == 4 && gameBests.getAssets().getTrophy4th() != null) {
                    mDisposables.add(il.loadImage(gameBests.getAssets().getTrophy4th().getUri())
                            .subscribe(new ImageViewPlacerConsumer(placeImg)));
                }
                else
                    ((ImageView)placeImg).setImageDrawable(new ColorDrawable(Color.TRANSPARENT));

                ((TextView)rowPersonalBest.findViewById(R.id.txtPlace)).setText(row.re.getPlaceName());

                ((TextView)rowPersonalBest.findViewById(R.id.txtRunTime)).setText(row.re.getRun().getTimes().getTime());
                ((TextView)rowPersonalBest.findViewById(R.id.txtRunDate)).setText(row.re.getRun().getDate());

                rowPersonalBest.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        viewRun(row.re.getRun().getId());
                    }
                });

                bestTable.addView(rowPersonalBest);
            }

            mBestsFrame.addView(gameLayout);
        }
    }

    @Override
    public void onClick(View v) {

        URL selectedLink = null;

        if(v == mIconTwitch)
            selectedLink = mPlayer.getTwitch().getUri();
        if(v == mIconTwitter)
            selectedLink = mPlayer.getTwitter().getUri();
        if(v == mIconYoutube)
            selectedLink = mPlayer.getYoutube().getUri();
        if(v == mIconZSR)
            selectedLink = mPlayer.getSpeedrunslive().getUri();

        if(selectedLink != null) {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(selectedLink.toString()));
            startActivity(intent);
        }
    }

    private void viewRun(String runId) {
        Intent intent = new Intent(getContext(), RunDetailActivity.class);
        intent.putExtra(RunDetailActivity.EXTRA_RUN_ID, runId);
        startActivity(intent);
    }

    private static class PersonalBestRunRow {

        public String label;
        public LeaderboardRunEntry re;

        public PersonalBestRunRow(String categoryName, String levelName, LeaderboardRunEntry re) {

            if(levelName != null)
                label = categoryName + " - " + levelName;
            else
                label = categoryName;

            this.re = re;
        }
    }
}
