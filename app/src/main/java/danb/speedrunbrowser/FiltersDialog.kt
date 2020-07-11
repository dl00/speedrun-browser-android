@file:Suppress("DEPRECATION")

package danb.speedrunbrowser

import android.app.AlertDialog
import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CompoundButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat

import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup

import danb.speedrunbrowser.api.objects.Game
import danb.speedrunbrowser.api.objects.Variable

class FiltersDialog(
        ctx: Context,
        private val mGame: Game,
        private val mVariables: List<Variable>,
        private val mVariableSelections: Variable.VariableSelections
) : AlertDialog(ctx, if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) android.R.style.Theme_DeviceDefault_Dialog_Alert
    else THEME_DEVICE_DEFAULT_DARK), CompoundButton.OnCheckedChangeListener {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(context)
        layout.orientation = LinearLayout.VERTICAL
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            layout.background = ColorDrawable(context.getColor(R.color.colorPrimary))
        }

        val scrollView = ScrollView(context)

        val paddingSize = context.resources.getDimensionPixelSize(R.dimen.fab_margin)
        scrollView.setPadding(paddingSize, paddingSize, paddingSize, paddingSize)

        val filterLayout = LinearLayout(context)
        filterLayout.orientation = LinearLayout.VERTICAL

        val titleTv = TextView(context)
        titleTv.textSize = 18f
        titleTv.text = context.getString(R.string.dialog_title_choose_filters)

        filterLayout.addView(titleTv)

        if (mGame.shouldShowPlatformFilter()) {
            val filterTv = makeFilterLabel()
            filterTv.text = context.getString(R.string.label_filter_platform)
            filterLayout.addView(filterTv)

            val cgv = ChipGroup(context)
            for ((id, name) in mGame.platforms!!) {
                val cv = makeChip(Variable.VariableSelections.FILTER_KEY_PLATFORM, id)
                cv.text = name
                cgv.addView(cv)
            }

            filterLayout.addView(cgv)
        }
        if (mGame.shouldShowRegionFilter()) {
            val filterTv = makeFilterLabel()
            filterTv.text = context.getString(R.string.label_filter_region)
            filterLayout.addView(filterTv)

            val cgv = ChipGroup(context)
            for ((id, name) in mGame.regions!!) {
                val cv = makeChip(Variable.VariableSelections.FILTER_KEY_REGION, id)
                cv.text = name
                cgv.addView(cv)
            }

            filterLayout.addView(cgv)
        }

        for ((id, name, _, _, _, _, isSubcategory, values) in mVariables) {

            if (isSubcategory)
                continue // handled elsewhere

            val filterTv = makeFilterLabel()
            filterTv.text = name
            filterLayout.addView(filterTv)

            val cgv = ChipGroup(context)
            for (vv in values.keys) {
                val cv = makeChip(id, vv)
                cv.text = values.getValue(vv).label
                cgv.addView(cv)
            }

            filterLayout.addView(cgv)
        }

        scrollView.addView(filterLayout)

        val scrollViewLayoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        scrollViewLayoutParams.weight = 1f

        scrollView.layoutParams = scrollViewLayoutParams

        layout.addView(scrollView)

        val okButton = Button(context)
        okButton.setText(android.R.string.ok)
        val okButtonLayoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        okButtonLayoutParams.weight = 0f
        okButton.layoutParams = okButtonLayoutParams

        okButton.setOnClickListener { dismiss() }

        layout.addView(okButton)

        val clearButton = Button(context)
        clearButton.setText(R.string.button_clear)
        val clearButtonLayoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        clearButtonLayoutParams.weight = 0f
        clearButton.layoutParams = clearButtonLayoutParams

        clearButton.setOnClickListener { clearSelections() }

        layout.addView(clearButton)

        setContentView(layout)
    }

    private fun makeFilterLabel(): TextView {
        val paddingSize = context.resources.getDimensionPixelSize(R.dimen.fab_margin)
        val filterTv = TextView(context)
        filterTv.setPadding(paddingSize, paddingSize, paddingSize, paddingSize)
        return filterTv
    }

    private fun makeChip(filterKey: String, filterValue: String): Chip {
        val cv = Chip(context, null, R.style.Widget_MaterialComponents_Chip_Filter)
        cv.chipBackgroundColor = ContextCompat.getColorStateList(context, R.color.filter)
        cv.isCheckedIconVisible = true

        cv.isClickable = true
        cv.isCheckable = true
        cv.isFocusable = true
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            cv.foreground = context.resources.getDrawable(R.drawable.clickable_item)
        }

        cv.setOnCheckedChangeListener(this)

        cv.tag = filterKey + "_" + filterValue

        if (mVariableSelections.isSelected(filterKey, filterValue))
            cv.isChecked = true

        return cv
    }

    override fun onCheckedChanged(buttonView: CompoundButton, isChecked: Boolean) {
        val spl = (buttonView.tag as String).split("_".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        mVariableSelections.select(spl[0], spl[1], isChecked)
    }

    private fun clearSelections() {
        mVariableSelections.clear()
        dismiss()
    }
}
