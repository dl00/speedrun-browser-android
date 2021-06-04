package danb.speedrunbrowser.holders

import android.content.Context
import android.view.LayoutInflater

import com.yayandroid.parallaxrecyclerview.ParallaxViewHolder
import danb.speedrunbrowser.R

class ProgressSpinnerViewHolder(ctx: Context) :
        ParallaxViewHolder((ctx.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater)
                .inflate(R.layout.fragment_progress, null)) {

    override fun getParallaxImageId(): Int {
        return R.id.parallax
    }
}
