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
        val version = (ipVersionAndIhl and 0xFF) ushr 4
        var isIpv6 = false
        var ipHeaderLength = 0
        
        if (version == 4) {
            ipHeaderLength = (ipVersionAndIhl and 0x0F) * 4
        } else if (version == 6) {
            ipHeaderLength = 40
            isIpv6 = true
        } else {
            return response // Unknown IP version
        }
        
        if (isIpv6) {
            for (i in 0..15) {
                val temp = response[8 + i]
                response[8 + i] = response[24 + i]
                response[24 + i] = temp
            }
        } else {
            for (i in 0..3) {
                val temp = response[12 + i]
                response[12 + i] = response[16 + i]
                response[16 + i] = temp
            }
            response[10] = 0
            response[11] = 0
        }
        
        val udpOffset = ipHeaderLength
        
        for (i in 0..1) {
            val temp = response[udpOffset + i]
            response[udpOffset + i] = response[udpOffset + 2 + i]
            response[udpOffset + 2 + i] = temp
        }
        
        val dnsOffset = udpOffset + 8
        
        response[dnsOffset + 2] = (response[dnsOffset + 2].toInt() or 0x80).toByte()
        response[dnsOffset + 3] = (response[dnsOffset + 3].toInt() and 0xF0 or 0x03).toByte()
        
        if (!isIpv6) {
            val ipChecksum = calculateChecksum(response, 0, ipHeaderLength, 0)
            val invChecksum = ipChecksum.inv() and 0xFFFF
            response[10] = (invChecksum shr 8).toByte()
            response[11] = invChecksum.toByte()
        }
        
        val udpChecksum = computeUdpChecksum(response, ipHeaderLength, udpOffset, isIpv6)
        response[udpOffset + 6] = (udpChecksum shr 8).toByte()
        response[udpOffset + 7] = udpChecksum.toByte()
        
        return response
    }
    
    private fun calculateChecksum(data: ByteArray, offset: Int, length: Int, initialSum: Int): Int {
        var sum = initialSum
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
        
        return sum
    }
    
    private fun computeUdpChecksum(responseArray: ByteArray, ipHeaderLength: Int, udpOffset: Int, isIpv6: Boolean): Int {
        responseArray[udpOffset + 6] = 0
        responseArray[udpOffset + 7] = 0

        val udpLength = ((responseArray[udpOffset + 4].toInt() and 0xFF) shl 8) or (responseArray[udpOffset + 5].toInt() and 0xFF)
        var sum = 0
        
        if (isIpv6) {
            sum = calculateChecksum(responseArray, 8, 32, sum)
            sum += udpLength
            sum += 17 
        } else {
            sum = calculateChecksum(responseArray, 12, 8, sum)
            sum += 17 
            sum += udpLength
        }
        
        sum = calculateChecksum(responseArray, udpOffset, udpLength, sum)
        
        val checksum = sum.inv() and 0xFFFF
        return if (checksum == 0) 0xFFFF else checksum
    }
}
