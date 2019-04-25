package danb.speedrunbrowser.utils;


import android.content.Context;

import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Dao;
import androidx.room.Database;
import androidx.room.Delete;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.PrimaryKey;
import androidx.room.Query;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;
import danb.speedrunbrowser.BuildConfig;
import io.reactivex.Completable;
import io.reactivex.Maybe;
import io.reactivex.Single;

@Database(entities = {AppDatabase.WatchHistoryEntry.class, AppDatabase.Subscription.class}, version = 3)
public abstract class AppDatabase extends RoomDatabase {

    public static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS `Subscription` (`resourceId` TEXT NOT NULL, `type` TEXT NOT NULL, PRIMARY KEY(`resourceId`))");
        }
    };

    public static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE `Subscription` ADD COLUMN `name` TEXT NOT NULL DEFAULT '';");
        }
    };

    public static AppDatabase make(Context ctx) {
        return Room.databaseBuilder(ctx, AppDatabase.class, "appdb")
                .addMigrations(
                        MIGRATION_1_2,
                        MIGRATION_2_3
                )
                .build();
    }

    public abstract WatchHistoryDao watchHistoryDao();
    public abstract SubscriptionDao subscriptionDao();

    @Entity
    public static class Subscription {
        Subscription() {}

        @Ignore
        public Subscription(@NonNull String type, @NonNull String resourceId, @NonNull String name) {
            this.resourceId = resourceId;
            this.type = type;
            this.name = name;
        }

        @PrimaryKey
        @NonNull
        public String resourceId;

        @NonNull
        public String type;

        @NonNull
        public String name;

        public String getFCMTopic() {
            if(BuildConfig.DEBUG) {
                return "debug_" + type + "_" + resourceId;
            }
            else
                return "release_" + type + "_" + resourceId;
        }

        @Override
        public boolean equals(@Nullable Object obj) {
            return obj instanceof Subscription && ((Subscription)obj).resourceId.equals(resourceId);
        }

        @Override
        public int hashCode() {
            return resourceId.hashCode();
        }
    }

    @Entity
    public static class WatchHistoryEntry {

        WatchHistoryEntry(@NonNull String runId) {
            this.runId = runId;
        }

        @Ignore
        public WatchHistoryEntry(@NonNull String runId, long seekPos) {
            this.runId = runId;

            this.watchDate = System.currentTimeMillis();

            this.seekPos = seekPos;
        }

        @PrimaryKey
        @NonNull
        public String runId;

        public long watchDate;
        public long seekPos;
    }

    @Dao
    public interface SubscriptionDao {
        @Query("SELECT * FROM Subscription WHERE resourceId = :resourceId")
        Maybe<Subscription> get(String resourceId);

        @Query("SELECT * FROM Subscription WHERE type = :type ORDER BY name LIMIT 40 OFFSET :offset")
        Single<List<Subscription>> listOfType(String type, int offset);

        @Query("SELECT * FROM Subscription WHERE type = :type AND resourceId LIKE :idPrefix || '%'")
        Single<List<Subscription>> listOfTypeWithIDPrefix(String type, String idPrefix);

        @Query("SELECT * FROM Subscription WHERE type = :type AND name LIKE :filter ORDER BY name LIMIT 40 OFFSET :offset")
        Single<List<Subscription>> listOfTypeWithFilter(String type, String filter, int offset);

        @Insert(onConflict = OnConflictStrategy.REPLACE)
        Completable subscribe(Subscription... subscriptions);

        @Delete
        Completable unsubscribe(Subscription... subscriptions);
    }

    @Dao
    public interface WatchHistoryDao {
        @Query("SELECT * FROM WatchHistoryEntry ORDER BY watchDate DESC LIMIT 40 OFFSET :offset")
        Single<List<WatchHistoryEntry>> getMany(int offset);

        @Query("SELECT * FROM WatchHistoryEntry WHERE runId = :runId")
        Maybe<WatchHistoryEntry> get(String runId);

        @Insert(onConflict = OnConflictStrategy.REPLACE)
        Completable record(WatchHistoryEntry... historyEntries);

        @Delete
        Completable delete(WatchHistoryEntry historyEntry);
    }
}
