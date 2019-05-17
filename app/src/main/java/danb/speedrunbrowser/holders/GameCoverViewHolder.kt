package danb.speedrunbrowser.holders

import android.content.Context
import android.view.View
import android.widget.ImageView
import android.widget.TextView

import androidx.recyclerview.widget.RecyclerView
import danb.speedrunbrowser.R
import danb.speedrunbrowser.api.objects.Game
import danb.speedrunbrowser.utils.ImageLoader
import danb.speedrunbrowser.utils.ImageViewPlacerConsumer
import io.reactivex.disposables.CompositeDisposable

class GameCoverViewHolder(v: View) : RecyclerView.ViewHolder(v) {
    private val mName: TextView = v.findViewById(R.id.txtGameName)
    private val mDate: TextView = v.findViewById(R.id.txtReleaseDate)
    private val mRunnersCount: TextView = v.findViewById(R.id.txtPlayerCount)

    private val mCover: ImageView = v.findViewById(R.id.imgGameCover)

    fun apply(ctx: Context, disposables: CompositeDisposable, game: Game) {
        mName.text = game.names["international"]
        mDate.text = game.releaseDate
        mRunnersCount.text = ""

        if (game.assets.coverLarge != null)
            disposables.add(ImageLoader(ctx).loadImage(game.assets.coverLarge.uri)
                    .subscribe(ImageViewPlacerConsumer(mCover)))
        else {
        }
        // TODO: In the extremely unlikely case there is no cover, might have to replace with dummy image
    }
}
