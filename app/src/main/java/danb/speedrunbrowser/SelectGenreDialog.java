package danb.speedrunbrowser;

import android.app.AlertDialog;
import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.lang.reflect.Array;
import java.util.List;
import java.util.Objects;

import danb.speedrunbrowser.api.SpeedrunMiddlewareAPI;
import danb.speedrunbrowser.api.objects.Game;
import danb.speedrunbrowser.api.objects.Genre;
import danb.speedrunbrowser.api.objects.Platform;
import danb.speedrunbrowser.api.objects.Region;
import danb.speedrunbrowser.api.objects.Variable;
import danb.speedrunbrowser.utils.ConnectionErrorConsumer;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.functions.Consumer;
import io.reactivex.schedulers.Schedulers;

public class SelectGenreDialog extends AlertDialog implements View.OnClickListener, AdapterView.OnItemClickListener {

    private CompositeDisposable mDisposables;

    private GenreArrayAdapter mGenreAdapter;

    public SelectGenreDialog(Context ctx, CompositeDisposable disposables) {
        super(ctx, AlertDialog.THEME_DEVICE_DEFAULT_DARK);

        mDisposables = disposables;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout layout = new LinearLayout(getContext());
        layout.setOrientation(LinearLayout.VERTICAL);

        TextView titleText = new TextView(getContext());


        layout.addView(titleText);

        EditText searchView = new EditText(getContext());
        searchView.setHint(R.string.enter_genre_filter);
        searchView.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                triggerSearchGenres(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
        layout.addView(searchView);

        ScrollView scrollView = new ScrollView(getContext());

        ListView lv = new ListView(getContext());
        mGenreAdapter = new GenreArrayAdapter(getContext());
        lv.setAdapter(mGenreAdapter);

        lv.setOnItemClickListener(this);

        scrollView.addView(lv);

        layout.addView(scrollView);

        Button cancelButton = new Button(getContext());
        cancelButton.setText(android.R.string.cancel);
        LinearLayout.LayoutParams cancelButtonLP = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        cancelButtonLP.weight = 0;
        cancelButton.setLayoutParams(cancelButtonLP);

        cancelButton.setOnClickListener(this);

        layout.addView(cancelButton);

        triggerSearchGenres("");

        setContentView(layout);
    }

    private void triggerSearchGenres(String query) {
        mDisposables.add(SpeedrunMiddlewareAPI.make().listGenres(query)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Consumer<SpeedrunMiddlewareAPI.APIResponse<Genre>>() {
                    @Override
                    public void accept(SpeedrunMiddlewareAPI.APIResponse<Genre> genreAPIResponse) throws Exception {
                        mGenreAdapter.clear();
                        mGenreAdapter.addAll(genreAPIResponse.data);
                        mGenreAdapter.notifyDataSetChanged();
                    }
                }, new ConnectionErrorConsumer(getContext())));
    }

    @Override
    public void onClick(View v) {
        dismiss();
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        Genre genre = mGenreAdapter.getItem(position);

        // TODO: Save this genre somewhere so that the result can be picked up by the listener

        dismiss();
    }

    private class GenreArrayAdapter extends ArrayAdapter<Genre> {
        public GenreArrayAdapter(@NonNull Context context) {
            super(context, R.layout.content_named_autocomplete_item);
        }

        @NonNull
        @Override
        public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            if(convertView == null) {
                convertView = getLayoutInflater().inflate(R.layout.content_named_autocomplete_item, parent, false);
            }

            TextView titleTv = convertView.findViewById(R.id.txtItemName);
            TextView countTv = convertView.findViewById(R.id.txtItemType);

            Genre data = Objects.requireNonNull(getItem(position));

            titleTv.setText(data.name);
            countTv.setText(data.count);

            return convertView;
        }
    }
}
