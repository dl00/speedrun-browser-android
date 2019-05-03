package danb.speedrunbrowser;

import android.app.Activity;
import android.app.ActivityOptions;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import danb.speedrunbrowser.api.SpeedrunMiddlewareAPI;
import danb.speedrunbrowser.api.objects.Game;
import danb.speedrunbrowser.api.objects.LeaderboardRunEntry;
import danb.speedrunbrowser.api.objects.User;
import danb.speedrunbrowser.holders.GameCoverViewHolder;
import danb.speedrunbrowser.holders.PlayerViewHolder;
import danb.speedrunbrowser.holders.ProgressSpinnerViewHolder;
import danb.speedrunbrowser.holders.WatchRunViewHolder;
import danb.speedrunbrowser.utils.ConnectionErrorConsumer;
import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Consumer;
import io.reactivex.functions.Function;
import io.reactivex.schedulers.Schedulers;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;


public class ItemListFragment extends Fragment {
    // TODO: Rename parameter arguments, choose names that match
    // the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
    public static final String ARG_ITEM_TYPE = "item_type";

    private ItemType mItemType;

    private CompositeDisposable mDisposables;

    private OnFragmentInteractionListener mListener;

    private ItemListAdapter mAdapter;

    private RecyclerView mSearchItemsView;

    private View mEmptyView;

    private ItemSource mItemSource;

    public ItemListFragment() {
        super();
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mDisposables = new CompositeDisposable();


        Bundle args = getArguments();

        if (args != null) {
            mItemType = (ItemType)args.getSerializable(ARG_ITEM_TYPE);
        }

    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View v =  inflater.inflate(mItemType.layout, container, false);

        mSearchItemsView = v.findViewById(R.id.listSearchItems);
        mEmptyView = v.findViewById(R.id.empty);

        mAdapter = new ItemListAdapter(getContext(), mSearchItemsView, new View.OnClickListener() {
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
                        case RUNS:
                            id = ((LeaderboardRunEntry)v.getTag()).run.id;
                            break;
                    }

                    mListener.onItemSelected(mItemType, id, ItemListFragment.this,
                            mItemType.makeSceneTransition(getActivity(), v));
                }
            }
        });
        mSearchItemsView.setAdapter(mAdapter);
        if(mItemSource != null)
            mAdapter.loadListTop();

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
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mListener = null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mDisposables.dispose();
    }

    public void setItemsSource(ItemSource source) {
        mItemSource = source;
        reload();
    }

    public void reload() {
        if(mAdapter != null)
            mAdapter.loadListTop();
    }

    public ItemType getItemType() {
        return mItemType;
    }

    public class ItemListAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

        private final Context ctx;

        private final LayoutInflater inflater;

        private final View.OnClickListener onClickListener;

        private Disposable currentLoading;
        private boolean isAtEndOfList;

        private List<Object> items;

        public ItemListAdapter(Context ctx, final RecyclerView rv, View.OnClickListener onClickListener) {
            this.ctx = ctx;
            this.items = new ArrayList<>();
            this.onClickListener = onClickListener;

            inflater = (LayoutInflater)ctx.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

            rv.addOnScrollListener(new RecyclerView.OnScrollListener() {
                @Override
                public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                    RecyclerView.LayoutManager lm = rv.getLayoutManager();
                    int visibleItemPosition = 0;
                    if(lm instanceof LinearLayoutManager)
                        visibleItemPosition = ((LinearLayoutManager)lm).findLastVisibleItemPosition();

                    if(visibleItemPosition == items.size() - 1)
                        loadListMore();
                }
            });
        }

        @Override
        public int getItemViewType(int position) {
            if(items == null || items.size() > position)
                return 0;
            else
                return 1;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            if(viewType == 0)
                return mItemType.newViewHolder(getContext(), parent);
            else
                return new ProgressSpinnerViewHolder(getContext());
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {

            if(getItemViewType(position) == 0) {
                mItemType.applyToViewHolder(getContext(), mDisposables, holder, items.get(position));
                holder.itemView.setOnClickListener(onClickListener);
                holder.itemView.setTag(items.get(position));
            }
        }

        @Override
        public int getItemCount() {
            int count = items != null ? items.size() : 0;

            if(currentLoading != null)
                count++;

            return count;
        }

        public void loadListTop() {

            if(currentLoading != null)
                currentLoading.dispose();

            currentLoading = mItemSource.list(0)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(new Consumer<SpeedrunMiddlewareAPI.APIResponse<Object>>() {
                        @Override
                        public void accept(SpeedrunMiddlewareAPI.APIResponse<Object> objectAPIResponse) throws Exception {
                            currentLoading = null;
                            items = objectAPIResponse.data;
                            isAtEndOfList = items.isEmpty();

                            if(isAtEndOfList)
                                mEmptyView.setVisibility(View.VISIBLE);
                            else
                                mEmptyView.setVisibility(View.GONE);

                            notifyDataSetChanged();
                        }
                    }, new ConnectionErrorConsumer(getContext()));
            notifyDataSetChanged();
        }

        public void loadListMore() {

            if(isAtEndOfList || currentLoading != null)
                return;

            currentLoading = mItemSource.list(items.size())
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(new Consumer<SpeedrunMiddlewareAPI.APIResponse<Object>>() {
                        @Override
                        public void accept(SpeedrunMiddlewareAPI.APIResponse<Object> objectAPIResponse) throws Exception {
                            currentLoading = null;
                            if(objectAPIResponse.data.isEmpty()) {
                                isAtEndOfList = true;
                                notifyItemRemoved(items.size());
                            }
                            else {
                                int prevSize = items.size();
                                items.addAll(objectAPIResponse.data);
                                notifyItemChanged(prevSize);
                                notifyItemRangeInserted(prevSize + 1, objectAPIResponse.data.size() - 1);
                            }
                        }
                    }, new ConnectionErrorConsumer(getContext()));

            notifyItemChanged(items.size());
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

        public void applyToViewHolder(Context ctx, CompositeDisposable disposables, RecyclerView.ViewHolder holder, Object toApply) {
            switch(this) {
                case GAMES:
                    ((GameCoverViewHolder)holder).apply(ctx, disposables, (Game)toApply);
                    break;
                case PLAYERS:
                    ((PlayerViewHolder)holder).apply(ctx, disposables, (User)toApply, false);
                    break;
                case RUNS:
                    ((WatchRunViewHolder)holder).apply(ctx, disposables, ((LeaderboardRunEntry)toApply).run.game, (LeaderboardRunEntry)toApply);
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
                    case RUNS:
                        return ActivityOptions.makeSceneTransitionAnimation(activity, v, activity.getString(R.string.transition_feature_img));
                }
            }

            return null;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    public static class GenericMapper<T> implements Function<SpeedrunMiddlewareAPI.APIResponse<T>, SpeedrunMiddlewareAPI.APIResponse<Object>> {

        @Override
        public SpeedrunMiddlewareAPI.APIResponse<Object> apply(SpeedrunMiddlewareAPI.APIResponse<T> res) throws Exception {
            SpeedrunMiddlewareAPI.APIResponse<Object> obj = new SpeedrunMiddlewareAPI.APIResponse<>();
            obj.data.addAll(res.data);
            obj.error = res.error;
            return obj;
        }
    }
}
