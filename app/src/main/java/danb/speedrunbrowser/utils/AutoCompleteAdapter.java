package danb.speedrunbrowser.utils;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

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
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Consumer;
import io.reactivex.functions.Function;
import io.reactivex.functions.Predicate;
import io.reactivex.subjects.Subject;

public class AutoCompleteAdapter extends BaseAdapter implements Consumer<SpeedrunMiddlewareAPI.APISearchResponse> {

    private static final int DEBOUNCE_SEARCH_DELAY = 500;

    private Context ctx;

    private Disposable disposableRequest;

    private String query;

    private List<SearchResultItem> searchResults;

    public AutoCompleteAdapter(@NonNull Context context) {
        ctx = context;
        searchResults = new ArrayList<>();
    }

    public void setSearchData(@NonNull String q, @NonNull SpeedrunMiddlewareAPI.APISearchData sd) {
        searchResults = new LinkedList<>();

        searchResults.addAll(sd.games);

        if(!searchResults.isEmpty()) {
            ListIterator<SearchResultItem> sr = searchResults.listIterator();
            SearchResultItem cur = sr.next();
            for(User player : sd.players) {
                // select the longest matching substring
                //String lcsp = longestCommonSubstring(q, player.getName());

                //String lcsg;
                //do {
                //    lcsg = longestCommonSubstring(q, cur.getName());
                //} while((cur = sr.next()) != null && lcsg.length() > lcsp.length());

                //sr.previous();
                sr.add(player);
            }
        }

        searchResults.addAll(sd.players);

        searchResults = new ArrayList<>(searchResults);

        notifyDataSetChanged();
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

        viewName.removeAllViews();

        TextView tv = new TextView(ctx);
        item.applyTextView(tv);
        viewName.addView(tv);

        viewType.setText(item.getTypeName());

        return convertView;
    }

    public void setPublishSubject(Subject<String> subj) {
        disposableRequest = subj
            .distinctUntilChanged()
            .filter(new Predicate<String>() {
                @Override
                public boolean test(String s) throws Exception {
                    return s != null && (s.isEmpty() || s.length() >= SpeedrunMiddlewareAPI.MIN_AUTOCOMPLETE_LENGTH);
                }
            })
            .debounce(DEBOUNCE_SEARCH_DELAY, TimeUnit.MILLISECONDS)
            .switchMap(new Function<String, ObservableSource<SpeedrunMiddlewareAPI.APISearchResponse>>() {
                @Override
                public ObservableSource<SpeedrunMiddlewareAPI.APISearchResponse> apply(String s) throws Exception {
                    if(s.length() < SpeedrunMiddlewareAPI.MIN_AUTOCOMPLETE_LENGTH)
                        return Observable.just(new SpeedrunMiddlewareAPI.APISearchResponse());
                    else
                        return SpeedrunMiddlewareAPI.make().autocomplete(s);
                }
            })
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(this);
    }

    @Override
    public void accept(SpeedrunMiddlewareAPI.APISearchResponse apiSearchResponse) {
        // TODO: Handle error

        setSearchData("", apiSearchResponse.search);
    }
}