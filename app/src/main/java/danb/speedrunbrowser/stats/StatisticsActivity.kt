package danb.speedrunbrowser.stats

import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.appcompat.app.AppCompatActivity
import danb.speedrunbrowser.api.SpeedrunMiddlewareAPI
import io.reactivex.Observable

abstract class StatisticsActivity : AppCompatActivity() {

    private lateinit var rootFrag: StatisticsFragment

    var onDataReadyListener: ((data: SpeedrunMiddlewareAPI.APIChartData) -> Unit)?
    get() = rootFrag.onDataReadyListener
    set(value) { rootFrag.onDataReadyListener = value }

    protected val chartData: SpeedrunMiddlewareAPI.APIChartData?
    get() = rootFrag.chartData

    protected lateinit var contentView: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        contentView = LinearLayout(this)
        contentView.orientation = LinearLayout.VERTICAL

        val statsFrame = FrameLayout(this)
        statsFrame.id = View.generateViewId()

        val rootLayout = LinearLayout(this)
        rootLayout.addView(contentView)
        rootLayout.addView(statsFrame)
        rootLayout.orientation = LinearLayout.VERTICAL

        val sv = ScrollView(this)
        sv.addView(rootLayout)

        setContentView(sv)

        if(savedInstanceState == null) {
            rootFrag = StatisticsFragment()

            supportFragmentManager.beginTransaction()
                    .add(statsFrame.id, rootFrag)
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
}
