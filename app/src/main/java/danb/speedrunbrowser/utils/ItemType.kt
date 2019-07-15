package danb.speedrunbrowser.utils

import android.app.Activity
import android.app.ActivityOptions
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import danb.speedrunbrowser.R
import danb.speedrunbrowser.api.objects.Game
import danb.speedrunbrowser.api.objects.LeaderboardRunEntry
import danb.speedrunbrowser.api.objects.Run
import danb.speedrunbrowser.api.objects.User
import danb.speedrunbrowser.holders.GameCoverViewHolder
import danb.speedrunbrowser.holders.PlayerViewHolder
import danb.speedrunbrowser.holders.WatchRunViewHolder
import io.reactivex.disposables.CompositeDisposable
import java.io.Serializable

enum class ItemType constructor(val layout: Int) : Serializable, ViewHolderSource {
    GAMES(R.layout.fragment_game_list),
    PLAYERS(R.layout.fragment_player_list),
    RUNS(R.layout.fragment_run_list);

    override fun newViewHolder(ctx: Context?, parent: ViewGroup): RecyclerView.ViewHolder {
        return when (this) {
            GAMES -> GameCoverViewHolder((ctx!!.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater).inflate(R.layout.content_game_cover, parent, false))
            PLAYERS -> PlayerViewHolder((ctx!!.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater).inflate(R.layout.content_player_list, parent, false))
            RUNS -> WatchRunViewHolder((ctx!!.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater).inflate(R.layout.content_watch_list, parent, false))
        }
    }

    override fun applyToViewHolder(ctx: Context?, disposables: CompositeDisposable?, holder: RecyclerView.ViewHolder, toApply: Any) {
        when (this) {
            GAMES -> (holder as GameCoverViewHolder).apply(ctx!!, disposables!!, toApply as Game)
            PLAYERS -> (holder as PlayerViewHolder).apply(ctx!!, disposables!!, toApply as User, false)
            RUNS -> {
                val lbr: LeaderboardRunEntry = when(toApply) {
                    is LeaderboardRunEntry -> toApply
                    else -> LeaderboardRunEntry(run = toApply as Run)
                }

                (holder as WatchRunViewHolder).apply(ctx!!, disposables!!, lbr.run.game!!, lbr)
            }
        }
    }

    fun makeSceneTransition(activity: Activity?, v: View): ActivityOptions? {
        return null
    }

    override fun toString(): String {
        return name
    }
}