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
        
        while (queryOffset < startPos + length) {
            val labelLength = buffer.get(queryOffset).toInt() and 0xFF
            if (labelLength == 0) break 
            
            if ((labelLength and 0xC0) == 0xC0) {
                break 
            }
            
            queryOffset++
            
            if (queryOffset + labelLength > startPos + length) return null 
            
            val labelBytes = ByteArray(labelLength)
            val oldPos = buffer.position()
            buffer.position(queryOffset)
            buffer.get(labelBytes)
            buffer.position(oldPos)
            
            if (domainName.isNotEmpty()) {
                domainName.append(".")
            }
            domainName.append(String(labelBytes))
            
            queryOffset += labelLength
        }
        
        return domainName.toString()
    }
}
