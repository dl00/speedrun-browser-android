package danb.speedrunbrowser;

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;
import danb.speedrunbrowser.api.SpeedrunMiddlewareAPI;
import danb.speedrunbrowser.api.objects.Game;
import danb.speedrunbrowser.api.objects.User;
import danb.speedrunbrowser.holders.GameCoverViewHolder;
import danb.speedrunbrowser.holders.PlayerViewHolder;
import danb.speedrunbrowser.utils.AppDatabase;
import io.reactivex.Observable;
import io.reactivex.ObservableSource;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Consumer;
import io.reactivex.functions.Function;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;


public class ItemListFragment extends Fragment {
    // TODO: Rename parameter arguments, choose names that match
    // the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
    private static final String ARG_ITEM_TYPE = "item_type";
    private static final String ARG_SHOW_SUBSCRIBED = "show_subscribed";

    private static final String SAVED_SEARCH_FILTER = "search_filter";

    private String mSearchFilter;
    private ItemType mItemType;
    private boolean mShowSubscribed;

    private CompositeDisposable mDisposables;
    private AppDatabase mDB;

    private OnFragmentInteractionListener mListener;

    private List<Object> mSubscribedItems;
    private List<Object> mItems;

    private RecyclerView mSubscribedItemsView;
    private RecyclerView mSearchItemsView;

    public ItemListFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mDisposables = new CompositeDisposable();

        mSubscribedItems = new ArrayList<>();
        mItems = new ArrayList<>();

        if(savedInstanceState != null) {
            mSearchFilter = savedInstanceState.getString(mSearchFilter);
        }

        Bundle args = getArguments();

        if (args != null) {
            mShowSubscribed = args.getBoolean(ARG_SHOW_SUBSCRIBED);
            mItemType = (ItemType)args.getSerializable(ARG_ITEM_TYPE);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View v =  inflater.inflate(mItemType.layout, container, false);

        mSubscribedItemsView = v.findViewById(R.id.listSubscribedItems);
        mSearchItemsView = v.findViewById(R.id.listSearchItems);

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

        downloadSubscribedItems();
        downloadItems();
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

    private void downloadSubscribedItems() {
        mDisposables.add(mDB.subscriptionDao()
            .listOfType(mItemType.name)
            .flatMapObservable(new Function<List<AppDatabase.Subscription>, ObservableSource<SpeedrunMiddlewareAPI.APIResponse<Object>>>() {
                @Override
                public ObservableSource<SpeedrunMiddlewareAPI.APIResponse<Object>> apply(List<AppDatabase.Subscription> subscriptions) throws Exception {

                    if(subscriptions.isEmpty())
                        return Observable.just(new SpeedrunMiddlewareAPI.APIResponse<>());

                    StringBuilder builder = new StringBuilder(subscriptions.size());
                    for(AppDatabase.Subscription sub : subscriptions)
                        builder.append(sub);

                    return mItemType.listRequest(builder.toString());
                }
            })
            .subscribe(new Consumer<SpeedrunMiddlewareAPI.APIResponse<Object>>() {
                @Override
                public void accept(SpeedrunMiddlewareAPI.APIResponse<Object> res) throws Exception {
                    mSubscribedItems = res.data;
                    Objects.requireNonNull(mSubscribedItemsView.getAdapter()).notifyDataSetChanged();
                }
            }));
    }

    private void downloadItems() {

        Disposable d;

        if(!mSearchFilter.isEmpty()) {
            d = SpeedrunMiddlewareAPI.make().autocomplete(mSearchFilter)
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(new Consumer<SpeedrunMiddlewareAPI.APISearchResponse>() {
                        @Override
                        public void accept(SpeedrunMiddlewareAPI.APISearchResponse apiSearchResponse) throws Exception {
                            //apiSearchResponse.search.games
                        }
                    });

        }
        else {
            d = mItemType.listRequest("")
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(new Consumer<SpeedrunMiddlewareAPI.APIResponse<Object>>() {
                        @Override
                        public void accept(SpeedrunMiddlewareAPI.APIResponse<Object> res) throws Exception {
                            mItems = res.data;
                            Objects.requireNonNull(mSearchItemsView.getAdapter()).notifyDataSetChanged();
                        }
                    });
        }

        mDisposables.add(d);
    }

    public void setSearchFilter(String newSearchFilter) {
        mSearchFilter = newSearchFilter;
        downloadItems();
    }

    public String getSearchFilter() {
        return mSearchFilter;
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);

        outState.putString(SAVED_SEARCH_FILTER, mSearchFilter);
    }

    public void setViewData() {
        if(mShowSubscribed) {

        }
    }

    public class ItemListAdapter<T extends RecyclerView.ViewHolder> extends RecyclerView.Adapter<T> {

        private final Context ctx;

        private final LayoutInflater inflater;

        private final View.OnClickListener onClickListener;

        private final List<Object> items;

        public ItemListAdapter(Context ctx, List<Object> items, View.OnClickListener onClickListener) {
            this.ctx = ctx;
            this.items = items;
            this.onClickListener = onClickListener;

            inflater = (LayoutInflater)ctx.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        }

        @Override
        public T onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return (T)mItemType.newViewHolder(getContext(), parent);
        }

        @Override
        public void onBindViewHolder(@NonNull T holder, int position) {
            mItemType.applyToViewHolder(getContext(), holder, items.get(position));
            holder.itemView.setOnClickListener(onClickListener);
            holder.itemView.setTag(items.get(position));
        }

        @Override
        public int getItemCount() {
            return items != null ? items.size() : 0;
        }
    }

    public interface OnFragmentInteractionListener {
        // TODO: Update argument type and name
        void onItemSelected(ItemType itemType, String itemId);
    }

    public enum ItemType implements Serializable {
        GAMES("games", R.layout.fragment_game_list),
        PLAYERS("players", R.layout.fragment_player_list),
        RUNS("runs", 0);

        public final String name;
        public final int layout;

        ItemType(String name, int layout) {
            this.name = name;
            this.layout = layout;
        }

        public RecyclerView.ViewHolder newViewHolder(Context ctx, ViewGroup parent) {
            switch(this) {
                case GAMES:
                    return new GameCoverViewHolder(View.inflate(ctx, R.layout.content_game_cover, parent));
                case PLAYERS:
                    return new PlayerViewHolder(View.inflate(ctx, R.layout.content_player_list, parent));
                default:
                    return null;
            }
        }

        public void applyToViewHolder(Context ctx, RecyclerView.ViewHolder holder, Object toApply) {
            switch(this) {
                case GAMES:
                    ((GameCoverViewHolder)holder).apply(ctx, (Game)toApply);
                case PLAYERS:
                    ((PlayerViewHolder)holder).apply(ctx, (User)toApply);
            }
        }

        public Observable<SpeedrunMiddlewareAPI.APIResponse<Object>> listRequest(String ids) {
            switch(this) {
                case GAMES:
                    return SpeedrunMiddlewareAPI.make().listGames(ids).map(new GenericMapper<Game>());
                case PLAYERS:
                    return SpeedrunMiddlewareAPI.make().listPlayers(ids).map(new GenericMapper<User>());
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
                obj.data.add(res.data);
                obj.error = res.error;
                return obj;
            }
        }
    }
}
