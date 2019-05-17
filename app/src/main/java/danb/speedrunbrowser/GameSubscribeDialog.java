package danb.speedrunbrowser;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.ColorStateList;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.util.List;
import java.util.Objects;
import java.util.Set;

import androidx.annotation.NonNull;
import danb.speedrunbrowser.api.objects.Category;
import danb.speedrunbrowser.api.objects.Game;
import danb.speedrunbrowser.api.objects.Level;
import danb.speedrunbrowser.api.objects.Variable;
import danb.speedrunbrowser.utils.AppDatabase;

public class GameSubscribeDialog extends AlertDialog implements CompoundButton.OnCheckedChangeListener, View.OnClickListener {

    private Game mGame;
    private GameDetailFragment.GameSubscription mSubscribed;

    private Button mSelectAllButton;
    private Button mDeselectAllButton;
    private LinearLayout mCategoryCheckboxDisplay;
    private Button mCancelButton;
    private Button mApplyButton;

    public GameSubscribeDialog(Context ctx, @NonNull GameDetailFragment.GameSubscription sub) {
        super(ctx, AlertDialog.THEME_DEVICE_DEFAULT_DARK);

        mGame = sub.getGame();
        mSubscribed = sub;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        View rootView = ((LayoutInflater)getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE)).inflate(R.layout.dialog_subscribe_game, null);

        mSelectAllButton = rootView.findViewById(R.id.btnSelectAll);
        mDeselectAllButton = rootView.findViewById(R.id.btnSelectNone);
        mCategoryCheckboxDisplay = rootView.findViewById(R.id.layoutListCategory);
        mCancelButton = rootView.findViewById(R.id.btnCancel);
        mApplyButton = rootView.findViewById(R.id.btnSave);

        mSelectAllButton.setOnClickListener(this);
        mDeselectAllButton.setOnClickListener(this);
        mCancelButton.setOnClickListener(this);
        mApplyButton.setOnClickListener(this);

        setContentView(rootView);

        setViewData();
    }

    private void setViewData() {
        mCategoryCheckboxDisplay.removeAllViews();

        for(Category category : mGame.getCategories()) {
            if(category.getType().equals("per-level")) {
                for(Level level : mGame.getLevels()) {
                    String id = category.getId() + "_" + level.getId();
                    CheckBox cb = makeStyledCheckbox();
                    cb.setText(getContext().getResources().getString(R.string.render_level_category, category.getName(), level.getName()));
                    cb.setChecked(mSubscribed.contains(id));
                    cb.setTag(id);

                    mCategoryCheckboxDisplay.addView(cb);
                }
            }
            else {
                CheckBox cb = makeStyledCheckbox();
                cb.setText(category.getName());
                cb.setChecked(mSubscribed.contains(category.getId()));
                cb.setTag(category.getId());

                mCategoryCheckboxDisplay.addView(cb);
            }
        }
    }

    private CheckBox makeStyledCheckbox() {
        CheckBox checkbox = new CheckBox(getContext());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            checkbox.setButtonTintList(getContext().getResources().getColorStateList(R.color.all_white));
        }

        checkbox.setTextColor(getContext().getResources().getColor(android.R.color.white));
        checkbox.setOnCheckedChangeListener(this);

        return checkbox;
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        String id = ((String)buttonView.getTag());

        if(isChecked)
            mSubscribed.add(id);
        else
            mSubscribed.remove(id);
    }

    @Override
    public void onClick(View v) {

        if(v == mSelectAllButton) {
            for(Category category : mGame.getCategories()) {
                if(category.getType().equals("per-level")) {
                    for(Level level : mGame.getLevels()) {
                        mSubscribed.add(category.getId() + "_" + level.getId());
                    }
                }
                else {
                    mSubscribed.add(category.getId());
                }
            }

            setViewData();
        }
        else if(v == mDeselectAllButton) {
            mSubscribed.clear();

            setViewData();
        }
        else if(v == mApplyButton) {
            dismiss();
        }
        else if(v == mCancelButton) {
            cancel();
        }
    }

    public GameDetailFragment.GameSubscription getSubscriptions() {
        return mSubscribed;
    }
}
