package danb.speedrunbrowser.stats

import android.app.ActionBar
import android.content.Context
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.children
import androidx.fragment.app.Fragment
import danb.speedrunbrowser.R
import danb.speedrunbrowser.api.SpeedrunMiddlewareAPI
import danb.speedrunbrowser.api.objects.Metric
import danb.speedrunbrowser.views.ProgressSpinnerView
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers

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
        clearCharts()
    }

    fun setDataSourceAPIResponse(d: Observable<SpeedrunMiddlewareAPI.APIChartResponse>) {
        setDataSource(d.map {
            // TODO: Check for error
            it.data
        })
    }

    private lateinit var rootLayout: FrameLayout
    private lateinit var layout: LinearLayout

    private lateinit var spinner: ProgressSpinnerView

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

        layout = LinearLayout(context)
        layout.orientation = LinearLayout.VERTICAL
        layout.visibility = View.GONE

        layout.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        )

        rootLayout.addView(layout)
    }

    fun setDataSource(d: Observable<SpeedrunMiddlewareAPI.APIChartData>) {
        spinner.visibility = View.VISIBLE
        layout.visibility = View.GONE

        dispose = d
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeOn(Schedulers.io())
                .subscribe {

                    animateChartsIn()

                    chartData = it

                    layout.children.forEach { view ->
                        when(view) {
                            is ChartView -> view.chartData = it.charts[view.options.identifier]
                            is LinearLayout -> {
                                view.children.forEach {innerView: View ->
                                    when(innerView) {
                                        is MetricView -> innerView.metricData = it.metrics[innerView.options.identifier]
                                    }
                                }
                            }
                            else -> {}
                        }
                    }

                    if(onDataReadyListener != null)
                        onDataReadyListener!!(it)
                }
    }

    fun addMetrics(options: Iterable<ChartOptions>) {
        val ll = LinearLayout(context!!)
        ll.orientation = LinearLayout.HORIZONTAL
        ll.gravity = Gravity.CENTER

        for(opts in options) {

            val mv = MetricView(context!!, opts)
            mv.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f)

            ll.addView(mv)
        }

        layout.addView(ll)
    }

    fun addMetric(options: ChartOptions): Unit = addMetrics(listOf(options))

    fun addChart(options: ChartOptions) {
        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)

        val v = ChartView(context!!, options)
        v.layoutParams = lp

        layout.addView(v)
    }

    fun addTabbedSwitcher(options: TabbedSwitcherOptions) {

    }

    fun clearCharts() {
        layout.removeAllViews()
    }

    fun animateChartsIn() {


        val animTime = resources.getInteger(
                android.R.integer.config_longAnimTime)

        val translationDistance = resources.getDimensionPixelSize(R.dimen.anim_slide_transition_distance)

        spinner.visibility = View.GONE

        rootLayout.alpha = 0.0f
        layout.visibility = View.VISIBLE

        rootLayout.translationY = translationDistance.toFloat()
        rootLayout.scaleY = 0.975f
        rootLayout.scaleX = 0.975f

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
}
