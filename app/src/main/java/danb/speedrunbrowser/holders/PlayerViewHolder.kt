package danb.speedrunbrowser.holders

import android.content.Context
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.TextView

import java.net.MalformedURLException
import java.net.URL

import androidx.recyclerview.widget.RecyclerView
import danb.speedrunbrowser.R
import danb.speedrunbrowser.api.objects.User
import danb.speedrunbrowser.utils.ImageLoader
import danb.speedrunbrowser.utils.ImageViewPlacerConsumer
import io.reactivex.disposables.CompositeDisposable

class PlayerViewHolder(v: View) : RecyclerView.ViewHolder(v) {

    private val mSubscribedIndicator: ImageView = v.findViewById(R.id.imgSubscribedIndicator)
    private val mPlayerImage: ImageView = v.findViewById(R.id.imgPlayerIcon)
    private val mPlayerName: TextView = v.findViewById(R.id.txtPlayerName)

    fun apply(context: Context, disposables: CompositeDisposable, user: User, subscribed: Boolean) {

        mSubscribedIndicator.visibility = if (subscribed) View.VISIBLE else View.INVISIBLE

        if (user.names != null && user.names["international"] != null) {
            mPlayerImage.visibility = View.VISIBLE
            try {
                disposables.add(ImageLoader(context).loadImage(user.iconUrl)
                        .subscribe(ImageViewPlacerConsumer(mPlayerImage)))
            } catch (e: MalformedURLException) {
                Log.w(TAG, "Could not generate player image URL:", e)
            }

        } else
            mPlayerImage.visibility = View.GONE

        user.applyTextView(mPlayerName)
    }

    companion object {
        private val TAG = WatchRunViewHolder::class.java.simpleName
    }
}
