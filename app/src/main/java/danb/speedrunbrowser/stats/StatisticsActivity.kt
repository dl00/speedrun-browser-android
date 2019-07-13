package danb.speedrunbrowser.stats

import android.os.Bundle
import android.os.PersistableBundle
import android.view.View
import android.widget.ScrollView
import androidx.appcompat.app.AppCompatActivity

abstract class StatisticsActivity : AppCompatActivity() {

    private lateinit var rootFrag: StatisticsFragment

    override fun onCreate(savedInstanceState: Bundle?, persistentState: PersistableBundle?) {
        super.onCreate(savedInstanceState, persistentState)

        val v = View(this)
        val sv = ScrollView(this)
        sv.addView(v)
        setContentView(sv)

        if(savedInstanceState == null) {
            rootFrag = StatisticsFragment()

            supportFragmentManager.beginTransaction()
                    .add(v.id, rootFrag)
                    .commit()

        }
        else {
            rootFrag = supportFragmentManager.fragments[0] as StatisticsFragment
        }
    }

    fun addMetric(options: ChartOptions) = rootFrag.addMetric(options)
    fun addChart(options: ChartOptions) = rootFrag.addChart(options)
    fun addTabbedSwitcher(options: TabbedSwitcherOptions) = rootFrag.addTabbedSwitcher(options)

    companion object {
        private val TAG = StatisticsActivity::class.java.simpleName

        protected val MAIN_FRAGMENT_ID = "main"
    }
}
