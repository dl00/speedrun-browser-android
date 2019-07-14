package danb.speedrunbrowser.stats

import android.os.Bundle
import android.os.PersistableBundle
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import androidx.appcompat.app.AppCompatActivity
import danb.speedrunbrowser.api.SpeedrunMiddlewareAPI
import io.reactivex.Observable

abstract class StatisticsActivity : AppCompatActivity() {

    private lateinit var rootFrag: StatisticsFragment

    protected val chartData: SpeedrunMiddlewareAPI.APIChartData?
    get() = rootFrag.chartData

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sv = ScrollView(this)
        sv.id = View.generateViewId()
        setContentView(sv)

        if(savedInstanceState == null) {
            rootFrag = StatisticsFragment()

            supportFragmentManager.beginTransaction()
                    .add(sv.id, rootFrag)
                    .commit()

        }
        else {
            rootFrag = supportFragmentManager.fragments[0] as StatisticsFragment
        }
    }

    override fun onStop() {
        super.onStop()
        rootFrag.clearCharts()
    }

    fun addMetric(options: ChartOptions) = rootFrag.addMetric(options)
    fun addChart(options: ChartOptions) = rootFrag.addChart(options)
    fun addTabbedSwitcher(options: TabbedSwitcherOptions) = rootFrag.addTabbedSwitcher(options)

    fun setDataSource(d: Observable<SpeedrunMiddlewareAPI.APIChartData>) = rootFrag.setDataSource(d)

    fun setDataSourceAPIResponse(d: Observable<SpeedrunMiddlewareAPI.APIChartResponse>) {
        setDataSource(d.map {
            // TODO: Check for error
            it.data
        })
    }

    companion object {
        private val TAG = StatisticsActivity::class.java.simpleName

        protected val MAIN_FRAGMENT_ID = "main"
    }
}
