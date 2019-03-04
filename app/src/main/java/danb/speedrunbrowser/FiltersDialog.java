package danb.speedrunbrowser;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.util.List;
import java.util.Objects;

import danb.speedrunbrowser.api.objects.Variable;

public class FiltersDialog extends AlertDialog implements CompoundButton.OnCheckedChangeListener {

    public List<Variable> mVariables;
    public Variable.VariableSelections mVariableSelections;

    public FiltersDialog(Context ctx, List<Variable> variables, Variable.VariableSelections variableSelections) {
        super(ctx, AlertDialog.THEME_DEVICE_DEFAULT_DARK);
        mVariables = variables;
        mVariableSelections = variableSelections;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        int paddingSize = getContext().getResources().getDimensionPixelSize(R.dimen.fab_margin);

        ScrollView scrollView = new ScrollView(getContext());

        scrollView.setPadding(paddingSize, paddingSize, paddingSize, paddingSize);

        LinearLayout filterLayout = new LinearLayout(getContext());
        filterLayout.setOrientation(LinearLayout.VERTICAL);

        TextView titleTv = new TextView(getContext());
        titleTv.setTextSize(18);
        titleTv.setText(R.string.dialog_title_choose_filters);

        filterLayout.addView(titleTv);

        for(Variable v : mVariables) {
            TextView filterTv = new TextView(getContext());
            filterTv.setText(v.name);
            filterTv.setPadding(paddingSize, paddingSize, paddingSize, paddingSize);

            filterLayout.addView(filterTv);

            ChipGroup cgv = new ChipGroup(getContext());

            for(String vv : v.values.keySet()) {
                Chip cv = new Chip(getContext(), null, R.style.Widget_MaterialComponents_Chip_Filter);
                cv.setText(Objects.requireNonNull(v.values.get(vv)).label);
                cv.setChipBackgroundColor(getContext().getResources().getColorStateList(R.color.filter));
                cv.setCheckedIconVisible(true);

                cv.setClickable(true);
                cv.setCheckable(true);

                if(mVariableSelections.isSelected(v.id, vv))
                    cv.setChecked(true);

                cv.setOnCheckedChangeListener(this);

                cv.setTag(v.id + "_" + vv);

                cgv.addView(cv);
            }

            filterLayout.addView(cgv);
        }

        scrollView.addView(filterLayout);

        setContentView(scrollView);
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        String[] spl = ((String)buttonView.getTag()).split("_");
        mVariableSelections.select(spl[0], spl[1], isChecked);
    }
}
