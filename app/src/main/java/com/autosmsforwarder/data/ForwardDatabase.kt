package com.autosmsforwarder.data

import android.content.Context
import androidx.room.Dao
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

@Entity(tableName = "upload_outbox")
data class UploadOutboxEntity(
    @PrimaryKey val dedupeKey: String,
    val sender: String,
    val smsTimestamp: Long,
    val body: String,
    val createdAt: Long
)

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
interface UploadOutboxDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(row: UploadOutboxEntity): Long

    @Query("SELECT * FROM upload_outbox WHERE dedupeKey = :key LIMIT 1")
    suspend fun find(key: String): UploadOutboxEntity?

    @Query("SELECT dedupeKey FROM upload_outbox")
    suspend fun allKeys(): List<String>

    @Query("SELECT COUNT(*) FROM upload_outbox")
    fun pendingCount(): Flow<Int>

    @Query("DELETE FROM upload_outbox WHERE dedupeKey = :key")
    suspend fun delete(key: String)

    @Query("DELETE FROM upload_outbox")
    suspend fun deleteAll()
}

class ForwardStatusConverter {
    @TypeConverter fun toDatabase(status: ForwardStatus): String = status.name
    @TypeConverter fun fromDatabase(value: String): ForwardStatus = ForwardStatus.valueOf(value)
}

@Database(entities = [ForwardLogEntity::class, PendingForwardEntity::class, UploadOutboxEntity::class], version = 2, exportSchema = false)
@TypeConverters(ForwardStatusConverter::class)
abstract class ForwardDatabase : RoomDatabase() {
    abstract fun logDao(): ForwardLogDao
    abstract fun pendingDao(): PendingForwardDao
    abstract fun uploadDao(): UploadOutboxDao

    companion object {
        @Volatile private var instance: ForwardDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `upload_outbox` (`dedupeKey` TEXT NOT NULL, `sender` TEXT NOT NULL, `smsTimestamp` INTEGER NOT NULL, `body` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`dedupeKey`))")
            }
        }

        fun get(context: Context): ForwardDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                ForwardDatabase::class.java,
                "forwarding.db"
            ).addMigrations(MIGRATION_1_2).build().also { instance = it }
        }
    }
}
