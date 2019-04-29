package danb.speedrunbrowser;

import android.app.AlertDialog;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.util.List;
import java.util.Objects;

import danb.speedrunbrowser.api.objects.Game;
import danb.speedrunbrowser.api.objects.Platform;
import danb.speedrunbrowser.api.objects.Region;
import danb.speedrunbrowser.api.objects.Variable;

public class FiltersDialog extends AlertDialog implements CompoundButton.OnCheckedChangeListener, View.OnClickListener {

    private Game mGame;

    private List<Variable> mVariables;
    private Variable.VariableSelections mVariableSelections;

    public FiltersDialog(Context ctx, Game game, List<Variable> variables, Variable.VariableSelections variableSelections) {
        super(ctx, AlertDialog.THEME_DEVICE_DEFAULT_DARK);
        mGame = game;
        mVariables = variables;
        mVariableSelections = variableSelections;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout layout = new LinearLayout(getContext());
        layout.setOrientation(LinearLayout.VERTICAL);

        ScrollView scrollView = new ScrollView(getContext());

        int paddingSize = getContext().getResources().getDimensionPixelSize(R.dimen.fab_margin);
        scrollView.setPadding(paddingSize, paddingSize, paddingSize, paddingSize);

        LinearLayout filterLayout = new LinearLayout(getContext());
        filterLayout.setOrientation(LinearLayout.VERTICAL);

        TextView titleTv = new TextView(getContext());
        titleTv.setTextSize(18);
        titleTv.setText(R.string.dialog_title_choose_filters);

        filterLayout.addView(titleTv);

        if(mGame.shouldShowPlatformFilter()) {
            TextView filterTv = makeFilterLabel();
            filterTv.setText(getContext().getString(R.string.label_filter_platform));
            filterLayout.addView(filterTv);

            ChipGroup cgv = new ChipGroup(getContext());
            for(Platform p : mGame.platforms) {
                Chip cv = makeChip(Variable.VariableSelections.FILTER_KEY_PLATFORM, p.id);
                cv.setText(p.name);
                cgv.addView(cv);
            }

            filterLayout.addView(cgv);
        }
        if(mGame.shouldShowRegionFilter()) {
            TextView filterTv = makeFilterLabel();
            filterTv.setText(getContext().getString(R.string.label_filter_region));
            filterLayout.addView(filterTv);

            ChipGroup cgv = new ChipGroup(getContext());
            for(Region r : mGame.regions) {
                Chip cv = makeChip(Variable.VariableSelections.FILTER_KEY_REGION, r.id);
                cv.setText(r.name);
                cgv.addView(cv);
            }

            filterLayout.addView(cgv);
        }

        for(Variable v : mVariables) {

            if(v.isSubcategory)
                continue; // handled elsewhere

            TextView filterTv = makeFilterLabel();
            filterTv.setText(v.name);
            filterLayout.addView(filterTv);

            ChipGroup cgv = new ChipGroup(getContext());
            for(String vv : v.values.keySet()) {
                Chip cv = makeChip(v.id, vv);
                cv.setText(Objects.requireNonNull(v.values.get(vv)).label);
                cgv.addView(cv);
            }

            filterLayout.addView(cgv);
        }

        scrollView.addView(filterLayout);

        LinearLayout.LayoutParams scrollViewLayoutParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        scrollViewLayoutParams.weight = 1;

        scrollView.setLayoutParams(scrollViewLayoutParams);

        layout.addView(scrollView);

        Button okButton = new Button(getContext());
        okButton.setText(android.R.string.ok);
        LinearLayout.LayoutParams okButtonLayoutParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        okButtonLayoutParams.weight = 0;
        okButton.setLayoutParams(okButtonLayoutParams);

        okButton.setOnClickListener(this);

        layout.addView(okButton);

        setContentView(layout);
    }

    private TextView makeFilterLabel() {
        int paddingSize = getContext().getResources().getDimensionPixelSize(R.dimen.fab_margin);
        TextView filterTv = new TextView(getContext());
        filterTv.setPadding(paddingSize, paddingSize, paddingSize, paddingSize);
        return filterTv;
    }

    private Chip makeChip(String filterKey, String filterValue) {
        Chip cv = new Chip(getContext(), null, R.style.Widget_MaterialComponents_Chip_Filter);
        cv.setChipBackgroundColor(getContext().getResources().getColorStateList(R.color.filter));
        cv.setCheckedIconVisible(true);

        cv.setClickable(true);
        cv.setCheckable(true);

        cv.setOnCheckedChangeListener(this);

        cv.setTag(filterKey + "_" + filterValue);

        if(mVariableSelections.isSelected(filterKey, filterValue))
            cv.setChecked(true);

        return cv;
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        String[] spl = ((String)buttonView.getTag()).split("_");
        mVariableSelections.select(spl[0], spl[1], isChecked);
    }

    @Override
    public void onClick(View v) {
        dismiss();
    }
}
