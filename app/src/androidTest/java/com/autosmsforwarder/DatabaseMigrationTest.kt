package com.autosmsforwarder

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.autosmsforwarder.data.ForwardDatabase
import com.autosmsforwarder.domain.ForwardStatus
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseMigrationTest {
    @Test fun upgradingV1PreservesForwardingDataAndCreatesUploadOutbox() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration-test.db"
        context.deleteDatabase(name)
        try {
            val old = context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null)
            try {
                old.execSQL("CREATE TABLE IF NOT EXISTS `forward_log` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `dedupeKey` TEXT NOT NULL, `receivedAt` INTEGER NOT NULL, `sender` TEXT NOT NULL, `snippet` TEXT NOT NULL, `status` TEXT NOT NULL, `attempts` INTEGER NOT NULL, `lastUpdatedAt` INTEGER NOT NULL)")
                old.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_forward_log_dedupeKey` ON `forward_log` (`dedupeKey`)")
                old.execSQL("CREATE INDEX IF NOT EXISTS `index_forward_log_receivedAt` ON `forward_log` (`receivedAt`)")
                old.execSQL("CREATE TABLE IF NOT EXISTS `pending_forward` (`dedupeKey` TEXT NOT NULL, `sender` TEXT NOT NULL, `smsTimestamp` INTEGER NOT NULL, `body` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`dedupeKey`))")
                old.execSQL("INSERT INTO forward_log (dedupeKey, receivedAt, sender, snippet, status, attempts, lastUpdatedAt) VALUES ('existing', 1000, '+911234567890', 'private', 'PENDING', 0, 1000)")
                old.execSQL("INSERT INTO pending_forward (dedupeKey, sender, smsTimestamp, body, createdAt) VALUES ('existing', '+911234567890', 1000, 'private body', 1000)")
                old.version = 1
            } finally {
                old.close()
            }
            val upgraded = Room.databaseBuilder(context, ForwardDatabase::class.java, name)
                .addMigrations(ForwardDatabase.MIGRATION_1_2)
                .build()
            try {
                assertEquals(ForwardStatus.PENDING, upgraded.logDao().find("existing")?.status)
                assertEquals("private body", upgraded.pendingDao().find("existing")?.body)
                assertNull(upgraded.uploadDao().find("existing"))
            } finally {
                upgraded.close()
            }
        } finally {
            context.deleteDatabase(name)
        }
    }
}
