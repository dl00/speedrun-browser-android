package danb.speedrunbrowser;

import android.app.Activity;
import android.app.ActivityOptions;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;
import danb.speedrunbrowser.api.SpeedrunMiddlewareAPI;
import danb.speedrunbrowser.api.objects.Game;
import danb.speedrunbrowser.api.objects.LeaderboardRunEntry;
import danb.speedrunbrowser.api.objects.User;
import danb.speedrunbrowser.holders.GameCoverViewHolder;
import danb.speedrunbrowser.holders.PlayerViewHolder;
import danb.speedrunbrowser.holders.WatchRunViewHolder;
import danb.speedrunbrowser.utils.AppDatabase;
import danb.speedrunbrowser.utils.ConnectionErrorConsumer;
import io.reactivex.Observable;
import io.reactivex.ObservableSource;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.functions.Consumer;
import io.reactivex.functions.Function;
import io.reactivex.functions.Predicate;
import io.reactivex.subjects.PublishSubject;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;


public class ItemListFragment extends Fragment {
    // TODO: Rename parameter arguments, choose names that match
    // the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
    public static final String ARG_ITEM_TYPE = "item_type";

    private static final String SAVED_SEARCH_FILTER = "search_filter";
    private static final String SAVED_SHOW_SUBSCRIBED = "show_subscribed";
    private static final int DEBOUNCE_SEARCH_DELAY = 500;

    private String mSearchFilter = "";
    private ItemType mItemType;
    private boolean mShowSubscribed;

    private CompositeDisposable mDisposables;
    private AppDatabase mDB;

    private OnFragmentInteractionListener mListener;

    private ItemListAdapter mAdapter;

    private RecyclerView mSearchItemsView;

    private PublishSubject<String> mGameFilterSearchSubject;

    public ItemListFragment() {
        super();
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mDisposables = new CompositeDisposable();
        mSearchFilter = "";

        if(savedInstanceState != null) {
            mSearchFilter = savedInstanceState.getString(SAVED_SEARCH_FILTER);
            mShowSubscribed = savedInstanceState.getBoolean(SAVED_SHOW_SUBSCRIBED);
        }

        Bundle args = getArguments();

        if (args != null) {
            mItemType = (ItemType)args.getSerializable(ARG_ITEM_TYPE);
        }

        mGameFilterSearchSubject = PublishSubject.create();

        mDisposables.add(mGameFilterSearchSubject
            .distinctUntilChanged()
            .filter(new Predicate<String>() {
                @Override
                public boolean test(String s) throws Exception {
                    return s != null && (s.isEmpty() || s.length() >= SpeedrunMiddlewareAPI.MIN_AUTOCOMPLETE_LENGTH);
                }
            })
            .throttleLatest(DEBOUNCE_SEARCH_DELAY, TimeUnit.MILLISECONDS)
            .subscribe(new Consumer<String>() {
                @Override
                public void accept(String res) throws Exception {
                    mSearchFilter = res;
                    downloadItems();
                }
            }));

    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View v =  inflater.inflate(mItemType.layout, container, false);

        mSearchItemsView = v.findViewById(R.id.listSearchItems);

        mAdapter = new ItemListAdapter(getContext(), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(mListener != null) {
                    String id = "";
                    switch(mItemType) {
                        case GAMES:
                            id = ((Game)v.getTag()).id;
                            break;
                        case PLAYERS:
                            id = ((User)v.getTag()).id;
                            break;
                    }

                    mListener.onItemSelected(mItemType, id, ItemListFragment.this,
                            mItemType.makeSceneTransition(getActivity(), v));
                }
            }
        });
        mSearchItemsView.setAdapter(mAdapter);

        downloadItems();

        return v;
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        if (context instanceof OnFragmentInteractionListener) {
            mListener = (OnFragmentInteractionListener) context;
        } else {
            throw new RuntimeException(context.toString()
                    + " must implement OnFragmentInteractionListener");
        }


        mDB = AppDatabase.make(context);
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mListener = null;
        mDB.close();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mDisposables.dispose();
    }

    private void downloadItems() {

        ItemSource itemSource;

        if(mShowSubscribed) {
            itemSource = new ItemSource() {
                @Override
                public Observable<SpeedrunMiddlewareAPI.APIResponse<Object>> list(final int offset) {
                    return mDB.subscriptionDao()
                        .listOfType(mItemType.name)
                        .flatMapObservable(new Function<List<AppDatabase.Subscription>, ObservableSource<SpeedrunMiddlewareAPI.APIResponse<Object>>>() {
                            @Override
                            public ObservableSource<SpeedrunMiddlewareAPI.APIResponse<Object>> apply(List<AppDatabase.Subscription> subscriptions) throws Exception {

                                if(subscriptions.isEmpty())
                                    return Observable.just(new SpeedrunMiddlewareAPI.APIResponse<>());

                                StringBuilder builder = new StringBuilder(subscriptions.size());
                                for(AppDatabase.Subscription sub : subscriptions)
                                    builder.append(sub);

                                return mItemType.listRequest(builder.toString(), offset);
                            }
                        });
                }
            };
        }
        else if(!mSearchFilter.isEmpty()) {
            itemSource = new ItemSource() {
                @Override
                public Observable<SpeedrunMiddlewareAPI.APIResponse<Object>> list(int offset) {
                    return SpeedrunMiddlewareAPI.make().autocomplete(mSearchFilter)
                        .map(new Function<SpeedrunMiddlewareAPI.APISearchResponse, SpeedrunMiddlewareAPI.APIResponse<Object>>() {
                            @Override
                            public SpeedrunMiddlewareAPI.APIResponse<Object> apply(SpeedrunMiddlewareAPI.APISearchResponse apiSearchResponse) throws Exception {
                                SpeedrunMiddlewareAPI.APIResponse<Object> res = new SpeedrunMiddlewareAPI.APIResponse<>();

                                switch(mItemType) {
                                    case GAMES:
                                        if(apiSearchResponse.search.games != null)
                                            res.data.addAll(apiSearchResponse.search.games);
                                        break;
                                    case PLAYERS:
                                        if(apiSearchResponse.search.players != null)
                                            res.data.addAll(apiSearchResponse.search.players);
                                        break;
                                    default:
                                        break; // leave empty for now
                                }

                                return res;
                            }
                        });
                }
            };
        }
        else {
            itemSource = new ItemSource() {
                @Override
                public Observable<SpeedrunMiddlewareAPI.APIResponse<Object>> list(int offset) {
                    return mItemType.listRequest("", offset);
                }
            };
        }

        mAdapter.setItemsSource(itemSource);
    }

    public void setSearchFilter(String newSearchFilter) {
        if(mGameFilterSearchSubject != null)
            mGameFilterSearchSubject.onNext(newSearchFilter);
    }

    public String getSearchFilter() {
        return mSearchFilter;
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);

        outState.putString(SAVED_SEARCH_FILTER, mSearchFilter);
    }

    public class ItemListAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

        private final Context ctx;

        private final LayoutInflater inflater;

        private final View.OnClickListener onClickListener;

        private ItemSource itemSource;

        private List<Object> items;

        public ItemListAdapter(Context ctx, View.OnClickListener onClickListener) {
            this.ctx = ctx;
            this.items = new ArrayList<>();
            this.onClickListener = onClickListener;

            inflater = (LayoutInflater)ctx.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return mItemType.newViewHolder(getContext(), parent);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            mItemType.applyToViewHolder(getContext(), holder, items.get(position));
            holder.itemView.setOnClickListener(onClickListener);
            holder.itemView.setTag(items.get(position));
        }

        @Override
        public int getItemCount() {
            return items != null ? items.size() : 0;
        }

        public void setItems(List<Object> items) {
            this.items = items;
            notifyDataSetChanged();
        }

        public void setItemsSource(ItemSource source) {
            itemSource = source;

            mDisposables.add(itemSource.list(0)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Consumer<SpeedrunMiddlewareAPI.APIResponse<Object>>() {
                    @Override
                    public void accept(SpeedrunMiddlewareAPI.APIResponse<Object> objectAPIResponse) throws Exception {
                        items = objectAPIResponse.data;
                        notifyDataSetChanged();
                    }
                }, new ConnectionErrorConsumer(getContext())));
        }
    }

    public interface ItemSource {
        Observable<SpeedrunMiddlewareAPI.APIResponse<Object>> list(int offset);
    }

    public interface OnFragmentInteractionListener {
        // TODO: Update argument type and name
        void onItemSelected(ItemType itemType, String itemId, Fragment fragment, ActivityOptions options);
    }

    public enum ItemType implements Serializable {
        GAMES("games", R.layout.fragment_game_list),
        PLAYERS("players", R.layout.fragment_player_list),
        RUNS("runs", R.layout.fragment_run_list);

        public final String name;
        public final int layout;

        ItemType(String name, int layout) {
            this.name = name;
            this.layout = layout;
        }

        public RecyclerView.ViewHolder newViewHolder(Context ctx, ViewGroup parent) {
            switch(this) {
                case GAMES:
                    return new GameCoverViewHolder(((LayoutInflater)ctx.getSystemService(Context.LAYOUT_INFLATER_SERVICE)).inflate(R.layout.content_game_cover, parent, false));
                case PLAYERS:
                    return new PlayerViewHolder(((LayoutInflater)ctx.getSystemService(Context.LAYOUT_INFLATER_SERVICE)).inflate(R.layout.content_player_list, parent, false));
                case RUNS:
                    return new WatchRunViewHolder(((LayoutInflater)ctx.getSystemService(Context.LAYOUT_INFLATER_SERVICE)).inflate(R.layout.content_watch_list, parent, false));
                default:
                    // should never happen
                    return new GameCoverViewHolder(View.inflate(ctx, R.layout.content_game_cover, parent));
            }
        }

        public void applyToViewHolder(Context ctx, RecyclerView.ViewHolder holder, Object toApply) {
            switch(this) {
                case GAMES:
                    ((GameCoverViewHolder)holder).apply(ctx, (Game)toApply);
                    break;
                case PLAYERS:
                    ((PlayerViewHolder)holder).apply(ctx, (User)toApply, false);
                    break;
                case RUNS:
                    ((WatchRunViewHolder)holder).apply(ctx, ((LeaderboardRunEntry)toApply).run.game, (LeaderboardRunEntry)toApply);
                    break;
            }
        }

        public ActivityOptions makeSceneTransition(Activity activity, View v) {

            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                View transitionView;
                switch (this) {
                    case GAMES:
                        transitionView = v.findViewById(R.id.imgGameCover);
                        transitionView.setTransitionName(activity.getString(R.string.transition_feature_img));
                        return ActivityOptions.makeSceneTransitionAnimation(activity, transitionView, activity.getString(R.string.transition_feature_img));
                    case PLAYERS:
                        transitionView = v.findViewById(R.id.imgPlayerIcon);
                        transitionView.setTransitionName(activity.getString(R.string.transition_feature_img));
                        return ActivityOptions.makeSceneTransitionAnimation(activity, transitionView, activity.getString(R.string.transition_feature_img));
                }
            }

            return null;
        }

        public Observable<SpeedrunMiddlewareAPI.APIResponse<Object>> listRequest(String ids, int offset) {
            switch(this) {
                case GAMES:
                    if(ids == null || ids.isEmpty())
                        return SpeedrunMiddlewareAPI.make().listGames(offset).map(new GenericMapper<Game>());
                    else
                        return SpeedrunMiddlewareAPI.make().listGames(ids).map(new GenericMapper<Game>());

                case PLAYERS:
                    if(ids == null || ids.isEmpty())
                        return Observable.just(new SpeedrunMiddlewareAPI.APIResponse<Object>());
                    else
                        return SpeedrunMiddlewareAPI.make().listPlayers(ids).map(new GenericMapper<User>());
                case RUNS:
                    if(ids == null || ids.isEmpty())
                        return SpeedrunMiddlewareAPI.make().listLatestRuns(offset).map(new GenericMapper<LeaderboardRunEntry>());
                default:
                    return null;
            }
        }

        @Override
        public String toString() {
            return name;
        }

        private class GenericMapper<T> implements Function<SpeedrunMiddlewareAPI.APIResponse<T>, SpeedrunMiddlewareAPI.APIResponse<Object>> {

            @Override
            public SpeedrunMiddlewareAPI.APIResponse<Object> apply(SpeedrunMiddlewareAPI.APIResponse<T> res) throws Exception {
                SpeedrunMiddlewareAPI.APIResponse<Object> obj = new SpeedrunMiddlewareAPI.APIResponse<>();
                obj.data.addAll(res.data);
                obj.error = res.error;
                return obj;
            }
        }
    }
}
