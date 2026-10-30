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
    @Test fun upgradingV12PreservesExistingRowsAndAddsWebStatusHistory() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration-v12-test.db"
        context.deleteDatabase(name)
        try {
            val old = context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null)
            try {
                old.execSQL("CREATE TABLE `forward_log` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `dedupeKey` TEXT NOT NULL, `receivedAt` INTEGER NOT NULL, `sender` TEXT NOT NULL, `snippet` TEXT NOT NULL, `status` TEXT NOT NULL, `attempts` INTEGER NOT NULL, `lastUpdatedAt` INTEGER NOT NULL)")
                old.execSQL("CREATE UNIQUE INDEX `index_forward_log_dedupeKey` ON `forward_log` (`dedupeKey`)")
                old.execSQL("CREATE INDEX `index_forward_log_receivedAt` ON `forward_log` (`receivedAt`)")
                old.execSQL("CREATE TABLE `pending_forward` (`dedupeKey` TEXT NOT NULL, `sender` TEXT NOT NULL, `smsTimestamp` INTEGER NOT NULL, `body` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`dedupeKey`))")
                old.execSQL("CREATE TABLE `web_delivery` (`dedupeKey` TEXT NOT NULL, `sender` TEXT NOT NULL, `smsTimestamp` INTEGER NOT NULL, `body` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `sessionId` TEXT NOT NULL, PRIMARY KEY(`dedupeKey`))")
                old.execSQL("CREATE TABLE `legacy_archive_work` (`dedupeKey` TEXT NOT NULL, PRIMARY KEY(`dedupeKey`))")
                old.execSQL("INSERT INTO forward_log (dedupeKey, receivedAt, sender, snippet, status, attempts, lastUpdatedAt) VALUES ('existing', 1000, 'sender', 'snippet', 'PENDING', 0, 1000)")
                old.execSQL("INSERT INTO pending_forward (dedupeKey, sender, smsTimestamp, body, createdAt) VALUES ('existing', 'sender', 1000, 'full body', 1000)")
                old.execSQL("INSERT INTO web_delivery(dedupeKey,sender,smsTimestamp,body,createdAt,sessionId) VALUES('pending-web','sender',1000,'web body',1000,'old-session')")
                old.version = 3
            } finally {
                old.close()
            }
            val upgraded = Room.databaseBuilder(context, ForwardDatabase::class.java, name)
                .addMigrations(ForwardDatabase.MIGRATION_3_4, ForwardDatabase.MIGRATION_4_5).build()
            try {
                assertEquals(ForwardStatus.PENDING, upgraded.logDao().find("existing")?.status)
                assertEquals("full body", upgraded.pendingDao().find("existing")?.body)
                assertEquals(listOf("pending-web"), upgraded.webDeliveryDao().allKeys())
                assertEquals("", upgraded.webDeliveryDao().find("pending-web")?.appSourcePhoneNumber)
                assertEquals("", upgraded.webDeliveryDao().find("pending-web")?.appSourceName)
                assertNull(upgraded.webLogDao().find("existing"))
            } finally {
                upgraded.close()
            }
        } finally {
            context.deleteDatabase(name)
        }
    }

    @Test fun upgradingV1PreservesForwardingDataWithoutAnUploadQueue() = runBlocking {
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
                .addMigrations(ForwardDatabase.MIGRATION_1_2, ForwardDatabase.MIGRATION_2_3, ForwardDatabase.MIGRATION_3_4, ForwardDatabase.MIGRATION_4_5)
                .build()
            try {
                assertEquals(ForwardStatus.PENDING, upgraded.logDao().find("existing")?.status)
                assertEquals("private body", upgraded.pendingDao().find("existing")?.body)
                assertEquals(emptyList<String>(), upgraded.webDeliveryDao().allKeys())
            } finally {
                upgraded.close()
            }
        } finally {
            context.deleteDatabase(name)
        }
    }

    @Test fun upgradingV11DropsQueuedBodyAndRetainsKeyForWorkCancellation() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration-v11-test.db"
        context.deleteDatabase(name)
        try {
            val old = context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null)
            try {
                old.execSQL("CREATE TABLE `forward_log` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `dedupeKey` TEXT NOT NULL, `receivedAt` INTEGER NOT NULL, `sender` TEXT NOT NULL, `snippet` TEXT NOT NULL, `status` TEXT NOT NULL, `attempts` INTEGER NOT NULL, `lastUpdatedAt` INTEGER NOT NULL)")
                old.execSQL("CREATE UNIQUE INDEX `index_forward_log_dedupeKey` ON `forward_log` (`dedupeKey`)")
                old.execSQL("CREATE INDEX `index_forward_log_receivedAt` ON `forward_log` (`receivedAt`)")
                old.execSQL("CREATE TABLE `pending_forward` (`dedupeKey` TEXT NOT NULL, `sender` TEXT NOT NULL, `smsTimestamp` INTEGER NOT NULL, `body` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`dedupeKey`))")
                old.execSQL("CREATE TABLE `upload_outbox` (`dedupeKey` TEXT NOT NULL, `sender` TEXT NOT NULL, `smsTimestamp` INTEGER NOT NULL, `body` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`dedupeKey`))")
                old.execSQL("INSERT INTO forward_log (dedupeKey, receivedAt, sender, snippet, status, attempts, lastUpdatedAt) VALUES ('pending', 1000, 'sender', 'snippet', 'PENDING', 0, 1000)")
                old.execSQL("INSERT INTO pending_forward (dedupeKey, sender, smsTimestamp, body, createdAt) VALUES ('pending', 'sender', 1000, 'forward body', 1000)")
                old.execSQL("INSERT INTO upload_outbox (dedupeKey, sender, smsTimestamp, body, createdAt) VALUES ('old-upload', 'sender', 1000, 'old upload body', 1000)")
                old.version = 2
            } finally {
                old.close()
            }
            val upgraded = Room.databaseBuilder(context, ForwardDatabase::class.java, name)
                .addMigrations(ForwardDatabase.MIGRATION_2_3, ForwardDatabase.MIGRATION_3_4, ForwardDatabase.MIGRATION_4_5).build()
            try {
                assertEquals(ForwardStatus.PENDING, upgraded.logDao().find("pending")?.status)
                assertEquals("forward body", upgraded.pendingDao().find("pending")?.body)
                assertEquals(listOf("old-upload"), upgraded.legacyArchiveWorkDao().allKeys())
                assertEquals(emptyList<String>(), upgraded.webDeliveryDao().allKeys())
                assertEquals(listOf("old-upload"),
                    com.autosmsforwarder.data.WebDeliveryRepository(upgraded).cleanupAtStartup().legacy)
                assertEquals(emptyList<String>(), upgraded.legacyArchiveWorkDao().allKeys())
            } finally {
                upgraded.close()
            }
        } finally {
            context.deleteDatabase(name)
        }
    }
}
