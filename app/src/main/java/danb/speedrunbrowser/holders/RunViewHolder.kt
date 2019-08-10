package danb.speedrunbrowser.holders

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

import com.google.android.flexbox.FlexboxLayout

import androidx.recyclerview.widget.RecyclerView
import com.google.android.flexbox.AlignContent
import com.google.android.flexbox.AlignItems
import com.google.android.flexbox.JustifyContent
import danb.speedrunbrowser.R
import danb.speedrunbrowser.api.objects.Game
import danb.speedrunbrowser.api.objects.LeaderboardRunEntry
import danb.speedrunbrowser.api.objects.User
import danb.speedrunbrowser.utils.ImageLoader
import danb.speedrunbrowser.utils.ImageViewPlacerConsumer
import io.reactivex.disposables.CompositeDisposable

class RunViewHolder(v: View, val showRank: Boolean = true) : RecyclerView.ViewHolder(v) {

    private val mPlayerNames: FlexboxLayout = v.findViewById(R.id.txtPlayerNames)
    private val mRunTime: TextView = v.findViewById(R.id.txtRunTime)
    private val mRunDate: TextView = v.findViewById(R.id.txtRunDate)
    private val mRank: TextView = v.findViewById(R.id.txtRank)

    private val mRankImg: ImageView = v.findViewById(R.id.imgRank)

    fun apply(context: Context, disposables: CompositeDisposable, game: Game?, entry: LeaderboardRunEntry) {

        mPlayerNames.removeAllViews()
        var first = true
        for (player in entry.run.players!!) {

            val iv = ImageView(context)
            player.applyCountryImage(iv)
            mPlayerNames.addView(iv)

            val tv = TextView(context)
            tv.textSize = 16f
            player.applyTextView(tv)

            if (!first) {
                val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                lp.setMargins(context.resources.getDimensionPixelSize(R.dimen.half_fab_margin), 0, 0, 0)
                tv.layoutParams = lp
            } else
                first = false

            mPlayerNames.addView(tv)
        }

        mRunTime.text = entry.run.times!!.time
        mRunDate.text = entry.run.date

        if(showRank)
            mRank.text = entry.placeName
        else {
            (mRank.parent as View).visibility = View.GONE
            mPlayerNames.justifyContent = JustifyContent.FLEX_START
        }

        val il = ImageLoader(context)

        if(game != null) {
            when {
                entry.place == 1 && game.assets.trophy1st != null ->
                    disposables.add(il.loadImage(game.assets.trophy1st.uri)
                            .subscribe(ImageViewPlacerConsumer(mRankImg)))

                entry.place == 2 && game.assets.trophy2nd != null ->
                    disposables.add(il.loadImage(game.assets.trophy2nd.uri)
                            .subscribe(ImageViewPlacerConsumer(mRankImg)))

                entry.place == 3 && game.assets.trophy3rd != null ->
                    disposables.add(il.loadImage(game.assets.trophy3rd.uri)
                            .subscribe(ImageViewPlacerConsumer(mRankImg)))

                entry.place == 4 && game.assets.trophy4th != null ->
                    disposables.add(il.loadImage(game.assets.trophy4th.uri)
                            .subscribe(ImageViewPlacerConsumer(mRankImg)))

                else -> mRankImg.setImageDrawable(ColorDrawable(Color.TRANSPARENT))
            }
        }
    }
}
