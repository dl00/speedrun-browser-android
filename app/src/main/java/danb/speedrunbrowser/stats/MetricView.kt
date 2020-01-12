package danb.speedrunbrowser.stats

import android.content.Context
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import danb.speedrunbrowser.R
import danb.speedrunbrowser.utils.Util

class MetricView(ctx: Context, val options: ChartOptions) : LinearLayout(ctx) {

    init {
        orientation = HORIZONTAL


        removeAllViews()

        View.inflate(context, R.layout.content_metric_view, this)

        findViewById<TextView>(R.id.textChartTitle).text = options.name
        findViewById<ImageView>(R.id.buttonShowChartInfo).setOnClickListener {
            Util.showInfoDialog(context, options.description)
        }
        applyData()
    }

    fun applyData() {

    }
}