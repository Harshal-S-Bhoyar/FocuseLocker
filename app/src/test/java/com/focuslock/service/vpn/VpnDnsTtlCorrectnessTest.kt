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
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import timber.log.Timber
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class VpnDnsTtlCorrectnessTest {

    private lateinit var vpnPacketLoop: VpnPacketLoop
    private lateinit var okHttpClient: OkHttpClient
    private lateinit var pfdClient: ParcelFileDescriptor
    private lateinit var pfdServer: ParcelFileDescriptor
    private lateinit var clientSocket: java.net.Socket
    private val dohCalls = AtomicInteger(0)
    @Volatile private var answerTtl: Long = 120

    class TestLoggingTree : Timber.DebugTree() {
        val logs = CopyOnWriteArrayList<String>()
        override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
            logs.add("[$tag] $message")
        }
    }

    private lateinit var testTree: TestLoggingTree

    @Before
    fun setup() {
        testTree = TestLoggingTree()
        Timber.plant(testTree)

        val serverSocket = java.net.ServerSocket(0)
        val clientThread = kotlin.concurrent.thread {
            clientSocket = java.net.Socket("localhost", serverSocket.localPort)
        }
        val serverConn = serverSocket.accept()
        clientThread.join()
        clientSocket.soTimeout = 5000

        val sockets = ParcelFileDescriptor.createSocketPair()
        pfdClient = sockets[0]
        pfdServer = sockets[1]

        okHttpClient = mockk()
        val reqSlot = io.mockk.slot<Request>()
        val mockCall = mockk<okhttp3.Call>()
        every { okHttpClient.newCall(capture(reqSlot)) } returns mockCall
        every { mockCall.execute() } answers {
            dohCalls.incrementAndGet()
            val body = reqSlot.captured.body!!
            val buf = Buffer()
            body.writeTo(buf)
            val query = buf.readByteArray()
            Response.Builder()
                .request(reqSlot.captured)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(buildDnsAnswer(query, answerTtl).toResponseBody("application/dns-message".toMediaTypeOrNull()))
                .build()
        }

        val dnsConfig = DnsConfig("10.0.0.2", "fd00:1:fd00:1:fd00:1:fd00:1", "https://dns.nextdns.io/71421a")

        vpnPacketLoop = VpnPacketLoop(
            vpnService = mockk(relaxed = true),
            vpnInterface = pfdServer,
            dnsConfig = dnsConfig,
            okHttpClient = okHttpClient,
            isDomainBlocked = { false },
            injectedInputStream = serverConn.getInputStream(),
            injectedOutputStream = serverConn.getOutputStream()
        )
    }

    @After
    fun teardown() {
        Timber.uproot(testTree)
        if (::clientSocket.isInitialized) clientSocket.close()
        if (::pfdClient.isInitialized) pfdClient.close()
        if (::pfdServer.isInitialized) pfdServer.close()
    }

    private fun qname(vararg labels: String): ByteArray {
        var out = byteArrayOf()
        labels.forEach { l ->
            out += l.length.toByte()
            out += l.toByteArray()
        }
        return out + byteArrayOf(0)
    }

    /** Encoded question length for single-A queries, so response math stays exact. */
    private fun questionLen(vararg labels: String) = qname(*labels).size + 4

    /** Full TUN response length for our fixed-shape answers (16-byte answer RR). */
    private fun responseLen(vararg labels: String) = 28 + 12 + questionLen(*labels) + 16

    /** Offset of the answer TTL field inside the served TUN response. */
    private fun answerTtlAt(vararg labels: String) = 28 + 12 + questionLen(*labels) + 6

    /** IPv4/UDP/DNS query packet; flagsByte3 defaults to 0x00 (pass 0x10 for CD). */
    private fun queryPacket(
        domain: List<String>,
        txid: Int,
        srcPort: Int,
        qtype: Int,
        flagsByte3: Byte = 0x00
    ): ByteArray {
        val question = qname(*domain.toTypedArray()) + byteArrayOf(0, qtype.toByte(), 0, 1)
        val dns = byteArrayOf(
            (txid shr 8).toByte(), txid.toByte(), 0x01, flagsByte3,
            0, 1, 0, 0, 0, 0, 0, 0
        ) + question
        val udpLen = 8 + dns.size
        val totalLen = 20 + udpLen
        val ip = byteArrayOf(
            0x45, 0x00, (totalLen shr 8).toByte(), totalLen.toByte(),
            0x00, 0x00, 0x40, 0x00, 0x40, 0x11, 0x00, 0x00,
            0x0A, 0x00, 0x00, 0x02, 0x0A, 0x00, 0x00, 0x03
        )
        val udp = byteArrayOf(
            (srcPort shr 8).toByte(), srcPort.toByte(), 0x00, 0x35,
            (udpLen shr 8).toByte(), udpLen.toByte(), 0x00, 0x00
        )
        return ip + udp + dns
    }

    private fun buildDnsAnswer(query: ByteArray, ttl: Long): ByteArray {
        val qtype = ((query[query.size - 4].toInt() and 0xFF) shl 8) or (query[query.size - 3].toInt() and 0xFF)
        val question = query.copyOfRange(12, query.size)
        val rdata = byteArrayOf(1, 2, 3, 4)
        val answer = byteArrayOf(
            0xC0.toByte(), 0x0C,
            (qtype shr 8).toByte(), qtype.toByte(), 0, 1,
            (ttl shr 24).toByte(), (ttl shr 16).toByte(), (ttl shr 8).toByte(), ttl.toByte(),
            0, rdata.size.toByte()
        ) + rdata
        return byteArrayOf(
            query[0], query[1], 0x81.toByte(), 0x80.toByte(),
            0, 1, 0, 1, 0, 0, 0, 0
        ) + question + answer
    }

    private fun readExact(n: Int): ByteArray {
        val input = clientSocket.getInputStream()
        val out = ByteArray(n)
        var read = 0
        while (read < n) {
            val r = input.read(out, read, n - read)
            check(r > 0) { "stream closed after $read/$n bytes" }
            read += r
        }
        return out
    }

    private fun u32(b: ByteArray, at: Int) =
        ((b[at].toLong() and 0xFF) shl 24) or
            ((b[at + 1].toLong() and 0xFF) shl 16) or
            ((b[at + 2].toLong() and 0xFF) shl 8) or
            (b[at + 3].toLong() and 0xFF)

    @Test
    fun `queries differing only by CD bit do not share cache entries`() = runBlocking {
        val domain = listOf("cdtest", "com")
        val job = launch(Dispatchers.IO) { vpnPacketLoop.runLoop() }
        try {
            val out = clientSocket.getOutputStream()
            out.write(queryPacket(domain, txid = 0xAAAA, srcPort = 5101, qtype = 1, flagsByte3 = 0x00))
            out.flush()
            readExact(responseLen("cdtest", "com"))
            assertEquals(1, dohCalls.get())

            out.write(queryPacket(domain, txid = 0xBBBB, srcPort = 5102, qtype = 1, flagsByte3 = 0x10))
            out.flush()
            readExact(responseLen("cdtest", "com"))
            assertEquals("CD=1 query must not reuse the CD=0 cached answer", 2, dohCalls.get())
        } finally {
            clientSocket.close()
            pfdClient.close()
            pfdServer.close()
            job.cancelAndJoin()
        }
    }

    @Test
    fun `zero TTL answers are never cached`() = runBlocking {
        answerTtl = 0
        val domain = listOf("zerottl", "com")
        val job = launch(Dispatchers.IO) { vpnPacketLoop.runLoop() }
        try {
            val out = clientSocket.getOutputStream()
            out.write(queryPacket(domain, txid = 0xAAAA, srcPort = 5201, qtype = 1))
            out.flush()
            readExact(responseLen("zerottl", "com"))
            assertEquals(1, dohCalls.get())

            out.write(queryPacket(domain, txid = 0xBBBB, srcPort = 5202, qtype = 1))
            out.flush()
            readExact(responseLen("zerottl", "com"))
            assertEquals("zero-TTL answer must be re-fetched, not served from cache", 2, dohCalls.get())
        } finally {
            clientSocket.close()
            pfdClient.close()
            pfdServer.close()
            job.cancelAndJoin()
        }
    }

    @Test
    fun `short TTL keeps its actual duration instead of the old floor`() = runBlocking {
        answerTtl = 2
        val domain = listOf("shortttl", "com")
        val job = launch(Dispatchers.IO) { vpnPacketLoop.runLoop() }
        try {
            val out = clientSocket.getOutputStream()
            out.write(queryPacket(domain, txid = 0xAAAA, srcPort = 5301, qtype = 1))
            out.flush()
            readExact(responseLen("shortttl", "com"))

            out.write(queryPacket(domain, txid = 0xBBBB, srcPort = 5302, qtype = 1))
            out.flush()
            readExact(responseLen("shortttl", "com"))
            assertEquals("fresh short-TTL answer must come from cache", 1, dohCalls.get())

            Thread.sleep(2600)
            out.write(queryPacket(domain, txid = 0xCCCC, srcPort = 5303, qtype = 1))
            out.flush()
            readExact(responseLen("shortttl", "com"))
            assertEquals("expired short-TTL entry must be re-fetched", 2, dohCalls.get())
        } finally {
            clientSocket.close()
            pfdClient.close()
            pfdServer.close()
            job.cancelAndJoin()
        }
    }

    @Test
    fun `served answers carry remaining TTL on a cache hit`() = runBlocking {
        answerTtl = 60
        val domain = listOf("aging", "com")
        val ttlAt = answerTtlAt("aging", "com")
        val job = launch(Dispatchers.IO) { vpnPacketLoop.runLoop() }
        try {
            val out = clientSocket.getOutputStream()
            out.write(queryPacket(domain, txid = 0xAAAA, srcPort = 5401, qtype = 1))
            out.flush()
            val fresh = readExact(responseLen("aging", "com"))
            // Fresh DoH path serves the origin TTL untouched.
            assertEquals(60L, u32(fresh, ttlAt))

            Thread.sleep(2100)
            out.write(queryPacket(domain, txid = 0xBBBB, srcPort = 5402, qtype = 1))
            out.flush()
            val aged = readExact(responseLen("aging", "com"))
            assertEquals(1, dohCalls.get())
            val served = u32(aged, ttlAt)
            assertTrue("cache hit must age the TTL down, was $served", served in 55L..59L)
        } finally {
            clientSocket.close()
            pfdClient.close()
            pfdServer.close()
            job.cancelAndJoin()
        }
    }

    @Test
    fun `answer TTL rewrite caps without touching other bytes`() {
        // Minimal response: 12-byte header + 11-byte question + 16-byte answer, TTL 300.
        // qname("x", "com") = [1,'x',3,'c','o','m',0] (7) + qtype/qclass (4) = 11.
        // TTL field at 12 + 11 + 6 = 29; RDATA starts 6 bytes later at 35.
        val question = qname("x", "com") + byteArrayOf(0, 1, 0, 1)
        val answer = byteArrayOf(
            0xC0.toByte(), 0x0C, 0, 1, 0, 1,
            0, 0, 1, 44,
            0, 4
        ) + byteArrayOf(9, 9, 9, 9)
        val msg = byteArrayOf(
            0x12, 0x34, 0x81.toByte(), 0x80.toByte(),
            0, 1, 0, 1, 0, 0, 0, 0
        ) + question + answer

        val capped = DnsTtlParser.withCappedAnswerTtls(msg, 0, msg.size, 10)
        assertTrue(capped != null)
        val ttlAt = 12 + 11 + 6
        val ttl = ((capped!![ttlAt].toLong() and 0xFF) shl 24) or
            ((capped[ttlAt + 1].toLong() and 0xFF) shl 16) or
            ((capped[ttlAt + 2].toLong() and 0xFF) shl 8) or
            (capped[ttlAt + 3].toLong() and 0xFF)
        assertEquals(10L, ttl)
        // RDATA untouched (first rdata byte 6 past the TTL field).
        assertEquals(9.toByte(), capped[ttlAt + 6])
        assertNull(DnsTtlParser.withCappedAnswerTtls(byteArrayOf(1, 2, 3), 0, 3, 10))
    }
}
