package danb.speedrunbrowser.holders

import android.content.Context
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import danb.speedrunbrowser.R
import danb.speedrunbrowser.api.objects.Stream
import danb.speedrunbrowser.utils.ImageLoader
import danb.speedrunbrowser.utils.ImageViewPlacerConsumer
import io.reactivex.disposables.CompositeDisposable
import java.net.URL

class StreamViewHolder(v: View) : RecyclerView.ViewHolder(v) {
    private val mThumbnailImage: ImageView = v.findViewById(R.id.imgVideoThumbnail)
    private val mPlayerName: TextView = v.findViewById(R.id.txtPlayerName)
    private val mStreamTitle: TextView = v.findViewById(R.id.txtStreamTitle)
    private val mGameName: TextView = v.findViewById(R.id.txtGameName)
    private val mViewerCount: TextView = v.findViewById(R.id.txtViewerCount)

    fun apply(context: Context, disposables: CompositeDisposable, strm: Stream) {

        strm.user.applyTextView(mPlayerName)

        if(strm.game?.names != null)
            mGameName.text = strm.game.names["international"]

        mStreamTitle.text = strm.title
        mViewerCount.text = context.resources.getString(R.string.label_stream_viewers, strm.viewer_count.toString())

        println(strm.thumbnail_url)
        println(strm.viewer_count)

        val finalThumbnailUrl = URL(strm.thumbnail_url
                .replace("{width}", mThumbnailImage.width.toString())
                .replace("{height}", mThumbnailImage.height.toString()))

        disposables.add(ImageLoader(context).loadImage(finalThumbnailUrl, false)
                .subscribe(ImageViewPlacerConsumer(mThumbnailImage)))
    }
}