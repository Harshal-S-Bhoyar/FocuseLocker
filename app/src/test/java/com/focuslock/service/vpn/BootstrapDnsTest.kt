package com.focuslock.service.vpn

import android.net.VpnService
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.InetAddress
import java.lang.reflect.Method

class BootstrapDnsTest {

    private lateinit var vpnService: VpnService
    private lateinit var bootstrapDns: BootstrapDns
    private lateinit var buildDnsQueryMethod: Method
    private lateinit var parseDnsResponseMethod: Method

    @Before
    fun setup() {
        vpnService = mockk<VpnService>(relaxed = true)
        bootstrapDns = BootstrapDns(vpnService, listOf("8.8.8.8"))

        buildDnsQueryMethod = BootstrapDns::class.java.getDeclaredMethod("buildDnsQuery", String::class.java)
        buildDnsQueryMethod.isAccessible = true

        parseDnsResponseMethod = BootstrapDns::class.java.getDeclaredMethod("parseDnsResponse", ByteArray::class.java, Int::class.java)
        parseDnsResponseMethod.isAccessible = true
    }

    @Test
    fun `buildDnsQuery generates correct DNS request for google_com`() {
        val query = buildDnsQueryMethod.invoke(bootstrapDns, "google.com") as ByteArray
        
        assertEquals(0x01.toByte(), query[2])
        assertEquals(0x00.toByte(), query[3])
        assertEquals(0x00.toByte(), query[4])
        assertEquals(0x01.toByte(), query[5])
        
        var offset = 12
        assertEquals(6.toByte(), query[offset++])
        assertEquals('g'.code.toByte(), query[offset++])
        assertEquals('o'.code.toByte(), query[offset++])
        assertEquals('o'.code.toByte(), query[offset++])
        assertEquals('g'.code.toByte(), query[offset++])
        assertEquals('l'.code.toByte(), query[offset++])
        assertEquals('e'.code.toByte(), query[offset++])
        
        assertEquals(3.toByte(), query[offset++])
        assertEquals('c'.code.toByte(), query[offset++])
        assertEquals('o'.code.toByte(), query[offset++])
        assertEquals('m'.code.toByte(), query[offset++])
        
        assertEquals(0x00.toByte(), query[offset++])
        assertEquals(0x00.toByte(), query[offset++])
        assertEquals(0x01.toByte(), query[offset++])
        assertEquals(0x00.toByte(), query[offset++])
        assertEquals(0x01.toByte(), query[offset++])
    }

    @Test
    fun `parseDnsResponse correctly extracts IPv4 address`() {
        val responseHex = "abcd8180000100010000000006676f6f676c6503636f6d0000010001c00c000100010000012c00048efa002e"
        val responseBytes = responseHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

        @Suppress("UNCHECKED_CAST")
        val ips = parseDnsResponseMethod.invoke(bootstrapDns, responseBytes, responseBytes.size) as List<InetAddress>
        
        assertEquals(1, ips.size)
        assertEquals("142.250.0.46", ips[0].hostAddress)
    }
    
    @Test
    fun `parseDnsResponse handles empty answer count gracefully`() {
        val responseHex = "abcd8180000100000000000006676f6f676c6503636f6d0000010001"
        val responseBytes = responseHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

        @Suppress("UNCHECKED_CAST")
        val ips = parseDnsResponseMethod.invoke(bootstrapDns, responseBytes, responseBytes.size) as List<InetAddress>
        
        assertTrue(ips.isEmpty())
    }
}
