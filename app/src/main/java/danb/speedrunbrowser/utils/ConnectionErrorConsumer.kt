package danb.speedrunbrowser.utils

import android.content.Context
import android.util.Log

import danb.speedrunbrowser.R

class ConnectionErrorConsumer(internal var ctx: Context) : io.reactivex.functions.Consumer<Throwable> {

    override fun accept(throwable: Throwable) {

        Log.w(TAG, "Could not download something:", throwable)
        try {
            Util.showErrorToast(ctx, ctx.getString(R.string.error_could_not_connect))
        } catch (e: Exception) {
            // TODO: what to do if we cannot show a toast?
        }

    }

    companion object {
        private val TAG = ConnectionErrorConsumer::class.java.simpleName
    }
}
