package danb.speedrunbrowser.stats

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.view.children
import androidx.fragment.app.Fragment
import danb.speedrunbrowser.api.SpeedrunMiddlewareAPI
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.functions.Consumer
import io.reactivex.schedulers.Schedulers

class StatisticsFragment : Fragment(), Consumer<SpeedrunMiddlewareAPI.APIChartData> {

    private lateinit var layout: LinearLayout

    var dispose: Disposable? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)

        layout = LinearLayout(context)
    }

    override fun onCreateView(inflater: LayoutInflater,
                              container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        return layout
    }

    override fun accept(t: SpeedrunMiddlewareAPI.APIChartData?) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    fun setDataSource(d: Observable<SpeedrunMiddlewareAPI.APIChartData>) {
        dispose = d
            .observeOn(Schedulers.io())
            .subscribeOn(AndroidSchedulers.mainThread())
            .subscribe {
                println("Got data")
                layout.children.forEach { view ->
                    when(view) {
                        is ChartView -> view.chartData = it.charts[view.options.identifier]
                        is MetricView -> {}
                        else -> {}
                    }
                }
            }
    }

    fun addMetric(options: ChartOptions) = layout.addView(MetricView(context!!, options))
    fun addChart(options: ChartOptions) = layout.addView(ChartView(context!!, options))

    fun addTabbedSwitcher(options: TabbedSwitcherOptions) {

    }

    fun clearCharts() {
        layout.removeAllViews()
    }

    override fun onDestroy() {
        super.onDestroy()

        dispose?.dispose()
    }
}
