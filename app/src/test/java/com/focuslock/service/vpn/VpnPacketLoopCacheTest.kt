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
class VpnPacketLoopCacheTest {

    private lateinit var vpnPacketLoop: VpnPacketLoop
    private lateinit var okHttpClient: OkHttpClient
    private lateinit var pfdClient: ParcelFileDescriptor
    private lateinit var pfdServer: ParcelFileDescriptor
    private lateinit var clientSocket: java.net.Socket
    private val dohCalls = AtomicInteger(0)

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
                .body(buildDnsAnswer(query).toResponseBody("application/dns-message".toMediaTypeOrNull()))
                .build()
        }

        val dnsConfig = DnsConfig("10.0.0.2", "fd00:1:fd00:1:fd00:1:fd00:1", "https://dns.nextdns.io/71421a")

        vpnPacketLoop = VpnPacketLoop(
            vpnService = mockk(relaxed = true),
            vpnInterface = pfdServer,
            dnsConfig = dnsConfig,
            okHttpClient = okHttpClient,
            isDomainBlocked = { domain -> domain.contains("blocked.com") },
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

    /** IPv4/UDP/DNS query packet. Returns bytes; DNS query starts at offset 28. */
    private fun queryPacket(domain: List<String>, txid: Int, srcPort: Int, qtype: Int): ByteArray {
        val question = qname(*domain.toTypedArray()) + byteArrayOf(0, qtype.toByte(), 0, 1)
        val dns = byteArrayOf(
            (txid shr 8).toByte(), txid.toByte(), 0x01, 0x00,
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

    /** DoH answer echoing the query TXID with one answer, TTL 120. */
    private fun buildDnsAnswer(query: ByteArray): ByteArray {
        val qtype = ((query[query.size - 4].toInt() and 0xFF) shl 8) or (query[query.size - 3].toInt() and 0xFF)
        val question = query.copyOfRange(12, query.size)
        val rdata = if (qtype == 28) ByteArray(16) { 0x20 } else byteArrayOf(1, 2, 3, 4)
        val answer = byteArrayOf(
            0xC0.toByte(), 0x0C,
            (qtype shr 8).toByte(), qtype.toByte(), 0, 1,
            0, 0, 0, 120,
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

    private fun u16(b: ByteArray, at: Int) = ((b[at].toInt() and 0xFF) shl 8) or (b[at + 1].toInt() and 0xFF)

    @Test
    fun `second identical lookup served from cache with current txid and ports`() = runBlocking {
        val job = launch(Dispatchers.IO) { vpnPacketLoop.runLoop() }
        try {
            val out = clientSocket.getOutputStream()
            out.write(queryPacket(listOf("cached", "com"), txid = 0xAAAA, srcPort = 5001, qtype = 1))
            out.flush()
            // A answer body = 12 + 16 + 12 + 4 = 44; response = 28 + 44 = 72
            val r1 = readExact(72)
            assertEquals(0xAAAA, u16(r1, 28))
            assertEquals(5001, u16(r1, 22))
            assertEquals("first lookup must hit DoH", 1, dohCalls.get())

            out.write(queryPacket(listOf("cached", "com"), txid = 0xBBBB, srcPort = 5002, qtype = 1))
            out.flush()
            val r2 = readExact(72)
            assertEquals("cached response must carry current TXID", 0xBBBB, u16(r2, 28))
            assertEquals("cached response must target current port", 5002, u16(r2, 22))
            assertEquals("second lookup must not hit DoH", 1, dohCalls.get())
            val deadline = System.currentTimeMillis() + 3000
            while (System.currentTimeMillis() < deadline &&
                testTree.logs.none { it.contains("DNS CACHE HIT") }) {
                delay(50)
            }
            assertTrue("cache hit must be logged. Logs: ${testTree.logs}", testTree.logs.any { it.contains("DNS CACHE HIT") })
        } finally {
            clientSocket.close()
            pfdClient.close()
            pfdServer.close()
            job.cancelAndJoin()
        }
    }

    @Test
    fun `blocked domain bypasses cache and network`() = runBlocking {
        val job = launch(Dispatchers.IO) { vpnPacketLoop.runLoop() }
        try {
            val out = clientSocket.getOutputStream()
            val q = queryPacket(listOf("blocked", "com"), txid = 0x1111, srcPort = 5003, qtype = 1)
            out.write(q)
            out.flush()
            val r = readExact(q.size)
            assertEquals("NXDOMAIN rcode expected", 3, r[31].toInt() and 0x0F)
            assertEquals("blocked path must not hit DoH", 0, dohCalls.get())
        } finally {
            clientSocket.close()
            pfdClient.close()
            pfdServer.close()
            job.cancelAndJoin()
        }
    }

    @Test
    fun `qtype isolated between A and AAAA`() = runBlocking {
        val job = launch(Dispatchers.IO) { vpnPacketLoop.runLoop() }
        try {
            val out = clientSocket.getOutputStream()
            out.write(queryPacket(listOf("cached", "com"), txid = 0x0001, srcPort = 5004, qtype = 1))
            out.flush()
            readExact(72)
            assertEquals(1, dohCalls.get())

            out.write(queryPacket(listOf("cached", "com"), txid = 0x0002, srcPort = 5005, qtype = 28))
            out.flush()
            // AAAA body = 12 + 16 + 12 + 16 = 56; response = 28 + 56 = 84
            val r = readExact(84)
            assertEquals(0x0002, u16(r, 28))
            assertEquals("AAAA must miss the A entry", 2, dohCalls.get())
        } finally {
            clientSocket.close()
            pfdClient.close()
            pfdServer.close()
            job.cancelAndJoin()
        }
    }

    @Test
    fun `releaseBuffer only recycles pool-sized buffers`() {
        // Regression: every array was offered back to the pool, including the oversized array
        // buildTunDnsResponse allocates for a response larger than the pool, which defeats the pool
        // by handing out blocks bigger than any caller expects. Reflection matches the convention
        // already used by PackageFilterTest; the oversized path is otherwise unreachable here
        // because the DoH mock cannot produce a response larger than the pool.
        val poolField = VpnPacketLoop::class.java.getDeclaredField("bufferPool")
        poolField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val pool = poolField.get(vpnPacketLoop) as java.util.concurrent.ArrayBlockingQueue<ByteArray>
        pool.clear()

        val release = VpnPacketLoop::class.java.getDeclaredMethod("releaseBuffer", ByteArray::class.java)
        release.isAccessible = true

        release.invoke(vpnPacketLoop, ByteArray(VpnPacketLoop.POOL_BUFFER_SIZE * 2))
        assertEquals("oversized buffers must not enter the pool", 0, pool.size)

        release.invoke(vpnPacketLoop, ByteArray(VpnPacketLoop.POOL_BUFFER_SIZE))
        assertEquals("pool-sized buffers are recycled", 1, pool.size)
    }

    @Test
    fun `query differing in question bytes is not served from cache`() = runBlocking {
        val job = launch(Dispatchers.IO) { vpnPacketLoop.runLoop() }
        try {
            val out = clientSocket.getOutputStream()
            out.write(queryPacket(listOf("cached", "com"), txid = 0x2001, srcPort = 5101, qtype = 1))
            out.flush()
            readExact(72)
            assertEquals(1, dohCalls.get())

            // Same domain and qtype, different question bytes (0x20-style casing). The entry is
            // keyed on the query's post-header bytes, so this must not be answered from it — the
            // cached reply would not match what was asked.
            out.write(queryPacket(listOf("CACHED", "com"), txid = 0x2002, srcPort = 5102, qtype = 1))
            out.flush()
            val r = readExact(72)
            assertEquals(0x2002, u16(r, 28))
            assertEquals("differing question bytes must miss the cache", 2, dohCalls.get())
        } finally {
            clientSocket.close()
            pfdClient.close()
            pfdServer.close()
            job.cancelAndJoin()
        }
    }
}
