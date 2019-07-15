package danb.speedrunbrowser.stats

import danb.speedrunbrowser.R
import danb.speedrunbrowser.api.SpeedrunMiddlewareAPI
import danb.speedrunbrowser.api.objects.RunTimes
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
                    yValueFormat = { RunTimes.format(it) ?: it.toString() }
            ))

            addChart(ChartOptions(
                    name = getString(R.string.chart_title_volume),
                    description = getString(R.string.chart_desc_volume),
                    identifier = "volume",
                    setLabels = { getString(R.string.chart_legend_volume) },
                    xValueFormat = ::formatMonthYear
            ))

            addChart(ChartOptions(
                    name = getString(R.string.chart_title_volume),
                    description = getString(R.string.chart_desc_volume),
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