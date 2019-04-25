package danb.speedrunbrowser.utils;

import android.content.Context;
import android.os.Bundle;

import com.google.firebase.analytics.FirebaseAnalytics;

public class Analytics {

    private static FirebaseAnalytics analytics = null;

    private static final String EVENT_SUBSCRIBE = "subscribe";
    private static final String EVENT_UNSUBSCRIBE = "unsubscribe";
    private static final String EVENT_DELIVER_NOTIFICATION = "deliver_notification";

    private static FirebaseAnalytics getFBAnalytics(Context ctx) {
        if(analytics == null) {
            analytics = FirebaseAnalytics.getInstance(ctx);
        }

        return analytics;
    }

    public static void logItemView(Context ctx, String type, String id) {
        Bundle bundle = new Bundle();
        bundle.putString(FirebaseAnalytics.Param.ITEM_ID, id);
        bundle.putString(FirebaseAnalytics.Param.ITEM_CATEGORY, type);
        getFBAnalytics(ctx).logEvent(FirebaseAnalytics.Event.VIEW_ITEM, bundle);
    }

    public static void logSubscribeChange(Context ctx, AppDatabase.Subscription sub, boolean subscribed) {
        Bundle bundle = new Bundle();
        bundle.putString(FirebaseAnalytics.Param.ITEM_ID, sub.resourceId);
        bundle.putString(FirebaseAnalytics.Param.ITEM_CATEGORY, sub.type);
        getFBAnalytics(ctx).logEvent(subscribed ? EVENT_SUBSCRIBE : EVENT_UNSUBSCRIBE, bundle);
    }

    public static void logDeliverNotification(Context ctx, String subjectId) {
        Bundle bundle = new Bundle();
        bundle.putString(FirebaseAnalytics.Param.ITEM_ID, subjectId);
        getFBAnalytics(ctx).logEvent(EVENT_DELIVER_NOTIFICATION, bundle);
    }
}
