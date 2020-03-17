package danb.speedrunbrowser.utils


import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import danb.speedrunbrowser.BuildConfig
import io.reactivex.Completable
import io.reactivex.Maybe
import io.reactivex.Single

@Database(entities = [AppDatabase.WatchHistoryEntry::class, AppDatabase.Subscription::class], version = 3)
abstract class AppDatabase : RoomDatabase() {

    abstract fun watchHistoryDao(): WatchHistoryDao
    abstract fun subscriptionDao(): SubscriptionDao

    @Entity
    data class Subscription(
            var type: String,
            @PrimaryKey var resourceId: String,
            var name: String
    ) {

        val fcmTopic: String
            get() = if (BuildConfig.DEBUG) {
                "debug_" + type + "_" + resourceId
            } else
                "release_" + type + "_" + resourceId

        override fun equals(other: Any?): Boolean {
            return other is Subscription && other.resourceId == resourceId
        }

        override fun hashCode(): Int {
            return resourceId.hashCode()
        }
    }

    @Entity
    data class WatchHistoryEntry(
        @PrimaryKey
        var runId: String,
        var seekPos: Long = 0
    ) {
        var watchDate: Long = System.currentTimeMillis()
    }

    @Dao
    interface SubscriptionDao {
        @Query("SELECT * FROM Subscription WHERE resourceId = :resourceId")
        operator fun get(resourceId: String): Maybe<Subscription>

        @Query("SELECT * FROM Subscription WHERE type = :type ORDER BY name LIMIT 40 OFFSET :offset")
        fun listOfType(type: String, offset: Int): Single<List<Subscription>>

        @Query("SELECT * FROM Subscription WHERE type = :type AND resourceId LIKE :idPrefix || '%'")
        fun listOfTypeWithIDPrefix(type: String, idPrefix: String): Single<List<Subscription>>

        @Query("SELECT *, substr(origResourceId, 1, pos - 1) as resourceId FROM (SELECT *, instr(resourceId, '_') as pos, resourceId as origResourceId FROM Subscription WHERE type = :type) GROUP BY substr(origResourceId, 1, pos - 1) ORDER BY name LIMIT 40 OFFSET :offset")
        fun listOfTypeGrouped(type: String, offset: Int): Single<List<Subscription>>

        @Query("SELECT * FROM Subscription WHERE type = :type AND name LIKE :filter ORDER BY name LIMIT 40 OFFSET :offset")
        fun listOfTypeWithFilter(type: String, filter: String, offset: Int): Single<List<Subscription>>

        @Insert(onConflict = OnConflictStrategy.REPLACE)
        fun subscribe(vararg subscriptions: Subscription): Completable

        @Delete
        fun unsubscribe(vararg subscriptions: Subscription): Completable
    }

    @Dao
    interface WatchHistoryDao {
        @Query("SELECT * FROM WatchHistoryEntry ORDER BY watchDate DESC LIMIT 40 OFFSET :offset")
        fun getMany(offset: Int): Single<List<WatchHistoryEntry>>

        @Query("SELECT * FROM WatchHistoryEntry WHERE runId = :runId")
        operator fun get(runId: String): Maybe<WatchHistoryEntry>

        @Insert(onConflict = OnConflictStrategy.REPLACE)
        fun record(vararg historyEntries: WatchHistoryEntry): Completable

        @Delete
        fun delete(historyEntry: WatchHistoryEntry): Completable
    }

    companion object {

        private val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE TABLE IF NOT EXISTS `Subscription` (`resourceId` TEXT NOT NULL, `type` TEXT NOT NULL, PRIMARY KEY(`resourceId`))")
            }
        }

        private val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE `Subscription` ADD COLUMN `name` TEXT NOT NULL DEFAULT '';")
            }
        }

        fun make(ctx: Context): AppDatabase {
            return Room.databaseBuilder(ctx, AppDatabase::class.java, "appdb")
                    .addMigrations(
                            MIGRATION_1_2,
                            MIGRATION_2_3
                    )
                    .build()
        }
    }
}
