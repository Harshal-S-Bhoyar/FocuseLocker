package com.focuslock.service.vpn

import java.nio.ByteBuffer

object DnsPacketParser {

    /**
     * Extracts the requested domain name from an IPv4 UDP DNS packet.
     * Returns null if the packet is not a valid DNS query or cannot be parsed.
     */
    fun extractDomainName(buffer: ByteBuffer, length: Int): String? {
        if (length < 20) return null // Minimum IPv4 header length
        
        val startPos = buffer.position()
        
        // Read IP Version and IHL (Internet Header Length)
        val versionAndIhl = buffer.get(startPos).toInt()
        val version = (versionAndIhl shr 4) and 0x0F
        val ihl = versionAndIhl and 0x0F
        
        if (version != 4) return null // Only parsing IPv4
        
        val ipHeaderLength = ihl * 4
        if (length < ipHeaderLength + 8) return null // Need at least IP header + UDP header
        
        // Protocol is at byte offset 9
        val protocol = buffer.get(startPos + 9).toInt() and 0xFF
        if (protocol != 17) return null // 17 is UDP
        
        val udpOffset = startPos + ipHeaderLength
        
        // UDP Destination Port is at offset 2 within UDP header
        val destPort = buffer.getShort(udpOffset + 2).toInt() and 0xFFFF
        if (destPort != 53) return null // 53 is DNS
        
        val dnsOffset = udpOffset + 8 // UDP header is 8 bytes
        
        if (length < dnsOffset + 12) return null // Need at least DNS header (12 bytes)
        
        // Number of questions (QDCOUNT)
        val qdCount = buffer.getShort(dnsOffset + 4).toInt() and 0xFFFF
        if (qdCount == 0) return null
        
        // Read the domain name
        var queryOffset = dnsOffset + 12
        val domainName = StringBuilder()
        
        while (queryOffset < startPos + length) {
            val labelLength = buffer.get(queryOffset).toInt() and 0xFF
            if (labelLength == 0) break // End of domain name
            
            // Pointer handling (compression) not usually seen in the query part, but check for it
            if ((labelLength and 0xC0) == 0xC0) {
                // Not handling compressed names in query for simplicity of MVP, 
                // but standard queries don't usually compress the first question.
                break 
            }
            
            queryOffset++
            
            if (queryOffset + labelLength > startPos + length) return null // Out of bounds
            
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
