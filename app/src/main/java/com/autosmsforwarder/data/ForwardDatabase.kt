package com.autosmsforwarder.data

import android.content.Context
import androidx.room.Dao
import androidx.room.ColumnInfo
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.autosmsforwarder.domain.ForwardStatus
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "forward_log", indices = [Index(value = ["dedupeKey"], unique = true), Index("receivedAt")])
data class ForwardLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dedupeKey: String,
    val receivedAt: Long,
    val sender: String,
    val snippet: String,
    val status: ForwardStatus,
    val attempts: Int,
    val lastUpdatedAt: Long
)

@Entity(tableName = "pending_forward")
data class PendingForwardEntity(
    @PrimaryKey val dedupeKey: String,
    val sender: String,
    val smsTimestamp: Long,
    val body: String,
    val createdAt: Long
)

@Entity(tableName = "web_delivery")
data class WebDeliveryEntity(
    @PrimaryKey val dedupeKey: String,
    val sender: String,
    val smsTimestamp: Long,
    val body: String,
    val createdAt: Long,
    val sessionId: String,
    @ColumnInfo(defaultValue = "''") val appSourcePhoneNumber: String = "",
    @ColumnInfo(defaultValue = "''") val appSourceName: String = ""
)

enum class WebStatus { QUEUED, UPLOADING, COMPLETED, FAILED, SKIPPED, UNCONFIRMED }

@Entity(tableName = "web_log", indices = [Index("receivedAt")])
data class WebLogEntity(
    @PrimaryKey val dedupeKey: String,
    val receivedAt: Long,
    val sender: String,
    val snippet: String,
    val status: WebStatus,
    val lastUpdatedAt: Long
)

@Entity(tableName = "legacy_archive_work")
data class LegacyArchiveWorkEntity(@PrimaryKey val dedupeKey: String)

@Dao
interface ForwardLogDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(row: ForwardLogEntity): Long

    @Query("SELECT * FROM forward_log WHERE dedupeKey = :key LIMIT 1")
    suspend fun find(key: String): ForwardLogEntity?

    @Query("UPDATE forward_log SET status = :status, attempts = :attempts, lastUpdatedAt = :updatedAt WHERE dedupeKey = :key")
    suspend fun update(key: String, status: ForwardStatus, attempts: Int, updatedAt: Long)

    @Query("SELECT * FROM forward_log WHERE receivedAt >= :cutoff ORDER BY receivedAt DESC, id DESC")
    fun recent(cutoff: Long): Flow<List<ForwardLogEntity>>

    @Query("SELECT COUNT(*) FROM forward_log")
    suspend fun count(): Int

    @Query("SELECT dedupeKey FROM forward_log WHERE status IN ('PENDING', 'SENDING')")
    suspend fun allUnsentKeys(): List<String>

    @Query("DELETE FROM forward_log")
    suspend fun deleteAll()

    @Query("DELETE FROM forward_log WHERE receivedAt < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long)

    @Query("UPDATE forward_log SET status = 'FAILED', lastUpdatedAt = :updatedAt WHERE dedupeKey IN (SELECT dedupeKey FROM pending_forward WHERE createdAt < :cutoff) AND status IN ('PENDING', 'SENDING')")
    suspend fun failExpiredPending(cutoff: Long, updatedAt: Long)
}

@Dao
interface PendingForwardDao {
    @Insert
    suspend fun insert(row: PendingForwardEntity)

    @Query("SELECT * FROM pending_forward WHERE dedupeKey = :key LIMIT 1")
    suspend fun find(key: String): PendingForwardEntity?

    @Query("DELETE FROM pending_forward WHERE dedupeKey = :key")
    suspend fun delete(key: String)

    @Query("SELECT COUNT(*) FROM pending_forward")
    suspend fun count(): Int

    @Query("SELECT dedupeKey FROM pending_forward")
    suspend fun allKeys(): List<String>

    @Query("DELETE FROM pending_forward")
    suspend fun deleteAll()

    @Query("DELETE FROM pending_forward WHERE createdAt < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long)
}

@Dao
interface WebDeliveryDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(row: WebDeliveryEntity): Long

    @Query("SELECT * FROM web_delivery WHERE dedupeKey = :key LIMIT 1")
    suspend fun find(key: String): WebDeliveryEntity?

    @Query("SELECT dedupeKey FROM web_delivery")
    suspend fun allKeys(): List<String>

    @Query("SELECT dedupeKey FROM web_delivery WHERE sessionId != :sessionId")
    suspend fun otherSessionKeys(sessionId: String): List<String>

    @Query("DELETE FROM web_delivery WHERE dedupeKey = :key")
    suspend fun delete(key: String)

    @Query("DELETE FROM web_delivery")
    suspend fun deleteAll()

    @Query("DELETE FROM web_delivery WHERE sessionId != :sessionId")
    suspend fun deleteOtherSessions(sessionId: String)
}

@Dao
interface WebLogDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(row: WebLogEntity): Long

    @Query("SELECT * FROM web_log WHERE dedupeKey = :key LIMIT 1")
    suspend fun find(key: String): WebLogEntity?

    @Query("SELECT * FROM web_log WHERE receivedAt >= :cutoff ORDER BY receivedAt DESC, dedupeKey DESC")
    fun recent(cutoff: Long): Flow<List<WebLogEntity>>

    @Query("UPDATE web_log SET status = :status, lastUpdatedAt = :updatedAt WHERE dedupeKey = :key")
    suspend fun update(key: String, status: WebStatus, updatedAt: Long)

    @Query("UPDATE web_log SET status = :status, lastUpdatedAt = :updatedAt WHERE status = :previous")
    suspend fun updateAll(previous: WebStatus, status: WebStatus, updatedAt: Long)

    @Query("DELETE FROM web_log WHERE receivedAt < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long)

    @Query("DELETE FROM web_log")
    suspend fun deleteAll()
}

@Dao
interface LegacyArchiveWorkDao {
    @Query("SELECT dedupeKey FROM legacy_archive_work")
    suspend fun allKeys(): List<String>

    @Query("DELETE FROM legacy_archive_work")
    suspend fun deleteAll()
}

class ForwardStatusConverter {
    @TypeConverter fun toDatabase(status: ForwardStatus): String = status.name
    @TypeConverter fun fromDatabase(value: String): ForwardStatus = ForwardStatus.valueOf(value)
}

class WebStatusConverter {
    @TypeConverter fun toDatabase(status: WebStatus): String = status.name
    @TypeConverter fun fromDatabase(value: String): WebStatus = WebStatus.valueOf(value)
}

@Database(entities = [ForwardLogEntity::class, PendingForwardEntity::class, WebDeliveryEntity::class, LegacyArchiveWorkEntity::class, WebLogEntity::class], version = 5, exportSchema = false)
@TypeConverters(ForwardStatusConverter::class, WebStatusConverter::class)
abstract class ForwardDatabase : RoomDatabase() {
    abstract fun logDao(): ForwardLogDao
    abstract fun pendingDao(): PendingForwardDao
    abstract fun webDeliveryDao(): WebDeliveryDao
    abstract fun webLogDao(): WebLogDao
    abstract fun legacyArchiveWorkDao(): LegacyArchiveWorkDao

    companion object {
        @Volatile private var instance: ForwardDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `upload_outbox` (`dedupeKey` TEXT NOT NULL, `sender` TEXT NOT NULL, `smsTimestamp` INTEGER NOT NULL, `body` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`dedupeKey`))")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `legacy_archive_work` (`dedupeKey` TEXT NOT NULL, PRIMARY KEY(`dedupeKey`))")
                db.execSQL("INSERT OR IGNORE INTO `legacy_archive_work` (`dedupeKey`) SELECT `dedupeKey` FROM `upload_outbox`")
                db.execSQL("DROP TABLE `upload_outbox`")
                db.execSQL("CREATE TABLE IF NOT EXISTS `web_delivery` (`dedupeKey` TEXT NOT NULL, `sender` TEXT NOT NULL, `smsTimestamp` INTEGER NOT NULL, `body` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `sessionId` TEXT NOT NULL, PRIMARY KEY(`dedupeKey`))")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `web_log` (`dedupeKey` TEXT NOT NULL, `receivedAt` INTEGER NOT NULL, `sender` TEXT NOT NULL, `snippet` TEXT NOT NULL, `status` TEXT NOT NULL, `lastUpdatedAt` INTEGER NOT NULL, PRIMARY KEY(`dedupeKey`))")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_web_log_receivedAt` ON `web_log` (`receivedAt`)")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `web_delivery` ADD COLUMN `appSourcePhoneNumber` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `web_delivery` ADD COLUMN `appSourceName` TEXT NOT NULL DEFAULT ''")
            }
        }

        fun get(context: Context): ForwardDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                ForwardDatabase::class.java,
                "forwarding.db"
            ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5).build().also { instance = it }
        }
    }
}
