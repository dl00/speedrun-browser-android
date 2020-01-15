package danb.speedrunbrowser.stats

import android.content.Context
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import danb.speedrunbrowser.R
import danb.speedrunbrowser.api.objects.Metric
import danb.speedrunbrowser.utils.Util

class MetricView(ctx: Context, val options: ChartOptions) : LinearLayout(ctx) {

    var metricData: Metric? = null
    set(value) {
        field = value
        applyData()
    }

    init {
        orientation = HORIZONTAL


        removeAllViews()

        View.inflate(context, R.layout.content_metric_view, this)

        findViewById<TextView>(R.id.txtLabel).text = options.name
    }

    private val valueView = findViewById<TextView>(R.id.txtValue)

    init {
        applyData()
    }

    private fun applyData() {
        if(metricData != null) {

            valueView.text = options.xValueFormat?.invoke(metricData!!.value.toFloat()) ?: metricData!!.value.toString()
        }
    }
}