package danb.speedrunbrowser.holders

import android.content.Context
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import danb.speedrunbrowser.R
import danb.speedrunbrowser.api.objects.Stream
import io.reactivex.disposables.CompositeDisposable

class StreamViewHolder(v: View) : RecyclerView.ViewHolder(v) {
    private val mThumbnailImage: ImageView = v.findViewById(R.id.imgVideoThumbnail)
    private val mPlayerName: TextView = v.findViewById(R.id.txtPlayerName)
    private val mGameName: TextView = v.findViewById(R.id.txtGameName)
    private val mViewerCount: TextView = v.findViewById(R.id.txtRank)

    private val mRankImg: ImageView = v.findViewById(R.id.imgRank)

    fun apply(context: Context, disposables: CompositeDisposable, strm: Stream) {

    }
}