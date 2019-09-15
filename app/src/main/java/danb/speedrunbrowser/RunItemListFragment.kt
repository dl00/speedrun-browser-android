package danb.speedrunbrowser

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox

class RunItemListFragment : ItemListFragment() {

    private var mBleedingEdgeSwitch: CheckBox? = null

    val shouldShowBleedingEdgeRun: Boolean
    get() = mBleedingEdgeSwitch?.isChecked ?: false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val v = super.onCreateView(inflater, container, savedInstanceState)
        mBleedingEdgeSwitch = v!!.findViewById(R.id.switchBleedingEdge)
        mBleedingEdgeSwitch!!.visibility = View.VISIBLE
        mBleedingEdgeSwitch!!.setOnCheckedChangeListener { _, _ -> reload() }
        return v
    }
}
