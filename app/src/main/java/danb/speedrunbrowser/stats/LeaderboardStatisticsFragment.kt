package danb.speedrunbrowser.stats

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.setPadding
import androidx.recyclerview.widget.RecyclerView
import danb.speedrunbrowser.R
import danb.speedrunbrowser.RunDetailFragment
import danb.speedrunbrowser.SpeedrunBrowserActivity
import danb.speedrunbrowser.api.SpeedrunMiddlewareAPI
import danb.speedrunbrowser.api.objects.*
import danb.speedrunbrowser.holders.RunViewHolder
import danb.speedrunbrowser.utils.Analytics
import danb.speedrunbrowser.utils.ItemType
import danb.speedrunbrowser.utils.ViewHolderSource
import io.reactivex.disposables.CompositeDisposable
import java.lang.StringBuilder

class LeaderboardStatisticsFragment : StatisticsFragment() {
    override fun onStart() {
        super.onStart()

        val leaderboardId: String? = arguments!!.getString(EXTRA_LEADERBOARD_ID)

        if (leaderboardId != null) {

            Analytics.logItemView(context!!, "leaderboard_chart", leaderboardId)

            onDataReadyListener = {
                activity!!.title =
                        StringBuilder(it.game!!.resolvedName).append(" \u2022 ")
                                .append(makeCategoryNameText(it.category!!, it.level))
                                .toString()

                // add text with leaderboard rules
                addRulesText(it.category)
            }

            addChart(ChartOptions(
                    name = getString(R.string.chart_title_wrs),
                    description = getString(R.string.chart_desc_wrs),
                    identifier = "wrs",
                    setLabels = {
                        val spl = it.split('_')

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
                    yValueFormat = { RunTimes.format(it) ?: it.toString() },
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
                    chartListOnSelected = { viewRun(it as Run) }
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
                    setLabels = {
                        val spl = it.split('_')

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
                    yValueFormat = { RunTimes.format(it) ?: it.toString() },
                    chartListReverse = false,
                    chartListViewHolderSource = object : ViewHolderSource {
                        override fun newViewHolder(ctx: Context?, parent: ViewGroup): RecyclerView.ViewHolder {
                            return RunViewHolder((ctx!!.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater)
                                    .inflate(R.layout.content_leaderboard_list, parent, false), showRank = false)
                        }

                        override fun applyToViewHolder(ctx: Context?, disposables: CompositeDisposable?, holder: RecyclerView.ViewHolder, toApply: Any) {
                            val lbr = LeaderboardRunEntry(run = toApply as Run, obsolete = false)

                            (holder as RunViewHolder).apply(ctx!!, disposables!!, null, lbr)
                        }
                    },
                    chartListOnSelected = { viewRun(it as Run) }
            ))

            setDataSourceAPIResponse(
                    SpeedrunMiddlewareAPI.make(context!!).getLeaderboardMetrics(leaderboardId)
            )
        }
    }

    private fun addRulesText(category: Category) {
        contentView.removeAllViews()

        val rulesText = category.getRulesText(Variable.VariableSelections())

        val rulesTv = TextView(context!!)
        rulesTv.text = getString(R.string.prelude_rules, rulesText)

        rulesTv.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT)

        rulesTv.setPadding(resources.getDimensionPixelSize(R.dimen.half_fab_margin))
        contentView.addView(rulesTv)
    }

    private fun viewRun(run: Run) {
        val intent = Intent(context!!, SpeedrunBrowserActivity::class.java)
        intent.putExtra(SpeedrunBrowserActivity.EXTRA_FRAGMENT_CLASSPATH, RunDetailFragment::class.java.canonicalName)
        intent.putExtra(RunDetailFragment.ARG_RUN_ID, run.id)
        startActivity(intent)
    }

    companion object {
        const val EXTRA_LEADERBOARD_ID = "game_id"
    }
}