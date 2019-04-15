package danb.speedrunbrowser.utils;

import android.content.Context;
import android.util.Log;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.messaging.FirebaseMessaging;

import androidx.annotation.NonNull;
import io.reactivex.Completable;
import io.reactivex.CompletableEmitter;
import io.reactivex.CompletableOnSubscribe;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;

public class SubscriptionChanger {
    private static final String TAG = SubscriptionChanger.class.getSimpleName();

    private Context ctx;
    private AppDatabase db;

    public SubscriptionChanger(Context ctx, AppDatabase db) {
        this.ctx = ctx;
        this.db = db;
    }

    public Completable subscribeTo(final AppDatabase.Subscription sub) {
        return Completable.create(new CompletableOnSubscribe() {
            @Override
            public void subscribe(final CompletableEmitter emitter) throws Exception {
                FirebaseMessaging.getInstance().subscribeToTopic(sub.getFCMTopic())
                    .addOnCompleteListener(new OnCompleteListener<Void>() {
                        @Override
                        public void onComplete(@NonNull Task<Void> task) {
                            Log.d(TAG, "Subscribed: " + sub.getFCMTopic());
                            emitter.onComplete();
                        }
                    });
            }
        })
            .andThen(db.subscriptionDao().subscribe(sub).subscribeOn(Schedulers.io()))
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread());

    }

    public Completable unsubscribeFrom(final AppDatabase.Subscription sub) {
        return Completable.create(new CompletableOnSubscribe() {
            @Override
            public void subscribe(final CompletableEmitter emitter) throws Exception {
                FirebaseMessaging.getInstance().unsubscribeFromTopic(sub.getFCMTopic())
                    .addOnCompleteListener(new OnCompleteListener<Void>() {
                        @Override
                        public void onComplete(@NonNull Task<Void> task) {
                            Log.d(TAG, "Unsubscribed: " + sub.getFCMTopic());
                            emitter.onComplete();
                        }
                    });
            }
        })
            .andThen(db.subscriptionDao().unsubscribe(sub).subscribeOn(Schedulers.io()))
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread());
    }
}
