package danb.speedrunbrowser.stats

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import danb.speedrunbrowser.R
import danb.speedrunbrowser.RunDetailFragment
import danb.speedrunbrowser.SpeedrunBrowserActivity
import danb.speedrunbrowser.api.SpeedrunMiddlewareAPI
import danb.speedrunbrowser.api.objects.Game
import danb.speedrunbrowser.api.objects.LeaderboardRunEntry
import danb.speedrunbrowser.api.objects.Run
import danb.speedrunbrowser.api.objects.RunTimes
import danb.speedrunbrowser.holders.RunViewHolder
import danb.speedrunbrowser.utils.Analytics
import danb.speedrunbrowser.utils.ViewHolderSource
import io.reactivex.disposables.CompositeDisposable
import java.lang.StringBuilder

class PlayerStatisticsFragment : StatisticsFragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val v = super.onCreateView(inflater, container, savedInstanceState)

        val playerId: String? = arguments!!.getString(ARG_PLAYER_ID)

        if(playerId != null) {

            Analytics.logItemView(context!!, "player_chart", playerId)

            addChart(ChartOptions(
                    name = getString(R.string.chart_title_favorite_games),
                    description = getString(R.string.chart_desc_favorite_games),
                    identifier = "favorite_games",
                    pieLabels = {
                        (it as Game).resolvedName
                    },
                    setLabels = {""}
            ))

            addMetric(ChartOptions(
                    name = getString(R.string.metric_title_total_runs),
                    description = getString(R.string.metric_desc_total_runs),
                    identifier = "total_run_count",
                    xValueFormat = ::formatBigNumber,
                    setLabels = { "" }
            ))

            addChart(ChartOptions(
                    name = getString(R.string.chart_title_volume),
                    description = getString(R.string.chart_desc_volume),
                    identifier = "volume",
                    setLabels = { getString(R.string.chart_legend_volume) },
                    xValueFormat = ::formatMonthYear
            ))

            /*addMetric(ChartOptions(
                    name = getString(R.string.metric_title_total_leaderboards),
                    description = getString(R.string.metric_desc_total_leaderboards),
                    identifier = "total_leaderboards",
                    xValueFormat = ::formatBigNumber,
                    setLabels = { "" }
            ))*/

            addMetric(ChartOptions(
                    name = getString(R.string.metric_title_total_run_time),
                    description = getString(R.string.metric_desc_total_run_time),
                    identifier = "total_run_time",
                    xValueFormat = ::formatTime,
                    setLabels = { "" }
            ))

            addMetrics(listOf(
                    ChartOptions(
                            name = getString(R.string.metric_title_total_runs_full_game),
                            description = getString(R.string.metric_desc_total_runs),
                            identifier = "full_game_run_count",
                            xValueFormat = ::formatBigNumber,
                            setLabels = { "" }
                    ),
                    ChartOptions(
                            name = getString(R.string.metric_title_total_runs_level),
                            description = getString(R.string.metric_desc_total_runs),
                            identifier = "level_run_count",
                            xValueFormat = ::formatBigNumber,
                            setLabels = { "" }
                    )
                )
            )

            val api = SpeedrunMiddlewareAPI.make(context!!)

            onDataReadyListener = {
                activity!!.title = it.player?.resolvedName

                // get the list of games that this player has played
                val games = it.charts["favorite_games"]?.data?.get("main")?.map { itt -> itt.obj as Game }

                addTabbedSwitcher(TabbedSwitcherOptions(
                        name = getString(R.string.chart_title_player_game),
                        description = getString(R.string.chart_desc_player_game),
                        identifier = "player_game",
                        subcharts = listOf(
                                ChartOptions(
                                        name = getString(R.string.chart_title_pbs),
                                        description = getString(R.string.chart_desc_pbs),
                                        identifier = "pbs",
                                        setLabels = { k ->
                                            val spl = k.split('_')

                                            val builder = StringBuilder()

                                            for (i in 0 until spl.size - 1 step 2) {
                                                val variableId = spl[i]
                                                val valueId = spl[i + 1]

                                                val variable = chartData!!.category?.variables?.find { v -> v.id == variableId }

                                                if (variable == null)
                                                    continue
                                                else {
                                                    val valueLabel = variable.values[valueId]?.label ?: continue
                                                    builder.append(valueLabel).append(' ')
                                                }
                                            }

                                            builder.toString()
                                        },
                                        xValueFormat = ::formatMonthYear,
                                        yValueFormat = { t -> RunTimes.format(t) ?: t.toString() },
                                        chartListViewHolderSource = object : ViewHolderSource {
                                            override fun newViewHolder(ctx: Context?, parent: ViewGroup): RecyclerView.ViewHolder {
                                                return RunViewHolder((ctx!!.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater)
                                                        .inflate(R.layout.content_leaderboard_list, parent, false), showRank = false)
                                            }

                                            override fun applyToViewHolder(ctx: Context?, disposables: CompositeDisposable?, holder: RecyclerView.ViewHolder, toApply: Any) {
                                                val lbr = LeaderboardRunEntry(run = toApply as Run)

                                                (holder as RunViewHolder).apply(ctx!!, disposables!!, null, lbr)
                                            }
                                        },
                                        chartListReverse = true,
                                        chartListOnSelected = { r -> viewRun(r as Run) }
                                )
                        ),
                        tabs = games?.map { g ->
                            TabOption(
                                    id = g.id,
                                    label = g.resolvedName,
                                    dataSource = api.getUserGameMetrics(g.id, playerId)
                            )
                        } ?: listOf()
                ))
            }

            setDataSourceAPIResponse(
                    api.getUserMetrics(playerId)
            )
        }

        return v
    }

    private fun viewRun(run: Run) {
        val intent = Intent(context!!, SpeedrunBrowserActivity::class.java)
        intent.putExtra(SpeedrunBrowserActivity.EXTRA_FRAGMENT_CLASSPATH, RunDetailFragment::class.java.canonicalName)
        intent.putExtra(RunDetailFragment.ARG_RUN_ID, run.id)
        startActivity(intent)
    }

    companion object {
        const val ARG_PLAYER_ID = "player_id"
    }
}