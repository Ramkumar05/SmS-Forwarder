package com.autosmsforwarder

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.autosmsforwarder.data.ForwardDatabase
import com.autosmsforwarder.data.WebDeliveryEntity
import com.autosmsforwarder.data.WebDeliveryRepository
import com.autosmsforwarder.domain.IdempotencyKey
import com.autosmsforwarder.domain.IncomingMessage
import com.autosmsforwarder.domain.WebIdempotencyKey
import com.autosmsforwarder.receiver.WebRouteProcessor
import com.autosmsforwarder.settings.MatchMode
import com.autosmsforwarder.settings.RouteConfiguration
import com.autosmsforwarder.settings.SendDelay
import com.autosmsforwarder.worker.HttpWebArchiveUploader
import com.autosmsforwarder.worker.WebConnectivityChecker
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppSourceUploadTest {
    @Test fun sourceSnapshotDoesNotChangeOriginalSmsOrLocalActivityIdentity() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), ForwardDatabase::class.java).build()
        try {
            val repository = WebDeliveryRepository(database, "fixture", { 1000 })
            val processor = WebRouteProcessor(repository, WebConnectivityChecker { true })
            val config = RouteConfiguration(false, "", SendDelay.INSTANT, MatchMode.ALL, "", true, MatchMode.ALL, "", "fixture",
                " +919876543210 ", " Home phone ")
            val message = IncomingMessage("ORIGINAL-SENDER", "Full body", 1000)
            val key = processor.recordIfMatched(message, config)!!
            assertEquals(IdempotencyKey.create(message.sender, 1000, message.body), key)
            val stored = database.webDeliveryDao().find(key)!!
            assertEquals("+919876543210", stored.appSourcePhoneNumber)
            assertEquals("Home phone", stored.appSourceName)
            assertEquals("ORIGINAL-SENDER", stored.sender)
            assertNull(processor.recordIfMatched(message, config.copy(appSourceName = "Changed later")))
            assertEquals("Home phone", repository.claim(key)?.appSourceName)
        } finally { database.close() }
    }

    @Test fun actualUploaderSerializesSourceFieldsAndSourceAwareServerKey() = runBlocking {
        val row = WebDeliveryEntity("a".repeat(64), "BANK", 1000, "Full body தமிழ்", 1000, "session", "+919876543210", "Home")
        val connection = RecordingConnection()
        assertEquals(201, HttpWebArchiveUploader { connection }.upload(row, "fixture-token"))
        val json = JSONObject(connection.output.toString(Charsets.UTF_8.name()))
        assertEquals("BANK", json.getString("sender"))
        assertEquals("Full body தமிழ்", json.getString("body"))
        assertEquals("+919876543210", json.getString("appSourcePhoneNumber"))
        assertEquals("Home", json.getString("appSourceName"))
        assertEquals(WebIdempotencyKey.create(row.dedupeKey, row.appSourcePhoneNumber, row.appSourceName), json.getString("idempotencyKey"))
        assertEquals("Bearer fixture-token", connection.getRequestProperty("Authorization"))
        assertTrue(connection.closed)
    }

    @Test fun actualUploaderOmitsEachBlankOptionalFieldAndKeepsLegacyKey() = runBlocking {
        val row = WebDeliveryEntity("b".repeat(64), "BANK", 1000, "body", 1000, "session")
        val connection = RecordingConnection()
        HttpWebArchiveUploader { connection }.upload(row, "fixture-token")
        val json = JSONObject(connection.output.toString(Charsets.UTF_8.name()))
        assertFalse(json.has("appSourcePhoneNumber"))
        assertFalse(json.has("appSourceName"))
        assertEquals(row.dedupeKey, json.getString("idempotencyKey"))
        val nameOnly = RecordingConnection()
        HttpWebArchiveUploader { nameOnly }.upload(row.copy(appSourceName = "Home"), "fixture-token")
        val named = JSONObject(nameOnly.output.toString(Charsets.UTF_8.name()))
        assertEquals("Home", named.getString("appSourceName"))
        assertFalse(named.has("appSourcePhoneNumber"))
    }

    private class RecordingConnection : HttpURLConnection(URL("https://sms-archive.arj-ramkumar.workers.dev/api/messages")) {
        val output = ByteArrayOutputStream()
        var closed = false
        override fun connect() = Unit
        override fun disconnect() { closed = true }
        override fun usingProxy() = false
        override fun getOutputStream() = output
        override fun getResponseCode() = 201
    }
}
