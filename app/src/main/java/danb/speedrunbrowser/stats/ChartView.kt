package danb.speedrunbrowser.stats

import android.content.Context
import android.graphics.Color
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager.widget.ViewPager
import com.github.mikephil.charting.charts.CombinedChart
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.ValueFormatter
import com.github.mikephil.charting.utils.ColorTemplate
import danb.speedrunbrowser.R
import danb.speedrunbrowser.api.objects.Chart
import danb.speedrunbrowser.api.objects.ChartData
import io.reactivex.disposables.CompositeDisposable

fun Chart.generateMpLineSetData(context: Context, labels: (v: String) -> String): LineData {
    val mpdata = LineData()

    val datasets = this.data.mapValues {
        it.value.map { vv ->
            Entry(vv.x, vv.y, vv.obj)
        }
    }

    var curColor = 0
    datasets.forEach {

        val dataset = LineDataSet(it.value, labels(it.key))

        val color = ColorTemplate.VORDIPLOM_COLORS[curColor++ % ColorTemplate.VORDIPLOM_COLORS.size]

        dataset.color = color
        dataset.setCircleColor(color)
        dataset.circleHoleColor = context.resources.getColor(R.color.colorPrimary)
        dataset.circleRadius = 5f
        dataset.circleHoleRadius = 3f

        mpdata.addDataSet(dataset)
    }

    mpdata.setDrawValues(false)

    return mpdata
}

fun Chart.generateMpBarSetData(context: Context, labels: (v: String) -> String): BarData {
    val mpdata = BarData()

    val datasets = this.data.mapValues {
        var i = 0
        it.value.map { vv ->
            BarEntry(i++.toFloat(), vv.y, vv.x)
        }
    }


    datasets.forEach {
        val dataSet = BarDataSet(it.value, labels(it.key))

        dataSet.color = context.resources.getColor(R.color.colorSelected)

        mpdata.addDataSet(dataSet)
    }

    mpdata.setDrawValues(false)

    return mpdata
}

class ChartView(ctx: Context, val options: ChartOptions) : LinearLayout(ctx) {

    private var graph: CombinedChart? = null
    private var list: ViewPager? = null

    private var disposables: CompositeDisposable = CompositeDisposable()

    var chartData: Chart? = null
    set(value) {
        field = value
        applyData()
    }

    private fun initializeChart(chartType: String): CombinedChart {
        val chart = CombinedChart(context)
        chart.setDrawGridBackground(chartType == "line")
        chart.setDrawBarShadow(false)
        //chart.isHighlightFullBarEnabled = true

        chart.axisLeft.setDrawAxisLine(false)
        chart.axisLeft.textColor = Color.WHITE
        chart.xAxis.textColor = Color.WHITE
        chart.xAxis.position = XAxis.XAxisPosition.BOTTOM
        chart.xAxis.setAvoidFirstLastClipping(true)
        chart.xAxis.setDrawGridLines(false)
        chart.axisRight.isEnabled = false
        chart.legend.textColor = Color.WHITE
        chart.legend.orientation = Legend.LegendOrientation.VERTICAL
        chart.legend.horizontalAlignment = Legend.LegendHorizontalAlignment.RIGHT
        chart.legend.verticalAlignment = Legend.LegendVerticalAlignment.TOP
        chart.description.isEnabled = false

        chart.setDrawGridBackground(false)

        chart.isScaleXEnabled = false
        chart.isScaleYEnabled = false



        if(options.xValueFormat != null) {
            chart.xAxis.valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    // bar charts require a hack since they are designed contiguous
                    return if(chartType == "bar")
                        options.xValueFormat!!(graph!!.barData.getDataSetByIndex(0)
                                .getEntryForIndex(value.toInt()).data as Float)
                    else
                        options.xValueFormat!!(value)
                }
            }
        }

        if(options.yValueFormat != null) {
            chart.axisLeft.valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    return options.yValueFormat!!(value)
                }
            }
        }

        val markerView = XYMarkerView(context, chart.xAxis.valueFormatter, chart.axisLeft.valueFormatter)
        markerView.chartView = chart
        chart.marker = markerView

        val lp = LayoutParams(LayoutParams.MATCH_PARENT, resources.getDimensionPixelSize(R.dimen.chart_height))

        chart.layoutParams = lp

        return chart
    }

    private fun initializeList(d: Map<String, List<ChartData>>): ViewPager {
        return ViewPager(context)
    }

    private fun applyData() {
        val data = chartData ?: return

        if(graph == null && list == null) {
            // first init
            when(data.chart_type) {
                "line", "bar" -> {
                    graph = initializeChart(data.chart_type)
                    addView(graph)

                    val cd = CombinedData()

                    if(data.chart_type == "line")
                        cd.setData(data.generateMpLineSetData(context, options.setLabels))
                    else if(data.chart_type == "bar")
                        cd.setData(data.generateMpBarSetData(context, options.setLabels))

                    graph!!.data = cd

                    // hack to prevent bar clipping
                    if(data.chart_type == "bar") {
                        graph!!.xAxis.axisMinimum = -0.5f
                        graph!!.xAxis.axisMaximum = graph!!.barData.xMax + 0.5f
                    }

                    graph!!.xAxis.labelRotationAngle = -45.0f

                    graph!!.invalidate()

                    if(options.chartListViewHolderSource != null) {
                        list = initializeList(data.data)
                    }
                }
                "list" -> {
                    list = initializeList(data.data)

                    // load the list with data
                }
            }
        }
        else {
            // in-place update...
        }
    }

    fun cleanup() {
        disposables.dispose()
    }

    inner class ChartDataAdapter(val chartData: List<ChartData>) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder =
            options.chartListViewHolderSource!!.newViewHolder(context, parent)

        override fun getItemCount(): Int = chartData.size

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val d = chartData[position]
            if(d.obj != null)
                options.chartListViewHolderSource!!.applyToViewHolder(context, disposables, holder, d.obj)
        }
    }
}