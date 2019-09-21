package danb.speedrunbrowser

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import danb.speedrunbrowser.utils.Constants

class RunItemListFragment : ItemListFragment() {

    private var mBleedingEdgeSwitch: CheckBox? = null

    val shouldShowBleedingEdgeRun: Boolean
    get() = mBleedingEdgeSwitch?.isChecked ?: false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val v = super.onCreateView(inflater, container, savedInstanceState)
        mBleedingEdgeSwitch = v!!.findViewById(R.id.switchBleedingEdge)
        mBleedingEdgeSwitch!!.visibility = View.VISIBLE

        val prefs = context!!.getSharedPreferences(Constants.SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE)

        mBleedingEdgeSwitch!!.isChecked = prefs.getBoolean(PREF_BLEEDING_EDGE, false)

        mBleedingEdgeSwitch!!.setOnCheckedChangeListener { _, _ ->
            val pe = context!!.getSharedPreferences(Constants.SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE).edit()
            pe.putBoolean(PREF_BLEEDING_EDGE, mBleedingEdgeSwitch!!.isChecked)
            pe.apply()

            reload()
        }
        return v
    }

    companion object {
        const val PREF_BLEEDING_EDGE = "showBleedingEdge"
    }
}
