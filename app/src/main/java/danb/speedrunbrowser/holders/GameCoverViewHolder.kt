package danb.speedrunbrowser.holders

import android.content.Context
import android.view.View
import android.widget.ImageView
import android.widget.TextView

import androidx.recyclerview.widget.RecyclerView
import com.yayandroid.parallaxrecyclerview.ParallaxViewHolder
import danb.speedrunbrowser.R
import danb.speedrunbrowser.api.objects.Game
import danb.speedrunbrowser.utils.ImageLoader
import danb.speedrunbrowser.utils.ImageViewPlacerConsumer
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers

class GameCoverViewHolder(v: View) : ParallaxViewHolder(v) {
    private val mName: TextView = v.findViewById(R.id.txtGameName)

    private val mCover: ImageView = v.findViewById(R.id.imgGameCover)

    fun apply(ctx: Context, disposables: CompositeDisposable, game: Game) {
        mName.text = game.resolvedName

        if (game.assets.coverLarge != null)
            disposables.add(ImageLoader(ctx).loadImage(game.assets.coverLarge.uri)
                    .subscribeOn(Schedulers.io())
                    .subscribe(ImageViewPlacerConsumer(mCover)))
        else {
        }
        // TODO: In the extremely unlikely case there is no cover, might have to replace with dummy image
    }

    override fun getParallaxImageId(): Int {
        return R.id.imgGameCover
    }
}
