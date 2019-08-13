package danb.speedrunbrowser.utils

import android.content.Context
import android.net.Uri
import android.os.Bundle

import com.google.firebase.analytics.FirebaseAnalytics

object Analytics {

    private var analytics: FirebaseAnalytics? = null

    private val EVENT_SUBSCRIBE = "subscribe"
    private val EVENT_UNSUBSCRIBE = "unsubscribe"
    private val EVENT_DELIVER_NOTIFICATION = "deliver_notification"

    private val EVENT_NOT_FOUND = "url_not_found"

    private fun getFBAnalytics(ctx: Context): FirebaseAnalytics {
        if (analytics == null) {
            analytics = FirebaseAnalytics.getInstance(ctx)
        }

        return analytics!!
    }

    fun logItemView(ctx: Context, type: String, id: String) {
        val bundle = Bundle()
        bundle.putString(FirebaseAnalytics.Param.ITEM_ID, id)
        bundle.putString(FirebaseAnalytics.Param.ITEM_CATEGORY, type)
        getFBAnalytics(ctx).logEvent(FirebaseAnalytics.Event.VIEW_ITEM, bundle)
    }

    fun logSubscribeChange(ctx: Context, sub: AppDatabase.Subscription, subscribed: Boolean) {
        val bundle = Bundle()
        bundle.putString(FirebaseAnalytics.Param.ITEM_ID, sub.resourceId)
        bundle.putString(FirebaseAnalytics.Param.ITEM_CATEGORY, sub.type)
        getFBAnalytics(ctx).logEvent(if (subscribed) EVENT_SUBSCRIBE else EVENT_UNSUBSCRIBE, bundle)
    }

    fun logDeliverNotification(ctx: Context, subjectId: String) {
        val bundle = Bundle()
        bundle.putString(FirebaseAnalytics.Param.ITEM_ID, subjectId)
        getFBAnalytics(ctx).logEvent(EVENT_DELIVER_NOTIFICATION, bundle)
    }

    fun logNotFound(ctx: Context, uri: Uri) {
        val bundle = Bundle()
        bundle.putString(FirebaseAnalytics.Param.SOURCE, uri.toString())
        getFBAnalytics(ctx).logEvent(EVENT_NOT_FOUND, bundle)
    }
}
