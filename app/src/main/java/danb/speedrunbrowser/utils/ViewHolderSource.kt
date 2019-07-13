package danb.speedrunbrowser.utils

import android.content.Context
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import io.reactivex.disposables.CompositeDisposable

interface ViewHolderSource {
    fun newViewHolder(ctx: Context?, parent: ViewGroup): RecyclerView.ViewHolder
    fun applyToViewHolder(ctx: Context?, disposables: CompositeDisposable?, holder: RecyclerView.ViewHolder, toApply: Any)
}