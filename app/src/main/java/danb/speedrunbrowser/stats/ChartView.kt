package danb.speedrunbrowser.stats

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import androidx.viewpager.widget.ViewPager
import com.github.mikephil.charting.charts.CombinedChart
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.ValueFormatter
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.listener.OnChartValueSelectedListener
import com.github.mikephil.charting.utils.ColorTemplate
import danb.speedrunbrowser.R
import danb.speedrunbrowser.api.objects.Chart
import danb.speedrunbrowser.api.objects.ChartData
import danb.speedrunbrowser.utils.ViewPagerAdapter
import danb.speedrunbrowser.utils.Util
import danb.speedrunbrowser.views.SimpleTabStrip
import io.reactivex.disposables.CompositeDisposable
import kotlinx.android.synthetic.main.content_chart_view.view.*

fun Chart.generateMpLineSetData(context: Context, labels: (v: String) -> String): LineData {
    val mpData = LineData()

    val ds = datasets.map {
        it to data.getValue(it).map { vv ->
            Entry(vv.x, vv.y, vv.obj)
        }
    }

    var curColor = 0
    ds.forEach {

        val dataSet = LineDataSet(it.second, labels(it.first))

        val color = ColorTemplate.VORDIPLOM_COLORS[curColor++ % ColorTemplate.VORDIPLOM_COLORS.size]

        dataSet.color = color
        dataSet.setCircleColor(color)
        dataSet.circleHoleColor = context.resources.getColor(R.color.colorPrimary)
        dataSet.circleRadius = 5f
        dataSet.circleHoleRadius = 3f

        mpData.addDataSet(dataSet)
    }

    mpData.setDrawValues(false)

    return mpData
}

fun Chart.generateMpBarSetData(context: Context, labels: (v: String) -> String): BarData {
    val mpData = BarData()

    val dataSets = datasets.map {
        var i = 0
        it to data.getValue(it).map { vv ->
            BarEntry(i++.toFloat(), vv.y, vv.x)
        }
    }


    dataSets.forEach {
        val dataSet = BarDataSet(it.second, labels(it.first))

        dataSet.color = ContextCompat.getColor(context, R.color.colorSelected)

        mpData.addDataSet(dataSet)
    }

    mpData.setDrawValues(false)

    return mpData
}

fun Chart.generateMpPieSetData(context: Context, labels: ((v: Any) -> String)?): PieData {
    val mpData = PieDataSet(data.getValue("main").map {
        if(labels != null)
            PieEntry(
                    it.y,
                    labels(it.obj!!),
                    it.obj
            )
        else
            PieEntry(
                    it.y,
                    it.obj
            )
    }, "")

    mpData.colors = ColorTemplate.MATERIAL_COLORS.asList()
    mpData.setDrawValues(false)
    mpData.setDrawIcons(false)

    val d = PieData()
    d.addDataSet(mpData)

    return d
}

class ChartView(ctx: Context, val options: ChartOptions) : FrameLayout(ctx), OnChartValueSelectedListener {

    private var graph: CombinedChart? = null
    private var pie: PieChart? = null

    private var disposables: CompositeDisposable = CompositeDisposable()

    var chartData: Chart? = null
    set(value) {
        field = value
        applyData()
    }

    private val dataFilter = mutableSetOf<String>()

    private val listAdapters: MutableList<ChartDataAdapter> = mutableListOf()

    init {
        onConfigurationChanged(null)
    }

    private lateinit var listContainer: LinearLayout
    private lateinit var listTabs: SimpleTabStrip
    private lateinit var listPager: ViewPager

    override fun onConfigurationChanged(newConfig: Configuration?) {

        removeAllViews()

        View.inflate(context, R.layout.content_chart_view, this)

        findViewById<TextView>(R.id.textChartTitle).text = options.name
        findViewById<ImageView>(R.id.buttonShowChartInfo).setOnClickListener {
            Util.showInfoDialog(context, options.description)
        }

        graph = null
        pie = null
        listContainer = findViewById(R.id.layoutChartListContainer)
        listTabs = findViewById(R.id.tabsChartList)
        listPager = findViewById(R.id.pagerChartList)
        listAdapters.clear()
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

        chart.isScaleXEnabled = true
        chart.isScaleYEnabled = true

        chart.setPinchZoom(true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            chart.isNestedScrollingEnabled = true
        }

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

        chart.setOnChartValueSelectedListener(this)

        return chart
    }

    private fun initializePie() {
        val chart = PieChart(context)
        chart.description.isEnabled = false
        chart.setHoleColor(resources.getColor(R.color.colorPrimary))
        chart.legend.isEnabled = false
        chart.legend.setDrawInside(false)
        chart.setEntryLabelTextSize(8f)
        //chart.setDrawEntryLabels(false)
        chart.isRotationEnabled = false

        pie = chart
    }

    private fun buildChartList() {
        val adapter = ViewPagerAdapter()

        chartData!!.datasets.forEachIndexed { index, s ->
            val lv = RecyclerView(context)
            lv.layoutManager = LinearLayoutManager(context)
            // disable animations which do not blend well with the rest of the app
            (lv.itemAnimator as SimpleItemAnimator).supportsChangeAnimations = false

            val listAdapter = ChartDataAdapter(chartData!!.data.getValue(s), lv)

            // setup events for highlight
            listAdapter.onClickListener = {
                val hl = Highlight(chartData!!.data.getValue(s)[it].x, chartData!!.data.getValue(s)[it].y, index)
                hl.dataIndex = 0

                graph?.highlightValue(hl, false)
            }

            lv.adapter = listAdapter
            listAdapters.add(listAdapter)

            lv.minimumHeight = 300

            val cb = CheckBox(context!!)

            cb.setText(R.string.label_filter_this)
            cb.setTextColor(Color.WHITE)

            if (Build.VERSION.SDK_INT >= 21){
                cb.buttonTintList = ContextCompat.getColorStateList(context, R.color.all_white)
            }


            cb.setOnCheckedChangeListener { _, checked ->

                if (checked) {
                    dataFilter.add(s)
                }
                else {
                    dataFilter.remove(s)
                }

                applyData()
            }

            val ll = LinearLayout(context!!)

            ll.orientation = LinearLayout.VERTICAL

            if(chartData!!.datasets.size > 1) {
                ll.addView(cb)
            }

            ll.addView(lv)

            adapter.views.add(options.setLabels(s) to ll)
        }

        listPager.adapter = adapter

        if(chartData!!.datasets.size <= 1) {
            listTabs.visibility = View.GONE
        }
        else {
            listTabs.visibility = View.VISIBLE
            listTabs.setup(listPager)
        }
    }

    private fun applyData() {
        val data = chartData?.copy(
                data = chartData!!.data.filterKeys { dataFilter.isEmpty() || dataFilter.contains(it) }
        ) ?: return

        // first init
        when(data.chart_type) {
            "line", "bar" -> {
                if(graph == null) {
                    graph = initializeChart(data.chart_type)
                    findViewById<FrameLayout>(R.id.frameChart).addView(graph)
                }

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

                graph!!.xAxis.labelRotationAngle = -30.0f

                graph!!.invalidate()

                if (listPager.adapter == null) {
                    if(options.chartListViewHolderSource != null) {
                        buildChartList()
                    }
                    else
                        listContainer.visibility = View.GONE
                }
            }
            "pie" -> {
                if(pie == null) {
                    initializePie()
                    findViewById<FrameLayout>(R.id.frameChart).addView(pie)
                }

                pie!!.data = data.generateMpPieSetData(context, options.pieLabels)

                pie!!.invalidate()

                listContainer.visibility = View.GONE
            }
            "list" -> {
                // load the list with data
            }
        }
    }


    // chart call functions when things are selected
    override fun onNothingSelected() {
        listAdapters.forEach {
            it.selectedIndex = null
        }
    }

    override fun onValueSelected(e: Entry?, h: Highlight?) {

        onNothingSelected()

        if(h != null && listAdapters.size != 0) {

            pagerChartList.currentItem = h.dataSetIndex

            val dataIndex = chartData!!.data
                .getValue(chartData!!.datasets[h.dataSetIndex]).indexOfFirst {
                    it.obj == e!!.data
                }

            // this so complicated because h.dataIndex does not include useful data for some reason
            listAdapters[h.dataSetIndex].selectedIndex = dataIndex
        }
    }

    fun cleanup() {
        disposables.dispose()
    }

    inner class ChartDataAdapter(private val chartData: List<ChartData>, private val recyclerView: RecyclerView) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        var selectedIndex: Int? = null
        set(value) {
            if(field != null)
                notifyItemChanged(reversePosition(field!!))

            field = value

            if(value != null) {
                notifyItemChanged(reversePosition(value))

                recyclerView.scrollToPosition(reversePosition(value))
            }
        }

        private fun reversePosition(position: Int): Int {
            return if(options.chartListReverse)
                itemCount - 1 - position
            else
                position
        }

        var onClickListener: ((position: Int) -> Unit)? = null

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder =
            options.chartListViewHolderSource!!.newViewHolder(context, parent)

        override fun getItemCount(): Int = chartData.size

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {

            val d = chartData[reversePosition(position)]

            if(d.obj != null)
                options.chartListViewHolderSource!!.applyToViewHolder(context, disposables, holder, d.obj)

            if(reversePosition(position) == selectedIndex)
                holder.itemView.background = ColorDrawable(resources.getColor(R.color.colorAccent))
            else
                holder.itemView.background = ColorDrawable(Color.TRANSPARENT)

            holder.itemView.setOnClickListener {

                if(selectedIndex == reversePosition(position) &&
                        options.chartListOnSelected != null && chartData[position].obj != null)
                    options.chartListOnSelected!!(chartData[selectedIndex!!].obj!!)

                selectedIndex = reversePosition(position)

                if(onClickListener != null)
                    onClickListener!!(selectedIndex!!)
            }
        }
    }
}