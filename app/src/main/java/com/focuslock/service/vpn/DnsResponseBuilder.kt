package com.focuslock.service.vpn

import java.nio.ByteBuffer

object DnsResponseBuilder {

    /**
     * Builds an NXDOMAIN (domain does not exist) response based on the incoming query packet.
     * This effectively blocks the domain resolution.
     */
    fun buildNxDomainResponse(packetBuffer: ByteBuffer, length: Int): ByteArray {
        val response = ByteArray(length)
        packetBuffer.position(0)
        packetBuffer.get(response, 0, length)
        
        val ipVersionAndIhl = response[0].toInt()
        val ihl = ipVersionAndIhl and 0x0F
        val ipHeaderLength = ihl * 4
        
        // 1. Swap Source and Destination IPs
        for (i in 0..3) {
            val temp = response[12 + i]
            response[12 + i] = response[16 + i]
            response[16 + i] = temp
        }
        
        // 2. Clear IP Checksum (to recalculate later if needed, though for local TUN sometimes ignored)
        response[10] = 0
        response[11] = 0
        
        val udpOffset = ipHeaderLength
        
        // 3. Swap Source and Destination Ports
        for (i in 0..1) {
            val temp = response[udpOffset + i]
            response[udpOffset + i] = response[udpOffset + 2 + i]
            response[udpOffset + 2 + i] = temp
        }
        
        // 4. Clear UDP Checksum (Setting to 0 means no checksum in IPv4)
        response[udpOffset + 6] = 0
        response[udpOffset + 7] = 0
        
        val dnsOffset = udpOffset + 8
        
        // 5. Set DNS Flags for NXDOMAIN
        // Original flags are at dnsOffset + 2 and + 3
        // Response (QR = 1), Authoritative Answer (AA = 1) -> byte 2 = 0x84 (if no recursion) or 0x81 (recursion desired)
        response[dnsOffset + 2] = (response[dnsOffset + 2].toInt() or 0x80).toByte()
        
        // Reply Code (RCODE) = 3 (NXDOMAIN)
        response[dnsOffset + 3] = (response[dnsOffset + 3].toInt() and 0xF0 or 0x03).toByte()
        
        // 6. Recalculate IP Checksum
        val ipChecksum = calculateChecksum(response, 0, ipHeaderLength)
        response[10] = (ipChecksum shr 8).toByte()
        response[11] = ipChecksum.toByte()
        
        return response
    }
    
    private fun calculateChecksum(data: ByteArray, offset: Int, length: Int): Int {
        var sum = 0
        var i = offset
        val end = offset + length
        
        while (i < end - 1) {
            val word = ((data[i].toInt() and 0xFF) shl 8) or (data[i + 1].toInt() and 0xFF)
            sum += word
            i += 2
        }
        
        if (i < end) {
            val word = (data[i].toInt() and 0xFF) shl 8
            sum += word
        }
        
        while ((sum shr 16) > 0) {
            sum = (sum and 0xFFFF) + (sum shr 16)
        }
        
        return sum.inv() and 0xFFFF
    }
}
