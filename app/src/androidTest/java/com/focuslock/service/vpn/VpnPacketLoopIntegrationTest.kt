package com.focuslock.service.vpn

import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import timber.log.Timber
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.concurrent.CopyOnWriteArrayList

@RunWith(AndroidJUnit4::class)
class VpnPacketLoopIntegrationTest {

    private lateinit var vpnPacketLoop: VpnPacketLoop
    private lateinit var okHttpClient: OkHttpClient
    private lateinit var pfdClient: ParcelFileDescriptor
    private lateinit var pfdServer: ParcelFileDescriptor
    private lateinit var testTree: TestLoggingTree

    class TestLoggingTree : Timber.DebugTree() {
        val logs = CopyOnWriteArrayList<String>()
        override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
            logs.add("[$tag] $message")
        }
    }

    @Before
    fun setup() {
        testTree = TestLoggingTree()
        Timber.plant(testTree)

        val sockets = ParcelFileDescriptor.createSocketPair()
        pfdClient = sockets[0] // Client reads/writes here (to simulate device)
        pfdServer = sockets[1] // Server (VpnPacketLoop) reads/writes here

        okHttpClient = mockk()
        val mockBuilder = mockk<OkHttpClient.Builder>(relaxed = true)
        every { okHttpClient.newBuilder() } returns mockBuilder
        every { mockBuilder.build() } returns okHttpClient

        val dnsConfig = DnsConfig("10.0.0.2", "fd00:1:fd00:1:fd00:1:fd00:1", "https://dns.nextdns.io/71421a")

        vpnPacketLoop = VpnPacketLoop(
            vpnService = mockk(relaxed = true),
            vpnInterface = pfdServer,
            dnsConfig = dnsConfig,
            okHttpClient = okHttpClient,
            isDomainBlocked = { domain ->
                domain.contains("blocked.com")
            }
        )
    }

    @After
    fun teardown() {
        Timber.uproot(testTree)
        pfdClient.close()
        pfdServer.close()
    }

    private fun hexStringToByteArray(s: String): ByteArray {
        val len = s.length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            data[i / 2] = ((Character.digit(s[i], 16) shl 4) +
                    Character.digit(s[i + 1], 16)).toByte()
            i += 2
        }
        return data
    }

    @Test
    fun testBlockedDomainLoggingAndResolution() = runBlocking {
        // Hex dump of an IPv4 UDP DNS query for "blocked.com"
        val hexString = "4500003b000040004011b74a7f0000017f000001dfa200350027fe56" +
                "12340100000100000000000007626c6f636b656403636f6d0000010001"
        val packetBytes = hexStringToByteArray(hexString)

        val job = launch(Dispatchers.IO) {
            vpnPacketLoop.runLoop()
        }

        val outputStream = FileOutputStream(pfdClient.fileDescriptor)
        outputStream.write(packetBytes)
        outputStream.flush()

        delay(500) // Wait for processing

        pfdClient.close()
        pfdServer.close()
        job.cancelAndJoin()

        // Verify Logging
        val hasBlockedLog = testTree.logs.any { it.contains("DNS BLOCKED (local): blocked.com") }
        assertTrue("Expected blocked domain to be logged", hasBlockedLog)
    }

    @Test
    fun testAllowedDomainLoggingAndForwarding() = runBlocking {
        // Hex dump of an IPv4 UDP DNS query for "allowed.com"
        val hexString = "4500003b000040004011b74a7f0000017f000001dfa200350027fe56" +
                "12340100000100000000000007616c6c6f77656403636f6d0000010001"
        val packetBytes = hexStringToByteArray(hexString)

        val mockCall = mockk<Call>()
        val mockResponse = Response.Builder()
            .request(Request.Builder().url("https://dns.nextdns.io/71421a").build())
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body("dummy-dns-response".toResponseBody("application/dns-message".toMediaTypeOrNull()))
            .build()

        every { okHttpClient.newCall(any()) } returns mockCall
        every { mockCall.execute() } returns mockResponse

        val job = launch(Dispatchers.IO) {
            vpnPacketLoop.runLoop()
        }

        val outputStream = FileOutputStream(pfdClient.fileDescriptor)
        outputStream.write(packetBytes)
        outputStream.flush()

        delay(500) // Wait for processing

        pfdClient.close()
        pfdServer.close()
        job.cancelAndJoin()

        // Verify Logging
        val hasAllowedLog = testTree.logs.any { it.contains("DNS query for allowed.com resolved via DoH") }
        assertTrue("Expected allowed domain to be logged as resolved", hasAllowedLog)
    }
}
