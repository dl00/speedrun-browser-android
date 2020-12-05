package danb.speedrunbrowser.stats

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.core.view.children
import androidx.fragment.app.Fragment
import danb.speedrunbrowser.R
import danb.speedrunbrowser.api.SpeedrunMiddlewareAPI
import danb.speedrunbrowser.views.ProgressSpinnerView
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import java.lang.Exception

class StatisticsView(ctx: Context) : LinearLayout(ctx) {


    fun addMetrics(options: Iterable<ChartOptions>) {
        val ll = LinearLayout(context!!)
        ll.orientation = HORIZONTAL
        ll.gravity = Gravity.CENTER

        for(opts in options) {

            val mv = MetricView(context!!, opts)
            mv.layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1.0f)

            ll.addView(mv)
        }

        addView(ll)
    }

    fun addMetric(options: ChartOptions): Unit = addMetrics(listOf(options))

    fun addChart(options: ChartOptions) {
        val lp = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)

        val v = ChartView(context!!, options)
        v.layoutParams = lp

        addView(v)
    }

    fun addRanking(options: RankOptions) {
        val lp = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)

        val v = RankView(context!!, options)
        v.layoutParams = lp

        addView(v)
    }

    fun addTabbedSwitcher(options: TabbedSwitcherOptions) {
        val lp = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)

        val v = TabbedSwitcherView(context!!, options)
        v.layoutParams = lp

        addView(v)
    }

    fun applyData(chartData: SpeedrunMiddlewareAPI.APIChartData) {
        children.forEach { view ->
            try {
                when(view) {
                    is ChartView -> {
                        val d = chartData.charts[view.options.identifier]
                        if (d != null)
                            view.setData(chartData, d)
                    }
                    is RankView -> {
                        val d = chartData.rankings[view.options.identifier]
                        if (d != null)
                            view.setData(chartData, d)
                    }
                    is LinearLayout -> {
                        view.children.forEach {innerView: View ->
                            when(innerView) {
                                is MetricView -> innerView.metricData = chartData.metrics[innerView.options.identifier]
                            }
                        }
                    }
                    else -> {}
                }
            } catch (e: Exception) {
                Log.e(StatisticsFragment.TAG, "Failed rendering single stat:", e)
            }
        }
    }

    fun clearCharts() = removeAllViews()
}

abstract class StatisticsFragment : Fragment() {

    protected lateinit var contentView: LinearLayout

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        contentView = LinearLayout(context!!)
        contentView.orientation = LinearLayout.VERTICAL

        val v = inflater.inflate(R.layout.fragment_statistics, container, false)

        contentView = v.findViewById(R.id.contentView)

        val statsView = v.findViewById<FrameLayout>(R.id.statsView)

        if (rootLayout.parent != null)
            (rootLayout.parent as ViewGroup).removeView(rootLayout)

        statsView.addView(rootLayout)

        return v
    }

    override fun onStop() {
        super.onStop()
        statsView.clearCharts()
    }

    fun setDataSourceAPIResponse(d: Observable<SpeedrunMiddlewareAPI.APIChartResponse>) {
        setDataSource(d.map {
            // TODO: Check for error
            it.data
        })
    }

    private lateinit var rootLayout: FrameLayout

    private lateinit var spinner: ProgressSpinnerView

    protected lateinit var statsView: StatisticsView

    var dispose: Disposable? = null

    var chartData: SpeedrunMiddlewareAPI.APIChartData? = null

    var onDataReadyListener: ((data: SpeedrunMiddlewareAPI.APIChartData) -> Unit)? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)

        rootLayout = FrameLayout(context)

        spinner  = ProgressSpinnerView(context, null)
        spinner.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT)
        rootLayout.addView(spinner, 0)

        statsView = StatisticsView(context)
        statsView.orientation = LinearLayout.VERTICAL
        statsView.visibility = View.GONE

        statsView.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        )

        rootLayout.addView(statsView)
    }

    private fun setDataSource(d: Observable<SpeedrunMiddlewareAPI.APIChartData>) {
        spinner.visibility = View.VISIBLE
        statsView.visibility = View.GONE

        dispose = d
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeOn(Schedulers.io())
                .subscribe {
                    animateChartsIn()
                    statsView.applyData(it)

                    if(onDataReadyListener != null)
                        onDataReadyListener!!(it)
                }
    }

    private fun animateChartsIn() {
        val animTime = resources.getInteger(
                android.R.integer.config_longAnimTime)

        val translationDistance = resources.getDimensionPixelSize(R.dimen.anim_slide_transition_distance)

        spinner.visibility = View.GONE

        rootLayout.alpha = 0.0f
        statsView.visibility = View.VISIBLE

        rootLayout.translationY = translationDistance.toFloat()
        //rootLayout.scaleY = 0.975f
        //rootLayout.scaleX = 0.975f

        rootLayout.animate()
                .alpha(1.0f)
                .setDuration(animTime.toLong())
                .translationY(0f)
                .scaleX(1.0f)
                .scaleY(1.0f)
                .setListener(null)
    }

    override fun onDestroy() {
        super.onDestroy()

        dispose?.dispose()
    }

    companion object {
        val TAG: String = StatisticsFragment::class.java.simpleName
    }
}
