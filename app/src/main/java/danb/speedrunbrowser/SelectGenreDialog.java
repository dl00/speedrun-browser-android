package danb.speedrunbrowser;

import android.app.AlertDialog;
import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
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
import java.util.Locale;
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

    private ScrollView mGenreListScroller;

    private Genre mSelectedGenre;

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
                if(s.length() >= SpeedrunMiddlewareAPI.MIN_AUTOCOMPLETE_LENGTH)
                    triggerSearchGenres(s.toString());
                else
                    triggerSearchGenres("");
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
        layout.addView(searchView);

        mGenreListScroller = new ScrollView(getContext());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, getContext().getResources().getDimensionPixelSize(R.dimen.dialog_list_min_height));
        params.weight = 1;
        mGenreListScroller.setLayoutParams(params);
        mGenreListScroller.setFillViewport(true);

        ListView lv = new ListView(getContext());
        params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0);
        params.weight = 1;
        lv.setLayoutParams(params);
        mGenreAdapter = new GenreArrayAdapter(getContext());
        lv.setAdapter(mGenreAdapter);

        lv.setOnItemClickListener(this);

        //mGenreListScroller.addView(lv);

        //layout.addView(mGenreListScroller);
        layout.addView(lv);

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

        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
        getWindow().setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);

        InputMethodManager inputMananger = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        inputMananger.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0);

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
                        //((ListView)mGenreListScroller.getChildAt(0)).scrollTo(0, 0);
                    }
                }, new ConnectionErrorConsumer(getContext())));
    }

    @Override
    public void onClick(View v) {
        dismiss();
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        mSelectedGenre = mGenreAdapter.getItem(position);

        // TODO: Save this genre somewhere so that the result can be picked up by the listener

        dismiss();
    }

    public Genre getSelectedGenre() {
        return mSelectedGenre;
    }

    private class GenreArrayAdapter extends ArrayAdapter<Genre> {
        public GenreArrayAdapter(@NonNull Context context) {
            super(context, R.layout.content_named_autocomplete_item);
        }

        @Nullable
        @Override
        public Genre getItem(int position) {
            if(position == 0)
                return null;

            return super.getItem(position - 1);
        }

        @NonNull
        @Override
        public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            if(convertView == null) {
                convertView = getLayoutInflater().inflate(R.layout.content_named_autocomplete_item, parent, false);
            }

            View unusedImage = convertView.findViewById(R.id.imgItemIcon);
            unusedImage.setVisibility(View.GONE);

            LinearLayout titleLayout = convertView.findViewById(R.id.txtItemName);
            TextView titleTv = new TextView(getContext());
            TextView countTv = convertView.findViewById(R.id.txtItemType);

            Genre data = getItem(position);

            if(data != null) {
                titleTv.setText(data.name);
                countTv.setText(String.format(Locale.US, "%d", data.count));
            }
            else {
                titleTv.setText(R.string.label_all_genres);
                countTv.setText("");
            }

            titleLayout.removeAllViews();
            titleLayout.addView(titleTv);

            convertView.setMinimumHeight(getContext().getResources().getDimensionPixelSize(R.dimen.genre_list_item_height));

            return convertView;
        }
    }
}
