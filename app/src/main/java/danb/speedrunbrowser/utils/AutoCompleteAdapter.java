package danb.speedrunbrowser.utils;

import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.concurrent.TimeUnit;

import androidx.annotation.NonNull;
import danb.speedrunbrowser.R;
import danb.speedrunbrowser.api.SpeedrunMiddlewareAPI;
import danb.speedrunbrowser.api.objects.SearchResultItem;
import danb.speedrunbrowser.api.objects.User;
import io.reactivex.Observable;
import io.reactivex.ObservableSource;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.functions.Consumer;
import io.reactivex.functions.Function;
import io.reactivex.functions.Predicate;
import io.reactivex.subjects.Subject;

public class AutoCompleteAdapter extends BaseAdapter implements Consumer<SpeedrunMiddlewareAPI.APISearchResponse> {
    private static final String TAG = AutoCompleteAdapter.class.getSimpleName();

    private static final int DEBOUNCE_SEARCH_DELAY = 500;

    private Context ctx;

    private CompositeDisposable disposables;

    private String query;

    private SpeedrunMiddlewareAPI.APISearchData rawSearchData;

    private List<SearchResultItem> searchResults;

    public AutoCompleteAdapter(@NonNull Context context, CompositeDisposable disposables) {
        ctx = context;
        this.disposables = disposables;
        searchResults = new ArrayList<>();
    }

    public void setSearchQuery(String q) {
        this.query = q.toLowerCase();
    }

    public void recalculateSearchResults() {
        searchResults = new LinkedList<>();

        searchResults.addAll(rawSearchData.getGames());

        if(!searchResults.isEmpty()) {
            ListIterator<SearchResultItem> sr = searchResults.listIterator();
            SearchResultItem cur = sr.next();
            for(User player : rawSearchData.getPlayers()) {
                // select the longest matching substring
                LCSMatcher lcsp = new LCSMatcher(query, player.getResolvedName().toLowerCase(), 3);

                LCSMatcher lcsg;
                do {
                    lcsg = new LCSMatcher(query, cur.getResolvedName().toLowerCase(), 3);
                    System.out.println("LCSG (" + cur.getResolvedName() + ", " + player.getResolvedName() + "): " + lcsg.getMaxMatchLength() + ", " + lcsp.getMaxMatchLength());
                } while(lcsg.getMaxMatchLength() >= lcsp.getMaxMatchLength() && sr.hasNext() && (cur = sr.next()) != null);

                sr.previous();
                sr.add(player);
                sr.next();
            }
        }

        searchResults.addAll(rawSearchData.getPlayers());

        searchResults = new ArrayList<>(searchResults);

        notifyDataSetInvalidated();
    }

    @Override
    public int getCount() {
        return searchResults.size();
    }

    @Override
    public Object getItem(int position) {
        return searchResults.get(position);
    }

    @Override
    public long getItemId(int position) {
        return 0;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if(convertView == null)
            convertView = ((LayoutInflater)ctx.getSystemService(Context.LAYOUT_INFLATER_SERVICE)).inflate(R.layout.content_named_autocomplete_item, parent, false);

        SearchResultItem item = searchResults.get(position);

        ImageView viewIcon = convertView.findViewById(R.id.imgItemIcon);
        LinearLayout viewName = convertView.findViewById(R.id.txtItemName);
        TextView viewType = convertView.findViewById(R.id.txtItemType);

        URL iconUrl;
        if((iconUrl = item.getIconUrl()) != null) {
            disposables.add(new ImageLoader(ctx).loadImage(iconUrl)
                    .subscribe(new ImageViewPlacerConsumer(viewIcon)));
        }

        viewName.removeAllViews();

        TextView tv = new TextView(ctx);
        item.applyTextView(tv);
        viewName.addView(tv);

        viewType.setText(item.getType());

        return convertView;
    }

    public void setPublishSubject(Subject<String> subj) {

        Observable<String> obs = subj
                .distinctUntilChanged()
                .filter(new Predicate<String>() {
                    @Override
                    public boolean test(String s) throws Exception {
                        return s != null && (s.isEmpty() || s.length() >= SpeedrunMiddlewareAPI.INSTANCE.getMIN_AUTOCOMPLETE_LENGTH());
                    }
                });

        disposables.add(obs.subscribe(new Consumer<String>() {
            @Override
            public void accept(String s) throws Exception {
                query = s;
            }
        }));

        disposables.add(obs
            .throttleLatest(DEBOUNCE_SEARCH_DELAY, TimeUnit.MILLISECONDS)
            .switchMap(new Function<String, ObservableSource<SpeedrunMiddlewareAPI.APISearchResponse>>() {
                @Override
                public ObservableSource<SpeedrunMiddlewareAPI.APISearchResponse> apply(String s) throws Exception {
                    if(s.length() < SpeedrunMiddlewareAPI.INSTANCE.getMIN_AUTOCOMPLETE_LENGTH())
                        return Observable.just(new SpeedrunMiddlewareAPI.APISearchResponse());
                    else
                        return SpeedrunMiddlewareAPI.INSTANCE.make().autocomplete(s);
                }
            })
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(this));
    }

    @Override
    public void accept(SpeedrunMiddlewareAPI.APISearchResponse apiSearchResponse) {
        // TODO: Handle error

        rawSearchData = apiSearchResponse.getSearch();
        recalculateSearchResults();
    }
}
