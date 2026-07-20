package com.focuslock.service.vpn

import java.nio.ByteBuffer

object DnsPacketParser {

    /**
     * Extracts the requested domain name from an IPv4 or IPv6 UDP DNS packet.
     * Returns null if the packet is not a valid DNS query or cannot be parsed.
     */
    fun extractDomainName(buffer: ByteBuffer, length: Int): String? {
        if (length < 20) return null 
        
        val startPos = buffer.position()
        val versionAndIhl = buffer.get(startPos).toInt()
        val version = (versionAndIhl shr 4) and 0x0F
        
        var ipHeaderLength = 0
        
        if (version == 4) {
            val ihl = versionAndIhl and 0x0F
            ipHeaderLength = ihl * 4
            if (length < ipHeaderLength + 8) return null
            
            val protocol = buffer.get(startPos + 9).toInt() and 0xFF
            if (protocol != 17) return null 
        } else if (version == 6) {
            ipHeaderLength = 40
            if (length < ipHeaderLength + 8) return null
            
            val payloadLength = buffer.getShort(startPos + 4).toInt() and 0xFFFF
            if (ipHeaderLength + payloadLength > buffer.limit() || ipHeaderLength + payloadLength > length) return null
            
            val nextHeader = buffer.get(startPos + 6).toInt() and 0xFF
            if (nextHeader != 17) return null 
        } else {
            return null
        }
        
        val udpOffset = startPos + ipHeaderLength
        val destPort = buffer.getShort(udpOffset + 2).toInt() and 0xFFFF
        if (destPort != 53) return null 
        
        val dnsOffset = udpOffset + 8
        if (length < dnsOffset + 12) return null
        
        val qdCount = buffer.getShort(dnsOffset + 4).toInt() and 0xFFFF
        if (qdCount == 0) return null
        
        var queryOffset = dnsOffset + 12
        val domainName = StringBuilder()
        var iterations = 0
        
        while (queryOffset < startPos + length && iterations < 128 && domainName.length < 254) {
            iterations++
            if (queryOffset >= startPos + length) return null // VULN-002 fix: strict bound check before read
            val labelLength = buffer.get(queryOffset).toInt() and 0xFF
            if (labelLength == 0) break 
            
            if ((labelLength and 0xC0) == 0xC0) {
                break 
            }
            
            queryOffset++
            
            if (queryOffset + labelLength > startPos + length) return null
            
            // ponytail: zero-alloc label read — direct byte→char from buffer via absolute get()
            // ceiling: assumes ASCII domain labels (DNS spec requires LDH subset of ASCII)
            if (domainName.isNotEmpty()) {
                domainName.append(".")
            }
            for (i in 0 until labelLength) {
                domainName.append((buffer.get(queryOffset + i).toInt() and 0xFF).toChar())
            }
            
            queryOffset += labelLength
        }
        
        if (iterations >= 128 || domainName.length >= 254) {
            return null
        }
        
        val result = domainName.toString()
        return result.ifEmpty { null }
    }
}
