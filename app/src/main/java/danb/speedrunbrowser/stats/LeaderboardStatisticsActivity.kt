package danb.speedrunbrowser.stats

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import danb.speedrunbrowser.R
import danb.speedrunbrowser.api.SpeedrunMiddlewareAPI
import danb.speedrunbrowser.api.objects.LeaderboardRunEntry
import danb.speedrunbrowser.api.objects.Run
import danb.speedrunbrowser.api.objects.RunTimes
import danb.speedrunbrowser.holders.RunViewHolder
import danb.speedrunbrowser.utils.ItemType
import danb.speedrunbrowser.utils.ViewHolderSource
import io.reactivex.disposables.CompositeDisposable
import java.lang.StringBuilder

class LeaderboardStatisticsActivity : StatisticsActivity() {
    override fun onStart() {
        super.onStart()

        val leaderboardId: String? = intent.getStringExtra(EXTRA_LEADERBOARD_ID)

        if(leaderboardId != null) {


            addChart(ChartOptions(
                    name = getString(R.string.chart_title_wrs),
                    description = getString(R.string.chart_desc_wrs),
                    identifier = "wrs",
                    setLabels = {
                        val spl = it.split('_')

                        val builder = StringBuilder()

                        for(i in 0 until spl.size - 1 step 2) {
                            val variableId = spl[i]
                            val valueId = spl[i + 1]

                            val variable = chartData!!.category?.variables?.find { v -> v.id == variableId }

                            if(variable == null)
                                continue
                            else {
                                val valueLabel = variable.values[valueId]?.label ?: continue
                                builder.append(variable.name).append(": ").append(valueLabel).append(' ')
                            }
                        }

                        builder.toString()
                    },
                    xValueFormat = ::formatMonthYear,
                    yValueFormat = { RunTimes.format(it) ?: it.toString() },
                    chartListViewHolderSource = object : ViewHolderSource {
                        override fun newViewHolder(ctx: Context?, parent: ViewGroup): RecyclerView.ViewHolder {
                            return RunViewHolder((ctx!!.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater).inflate(R.layout.content_leaderboard_list, parent, false))
                        }

                        override fun applyToViewHolder(ctx: Context?, disposables: CompositeDisposable?, holder: RecyclerView.ViewHolder, toApply: Any) {
                            val lbr = LeaderboardRunEntry(run = toApply as Run)

                            (holder as RunViewHolder).apply(ctx!!, disposables!!, null, lbr)
                        }
                    }
            ))

            addChart(ChartOptions(
                    name = getString(R.string.chart_title_volume),
                    description = getString(R.string.chart_desc_volume),
                    identifier = "volume",
                    setLabels = { getString(R.string.chart_legend_volume) },
                    xValueFormat = ::formatMonthYear
            ))

            addChart(ChartOptions(
                    name = getString(R.string.chart_title_distribution),
                    description = getString(R.string.chart_desc_distribution),
                    identifier = "distribution",
                    setLabels = { v -> chartData!!.category?.variables?.find { it.id == v }?.name ?: v },
                    yValueFormat = { RunTimes.format(it) ?: it.toString() }
            ))

            setDataSourceAPIResponse(
                    SpeedrunMiddlewareAPI.make().getLeaderboardMetrics(leaderboardId)
            )
        }
    }

    companion object {
        const val EXTRA_LEADERBOARD_ID = "game_id"
    }
}