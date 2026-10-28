package com.yatri.location

import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * A GPS fix captured on the device and waiting to be uploaded.
 *
 * Every fix is written here first (online or offline) and removed only after the
 * server confirms it stored the point, so no-network periods don't lose the trail.
 * [capturedAt] is the provider's fix time, so the server stores the real time the
 * user was at that spot rather than the time the upload happened.
 */
@Entity(
    tableName = "pending_locations",
    indices = [Index(value = ["clientId"], unique = true), Index(value = ["userId", "capturedAt"])]
)
data class PendingLocation(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Stable id sent to the server so a retried upload is not stored twice. */
    val clientId: String,
    /** Owner of the point (from the JWT), so a later login can't upload it as someone else. */
    val userId: String,
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float,
    val batteryLevel: Int?,
    val isGpsEnabled: Boolean,
    /** Location.time — UTC epoch millis of the fix. */
    val capturedAt: Long,
)

@Dao
interface PendingLocationDao {
    @Insert
    suspend fun insert(point: PendingLocation)

    @Query("SELECT * FROM pending_locations WHERE userId = :userId ORDER BY capturedAt ASC LIMIT :limit")
    suspend fun oldestForUser(userId: String, limit: Int): List<PendingLocation>

    @Query("DELETE FROM pending_locations WHERE clientId IN (:clientIds)")
    suspend fun deleteByClientIds(clientIds: List<String>)

    @Query("SELECT COUNT(*) FROM pending_locations WHERE userId = :userId")
    suspend fun countForUser(userId: String): Int

    /** Drop points older than the retention window (the server rejects very old points anyway). */
    @Query("DELETE FROM pending_locations WHERE capturedAt < :cutoffMillis")
    suspend fun deleteOlderThan(cutoffMillis: Long)

    /** Hard cap on queue size so a very long outage can't grow the database without bound. */
    @Query(
        "DELETE FROM pending_locations WHERE id NOT IN " +
            "(SELECT id FROM pending_locations ORDER BY capturedAt DESC LIMIT :keep)"
    )
    suspend fun trimTo(keep: Int)
}

/**
 * A change of the phone's Location setting while on duty (the employee switched GPS off or
 * back on), queued like [PendingLocation] so it reaches the admin even if it happened offline.
 */
@Entity(tableName = "pending_device_events", indices = [Index(value = ["clientId"], unique = true)])
data class DeviceEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val clientId: String,
    val userId: String,
    /** [TYPE_LOCATION_OFF] or [TYPE_LOCATION_ON]. */
    val type: String,
    /** UTC epoch millis when the setting changed. */
    val occurredAt: Long,
    /** Last known position when it happened (where location was switched off). */
    val latitude: Double?,
    val longitude: Double?,
    /** For LOCATION_ON: when location had been switched off, so the admin sees the duration. */
    val offSince: Long?,
) {
    companion object {
        const val TYPE_LOCATION_OFF = "LOCATION_OFF"
        const val TYPE_LOCATION_ON = "LOCATION_ON"
    }
}

@Dao
interface DeviceEventDao {
    @Insert
    suspend fun insert(event: DeviceEvent)

    @Query("SELECT * FROM pending_device_events WHERE userId = :userId ORDER BY occurredAt ASC LIMIT :limit")
    suspend fun oldestForUser(userId: String, limit: Int): List<DeviceEvent>

    @Query("DELETE FROM pending_device_events WHERE clientId IN (:clientIds)")
    suspend fun deleteByClientIds(clientIds: List<String>)

    @Query("SELECT COUNT(*) FROM pending_device_events WHERE userId = :userId")
    suspend fun countForUser(userId: String): Int

    @Query("DELETE FROM pending_device_events WHERE occurredAt < :cutoffMillis")
    suspend fun deleteOlderThan(cutoffMillis: Long)
}

@Database(entities = [PendingLocation::class, DeviceEvent::class], version = 2, exportSchema = false)
abstract class LocationDatabase : RoomDatabase() {
    abstract fun pendingLocations(): PendingLocationDao
    abstract fun deviceEvents(): DeviceEventDao

    companion object {
        @Volatile private var instance: LocationDatabase? = null

        fun get(context: Context): LocationDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    LocationDatabase::class.java,
                    "yatri_locations.db"
                ).addMigrations(MIGRATION_1_2).build().also { instance = it }
            }

        /** v2 adds the device-event queue; queued offline points from v1 are kept. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `pending_device_events` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `clientId` TEXT NOT NULL, " +
                        "`userId` TEXT NOT NULL, `type` TEXT NOT NULL, `occurredAt` INTEGER NOT NULL, " +
                        "`latitude` REAL, `longitude` REAL, `offSince` INTEGER)"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_pending_device_events_clientId` " +
                        "ON `pending_device_events` (`clientId`)"
                )
            }
        }

        /** Tests swap in an in-memory database. */
        @VisibleForTesting
        fun setInstanceForTests(db: LocationDatabase?) {
            instance = db
        }
    }
}
