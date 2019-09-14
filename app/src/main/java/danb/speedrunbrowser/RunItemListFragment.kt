package danb.speedrunbrowser

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Switch

class RunItemListFragment : ItemListFragment() {

    private lateinit var mBleedingEdgeSwitch: Switch

    val shouldShowBleedingEdgeRun: Boolean
    get() = mBleedingEdgeSwitch.isEnabled

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val v = super.onCreateView(inflater, container, savedInstanceState)
        mBleedingEdgeSwitch = v!!.findViewById(R.id.switchBleedingEdge)
        mBleedingEdgeSwitch.setOnCheckedChangeListener { _, _ -> reload() }
        return v
    }
}
