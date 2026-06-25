package com.focuslock.service.vpn

import android.net.VpnService
import okhttp3.Dns
import timber.log.Timber
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

class BootstrapDns(
    private val vpnService: VpnService? = null,
    private val fallbackServers: List<String> = com.focuslock.BuildConfig.BOOTSTRAP_FALLBACK_SERVERS.split(",").map { it.trim() }
) : Dns {
    private val cache = ConcurrentHashMap<String, Pair<List<InetAddress>, Long>>()
    private val TTL_MS = 5 * 60 * 1000L
    private val nextDnsHostname = try { java.net.URI(com.focuslock.BuildConfig.NEXTDNS_BASE_URL).host } catch (e: Exception) { null }
    private val fallbackDnsHostname = try { java.net.URI(com.focuslock.BuildConfig.FALLBACK_DNS_ENDPOINT).host } catch (e: Exception) { null }

    override fun lookup(hostname: String): List<InetAddress> {
        // Fast-path known DoH endpoints to avoid UDP bootstrap loop
        if (nextDnsHostname != null && (hostname.endsWith(nextDnsHostname) || hostname == nextDnsHostname)) {
            return com.focuslock.BuildConfig.NEXTDNS_FASTPATH_IPS.split(",").mapNotNull {
                try { InetAddress.getByName(it.trim()) } catch (e: Exception) { null }
            }
        }
        if (fallbackDnsHostname != null && hostname == fallbackDnsHostname) {
            return com.focuslock.BuildConfig.ADGUARD_FASTPATH_IPS.split(",").mapNotNull {
                try { InetAddress.getByName(it.trim()) } catch (e: Exception) { null }
            }
        }

        val cached = cache[hostname]
        if (cached != null && System.currentTimeMillis() < cached.second) {
            return cached.first
        }

        for (server in fallbackServers) {
            try {
                val ipList = resolveRawUdp(hostname, server)
                if (ipList.isNotEmpty()) {
                    cache[hostname] = Pair(ipList, System.currentTimeMillis() + TTL_MS)
                    return ipList
                }
            } catch (e: Exception) {
                Timber.tag("BootstrapDns").w(e, "Failed to resolve $hostname via $server")
            }
        }
        
        // Fallback to system DNS
        try {
            val systemResult = Dns.SYSTEM.lookup(hostname)
            if (systemResult.isNotEmpty()) {
                return systemResult
            }
        } catch (e: Exception) {
            Timber.tag("BootstrapDns").w(e, "System DNS fallback failed for $hostname")
        }

        throw UnknownHostException("Failed to resolve $hostname using bootstrap servers and system fallback")
    }

    private fun resolveRawUdp(hostname: String, dnsServerIp: String): List<InetAddress> {
        var socket: DatagramSocket? = null
        try {
            socket = DatagramSocket()
            vpnService?.protect(socket)
            socket.soTimeout = 3000

            val serverAddress = InetAddress.getByName(dnsServerIp)
            val requestData = buildDnsQuery(hostname)
            
            val requestPacket = DatagramPacket(requestData, requestData.size, serverAddress, 53)
            socket.send(requestPacket)

            val responseData = ByteArray(512)
            val responsePacket = DatagramPacket(responseData, responseData.size)
            socket.receive(responsePacket)

            return parseDnsResponse(responseData, responsePacket.length)
        } finally {
            socket?.close()
        }
    }

    private fun buildDnsQuery(hostname: String): ByteArray {
        val parts = hostname.split(".")
        val qnameLen = parts.sumOf { it.length + 1 } + 1
        val data = ByteArray(12 + qnameLen + 4)
        
        val txId = Random.nextInt(0, 65535)
        data[0] = (txId shr 8).toByte()
        data[1] = txId.toByte()
        
        data[2] = 0x01.toByte() // Flags: Standard query, recursion desired
        data[3] = 0x00.toByte()
        
        data[4] = 0x00.toByte() // QDCOUNT (1)
        data[5] = 0x01.toByte()
        
        var offset = 12
        for (part in parts) {
            data[offset++] = part.length.toByte()
            for (char in part) {
                data[offset++] = char.code.toByte()
            }
        }
        data[offset++] = 0x00.toByte()
        
        data[offset++] = 0x00.toByte() // QTYPE A
        data[offset++] = 0x01.toByte()
        
        data[offset++] = 0x00.toByte() // QCLASS IN
        data[offset++] = 0x01.toByte()
        
        return data
    }

    private fun parseDnsResponse(data: ByteArray, length: Int): List<InetAddress> {
        val result = mutableListOf<InetAddress>()
        if (length < 12) return result

        val ancount = ((data[6].toInt() and 0xFF) shl 8) or (data[7].toInt() and 0xFF)
        if (ancount == 0) return result

        var offset = 12
        // Skip Question Section
        while (offset < length) {
            val len = data[offset].toInt() and 0xFF
            if (len == 0) {
                offset++
                break
            }
            if ((len and 0xC0) == 0xC0) {
                offset += 2
                break
            }
            offset += len + 1
        }
        offset += 4 // Skip QTYPE and QCLASS

        // Parse Answer Section
        for (i in 0 until ancount) {
            if (offset >= length) break
            
            // Skip Name
            while (offset < length) {
                val len = data[offset].toInt() and 0xFF
                if (len == 0) {
                    offset++
                    break
                }
                if ((len and 0xC0) == 0xC0) {
                    offset += 2
                    break
                }
                offset += len + 1
            }

            if (offset + 10 > length) break

            val type = ((data[offset].toInt() and 0xFF) shl 8) or (data[offset + 1].toInt() and 0xFF)
            offset += 2
            
            // Skip Class and TTL
            offset += 6
            
            val rdlength = ((data[offset].toInt() and 0xFF) shl 8) or (data[offset + 1].toInt() and 0xFF)
            offset += 2

            if (type == 1 && rdlength == 4 && offset + 4 <= length) { // A record
                val ipBytes = data.copyOfRange(offset, offset + 4)
                result.add(InetAddress.getByAddress(ipBytes))
            }
            
            offset += rdlength
        }
        return result
    }
}
