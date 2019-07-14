package danb.speedrunbrowser.stats

import com.github.mikephil.charting.utils.MPPointF
import com.github.mikephil.charting.formatter.ValueFormatter
import android.widget.TextView
import com.github.mikephil.charting.components.MarkerView
import android.annotation.SuppressLint
import android.content.Context
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.highlight.Highlight
import danb.speedrunbrowser.R
import java.text.DecimalFormat


/**
 * Source: https://github.com/PhilJay/MPAndroidChart/blob/e5b66192e7b303d7d25fc172b1878c055b554047/MPChartExample/src/main/java/com/xxmassdeveloper/mpchartexample/custom/XYMarkerView.java
 */
@SuppressLint("ViewConstructor")
class XYMarkerView(context: Context, private val xAxisValueFormatter: ValueFormatter) : MarkerView(context, R.layout.content_chart_marker) {

    private val tvContent: TextView

    private val format: DecimalFormat

    init {
        tvContent = findViewById(R.id.txtMarkerContent)
        format = DecimalFormat("###.#")
    }

    // runs every time the MarkerView is redrawn, can be used to update the
    // content (user-interface)
    override fun refreshContent(e: Entry, highlight: Highlight) {

        tvContent.text = String.format("%s: %s", xAxisValueFormatter.getFormattedValue(e.x), format.format(e.y))

        super.refreshContent(e, highlight)
    }

    override fun getOffset(): MPPointF {
        return MPPointF((-(width / 2)).toFloat(), (-height).toFloat())
    }
}