package danb.speedrunbrowser.stats

import android.content.Context
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
import com.github.mikephil.charting.data.*
import danb.speedrunbrowser.R
import danb.speedrunbrowser.api.objects.BarChartData
import danb.speedrunbrowser.api.objects.Chart
import danb.speedrunbrowser.api.objects.ChartData
import danb.speedrunbrowser.api.objects.LineChartData
import io.reactivex.disposables.CompositeDisposable

fun Chart.generateMpLineSetData(labels: Map<String, String>): LineData {
    val mpdata = LineData()

    val datasets = this.data.mapValues {
        it.value.map { vv ->
            val v = vv as LineChartData
            Entry(v.x, v.y, v.obj)
        }
    }

    datasets.forEach {
        mpdata.addDataSet(LineDataSet(it.value, labels[it.key]))
    }

    return mpdata
}

fun Chart.generateMpBarSetData(labels: Map<String, String>): BarData {
    val mpdata = BarData()

    val datasets = this.data.mapValues {
        it.value.map { vv ->
            val v = vv as BarChartData
            BarEntry(v.x, v.y)
        }
    }

    datasets.forEach {
        mpdata.addDataSet(BarDataSet(it.value, labels[it.key]))
    }

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
        chart.setBackgroundColor(resources.getColor(R.color.colorPrimaryDark))
        chart.setDrawGridBackground(chartType == "line")
        chart.setDrawBarShadow(false)
        chart.isHighlightFullBarEnabled = true

        // TODO: legend?

        chart.axisLeft.axisMinimum = 0f

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

                    val cd = CombinedData()

                    if(data.chart_type == "line")
                        cd.setData(data.generateMpLineSetData(options.setLabels))
                    if(data.chart_type == "bar")
                        cd.setData(data.generateMpBarSetData(options.setLabels))

                    println("GRAPH DATA TO BE INVALID" + cd)

                    graph!!.data = cd
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