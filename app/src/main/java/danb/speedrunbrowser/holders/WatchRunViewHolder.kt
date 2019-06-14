package danb.speedrunbrowser.holders

import android.content.Context
import android.view.View
import android.widget.ImageView
import android.widget.TextView

import androidx.recyclerview.widget.RecyclerView
import danb.speedrunbrowser.R
import danb.speedrunbrowser.api.objects.Game
import danb.speedrunbrowser.api.objects.LeaderboardRunEntry
import danb.speedrunbrowser.utils.ImageLoader
import danb.speedrunbrowser.utils.ImageViewPlacerConsumer
import io.reactivex.disposables.CompositeDisposable

class WatchRunViewHolder(v: View) : RecyclerView.ViewHolder(v) {

    private val mLeaderboardHolder: RunViewHolder = RunViewHolder(v)

    private val mGameName: TextView = v.findViewById(R.id.txtGameName)
    private val mPlayerImage: ImageView? = null
    private val mGameImage: ImageView = v.findViewById(R.id.imgGameIcon)

    fun apply(context: Context, disposables: CompositeDisposable, game: Game, entry: LeaderboardRunEntry) {

        mLeaderboardHolder.apply(context, disposables, game, entry)

        var gameAndCategoryText = entry.run.game?.resolvedName ?: ""

        if(entry.run.category != null)
            gameAndCategoryText += " \u2022 " + entry.run.category.name

        if (entry.run.level?.name != null)
            gameAndCategoryText += " \u2022 " + entry.run.level.name

        mGameName.text = gameAndCategoryText

        if (entry.run.game != null && entry.run.game.assets.coverLarge != null) {
            mGameImage.visibility = View.VISIBLE
            disposables.add(ImageLoader(context).loadImage(entry.run.game.assets.coverLarge.uri)
                    .subscribe(ImageViewPlacerConsumer(mGameImage)))
        } else
            mGameImage.visibility = View.GONE
    }

    companion object {
        private val TAG = WatchRunViewHolder::class.java.simpleName
    }
}
