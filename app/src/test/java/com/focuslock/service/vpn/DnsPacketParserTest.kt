package com.focuslock.service.vpn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.nio.ByteBuffer

class DnsPacketParserTest {

    @Test
    fun testExtractDomainName_ValidPacket() {
        // Hex dump of a simple IPv4 UDP DNS query for "example.com"
        val hexString = "4500003b000040004011b74a7f0000017f000001dfa200350027fe56" +
                        "123401000001000000000000076578616d706c6503636f6d0000010001"
        
        val bytes = hexStringToByteArray(hexString)
        val buffer = ByteBuffer.wrap(bytes)
        
        val domain = DnsPacketParser.extractDomainName(buffer, bytes.size)
        assertEquals("example.com", domain)
    }

    @Test
    fun testExtractDomainName_InvalidVersion() {
        // IPv6 packet instead of IPv4
        val bytes = ByteArray(60)
        bytes[0] = 0x60.toByte() // IPv6
        
        val buffer = ByteBuffer.wrap(bytes)
        val domain = DnsPacketParser.extractDomainName(buffer, bytes.size)
        assertNull(domain)
    }

    @Test
    fun testExtractDomainName_NotUDP() {
        val bytes = ByteArray(60)
        bytes[0] = 0x45.toByte() // IPv4, IHL=5
        bytes[9] = 0x06.toByte() // Protocol = TCP
        
        val buffer = ByteBuffer.wrap(bytes)
        val domain = DnsPacketParser.extractDomainName(buffer, bytes.size)
        assertNull(domain)
    }

    @Test
    fun testExtractDomainName_NotDNS() {
        val bytes = ByteArray(60)
        bytes[0] = 0x45.toByte() // IPv4, IHL=5
        bytes[9] = 0x11.toByte() // Protocol = UDP
        bytes[20] = 0x00.toByte()
        bytes[21] = 0x00.toByte() // Source port 0
        bytes[22] = 0x00.toByte()
        bytes[23] = 0x50.toByte() // Dest port 80 (HTTP, not DNS)
        
        val buffer = ByteBuffer.wrap(bytes)
        val domain = DnsPacketParser.extractDomainName(buffer, bytes.size)
        assertNull(domain)
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
}
