package danb.speedrunbrowser

import android.app.AlertDialog
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.CompoundButton
import android.widget.LinearLayout

import danb.speedrunbrowser.api.objects.Game

class GameSubscribeDialog(ctx: Context, val subscriptions: GameDetailFragment.GameSubscription) : AlertDialog(ctx, THEME_DEVICE_DEFAULT_DARK), CompoundButton.OnCheckedChangeListener, View.OnClickListener {

    private val mGame: Game?

    private var mSelectAllButton: Button? = null
    private var mDeselectAllButton: Button? = null
    private var mCategoryCheckboxDisplay: LinearLayout? = null
    private var mCancelButton: Button? = null
    private var mApplyButton: Button? = null

    init {

        mGame = subscriptions.game
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val rootView = (context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater).inflate(R.layout.dialog_subscribe_game, null)

        mSelectAllButton = rootView.findViewById(R.id.btnSelectAll)
        mDeselectAllButton = rootView.findViewById(R.id.btnSelectNone)
        mCategoryCheckboxDisplay = rootView.findViewById(R.id.layoutListCategory)
        mCancelButton = rootView.findViewById(R.id.btnCancel)
        mApplyButton = rootView.findViewById(R.id.btnSave)

        mSelectAllButton!!.setOnClickListener(this)
        mDeselectAllButton!!.setOnClickListener(this)
        mCancelButton!!.setOnClickListener(this)
        mApplyButton!!.setOnClickListener(this)

        setContentView(rootView)

        setViewData()
    }

    private fun setViewData() {
        mCategoryCheckboxDisplay!!.removeAllViews()

        for ((id1, name, _, type) in mGame!!.categories!!) {
            if (type == "per-level") {
                for ((id2, name1) in mGame.levels!!) {
                    val id = id1 + "_" + id2
                    val cb = makeStyledCheckbox()
                    cb.text = context.resources.getString(R.string.render_level_category, name, name1)
                    cb.isChecked = subscriptions.contains(id)
                    cb.tag = id

                    mCategoryCheckboxDisplay!!.addView(cb)
                }
            } else {
                val cb = makeStyledCheckbox()
                cb.text = name
                cb.isChecked = subscriptions.contains(id1)
                cb.tag = id1

                mCategoryCheckboxDisplay!!.addView(cb)
            }
        }
    }

    private fun makeStyledCheckbox(): CheckBox {
        val checkbox = CheckBox(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            checkbox.buttonTintList = context.resources.getColorStateList(R.color.all_white)
        }

        checkbox.setTextColor(context.resources.getColor(android.R.color.white))
        checkbox.setOnCheckedChangeListener(this)

        return checkbox
    }

    override fun onCheckedChanged(buttonView: CompoundButton, isChecked: Boolean) {
        val id = buttonView.tag as String

        if (isChecked)
            subscriptions.add(id)
        else
            subscriptions.remove(id)
    }

    override fun onClick(v: View) {

        if (v === mSelectAllButton) {
            for ((id, _, _, type) in mGame!!.categories!!) {
                if (type == "per-level") {
                    for ((id1) in mGame.levels!!) {
                        subscriptions.add(id + "_" + id1)
                    }
                } else {
                    subscriptions.add(id)
                }
            }

            setViewData()
        } else if (v === mDeselectAllButton) {
            subscriptions.clear()

            setViewData()
        } else if (v === mApplyButton) {
            dismiss()
        } else if (v === mCancelButton) {
            cancel()
        }
    }
}
