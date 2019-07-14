package danb.speedrunbrowser.stats

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.BaseAdapter
import android.widget.LinearLayout
import android.widget.ListView
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager.widget.ViewPager
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.CombinedChart
import com.github.mikephil.charting.components.Description
import com.github.mikephil.charting.components.MarkerView
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.ValueFormatter
import danb.speedrunbrowser.R
import danb.speedrunbrowser.api.objects.BarChartData
import danb.speedrunbrowser.api.objects.Chart
import danb.speedrunbrowser.api.objects.ChartData
import danb.speedrunbrowser.api.objects.LineChartData
import io.reactivex.disposables.CompositeDisposable

fun Chart.generateMpLineSetData(context: Context, labels: (v: String) -> String): LineData {
    val mpdata = LineData()

    val datasets = this.data.mapValues {
        it.value.map { vv ->
            val v = vv as LineChartData
            Entry(v.x, v.y, v.obj)
        }
    }

    datasets.forEach {
        mpdata.addDataSet(LineDataSet(it.value, labels(it.key)))
    }

    return mpdata
}

fun Chart.generateMpBarSetData(context: Context, labels: (v: String) -> String): BarData {
    val mpdata = BarData()

    val datasets = this.data.mapValues {
        var i = 0
        it.value.map { vv ->
            val v = vv as BarChartData
            BarEntry(i++.toFloat(), v.y, v.x)
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
        chart.axisRight.textColor = Color.WHITE
        chart.legend.textColor = Color.WHITE
        chart.description.isEnabled = false
        chart.background = ColorDrawable(Color.TRANSPARENT)

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

        val markerView = XYMarkerView(context, chart.xAxis.valueFormatter)
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
                    if(data.chart_type == "bar")
                        cd.setData(data.generateMpBarSetData(context, options.setLabels))

                    graph!!.data = cd

                    // hack to prevent bar clipping
                    if(data.chart_type == "bar") {
                        graph!!.xAxis.axisMinimum = -0.5f
                        graph!!.xAxis.axisMaximum = graph!!.barData.xMax + 0.5f
                        graph!!.xAxis.labelRotationAngle = -45.0f
                    }

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
            val obj = when(d) {
                is LineChartData -> d.obj
                is BarChartData -> null
            }

            //options.chartListViewHolderSource!!.applyToViewHolder(context, disposables, holder, )
        }
    }
}