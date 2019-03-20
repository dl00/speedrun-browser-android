package danb.speedrunbrowser.utils;


import android.content.Context;

import androidx.annotation.NonNull;
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
import io.reactivex.Completable;
import io.reactivex.Flowable;
import io.reactivex.Maybe;

@Database(entities = {AppDatabase.WatchHistoryEntry.class}, version = 1)
public abstract class AppDatabase extends RoomDatabase {

    public static AppDatabase make(Context ctx) {
        return Room.databaseBuilder(ctx, AppDatabase.class, "appdb")
                .build();
    }

    public abstract WatchHistoryDao watchHistoryDao();

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
    public interface WatchHistoryDao {
        @Query("SELECT * FROM WatchHistoryEntry ORDER BY watchDate DESC")
        Flowable<WatchHistoryEntry> getAll();

        @Query("SELECT * FROM WatchHistoryEntry WHERE runId = :runId")
        Maybe<WatchHistoryEntry> get(String runId);

        @Insert(onConflict = OnConflictStrategy.REPLACE)
        Completable record(WatchHistoryEntry... historyEntries);

        @Delete
        Completable delete(WatchHistoryEntry historyEntry);
    }
}
