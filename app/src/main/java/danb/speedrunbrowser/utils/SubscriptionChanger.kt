package danb.speedrunbrowser.utils

import android.content.Context
import android.util.Log

import com.google.firebase.messaging.FirebaseMessaging
import io.reactivex.Completable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers

class SubscriptionChanger(private val ctx: Context, private val db: AppDatabase) {

    fun subscribeTo(sub: AppDatabase.Subscription): Completable {
        return Completable.create { emitter ->
            Analytics.logSubscribeChange(ctx, sub, true)

            FirebaseMessaging.getInstance().subscribeToTopic(sub.fcmTopic)
                    .addOnCompleteListener {
                        Log.d(TAG, "Subscribed: " + sub.fcmTopic)
                        emitter.onComplete()
                    }
        }
                .andThen(db.subscriptionDao().subscribe(sub).subscribeOn(Schedulers.io()))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())

    }

    fun unsubscribeFrom(sub: AppDatabase.Subscription): Completable {
        return Completable.create { emitter ->
            Analytics.logSubscribeChange(ctx, sub, false)

            FirebaseMessaging.getInstance().unsubscribeFromTopic(sub.fcmTopic)
                    .addOnCompleteListener {
                        Log.d(TAG, "Unsubscribed: " + sub.fcmTopic)
                        emitter.onComplete()
                    }
        }
                .andThen(db.subscriptionDao().unsubscribe(sub).subscribeOn(Schedulers.io()))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
    }

    companion object {
        val TAG: String = SubscriptionChanger::class.java.simpleName
    }
}
