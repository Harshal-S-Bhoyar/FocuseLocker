package com.focuslock.service.vpn

import android.net.VpnService
import android.os.ParcelFileDescriptor
import timber.log.Timber
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer

class VpnPacketLoop(
    private val vpnService: VpnService,
    private val vpnInterface: ParcelFileDescriptor,
    private val dnsConfig: DnsConfig,
    private val isDomainBlocked: (String) -> Boolean
) {
    private val TAG = "VpnPacketLoop"
    
    // Reusable socket — created once, protected once, reused for all DNS forwards.
    // Eliminates OS-level socket allocation + vpnService.protect() call per DNS query.
    private val sharedDnsSocket: DatagramSocket by lazy {
        DatagramSocket().also { socket ->
            socket.soTimeout = DNS_TIMEOUT_MILLIS
            vpnService.protect(socket) // Only called ONCE for the lifetime of the loop
        }
    }

    suspend fun runLoop() = withContext(Dispatchers.IO) {
        val inputStream = FileInputStream(vpnInterface.fileDescriptor)
        val outputStream = FileOutputStream(vpnInterface.fileDescriptor)
        
        val packet = ByteBuffer.allocate(32767)
        
        try {
            while (isActive) {
                // yield() gives the coroutine scheduler a chance to cancel cleanly
                yield()
                val length = inputStream.read(packet.array())
                when {
                    length > 0 -> {
                        packet.limit(length)
                        packet.position(0)
                        
                        val domain = DnsPacketParser.extractDomainName(packet, length)
                        if (domain != null) {
                            if (isDomainBlocked(domain)) {
                                val response = DnsResponseBuilder.buildNxDomainResponse(packet, length)
                                outputStream.write(response)
                            } else {
                                // Forward to upstream DNS
                                forwardDns(packet, length, outputStream)
                            }
                        }
                    }
                    length == 0 -> {
                        // No data available — sleep briefly to avoid 100% CPU busy-loop
                        // This is the #1 battery drain fix: the VPN was spinning at full speed on idle
                        delay(10L)
                    }
                    else -> {
                        // length == -1 means EOF (TUN interface was closed by OS or VPN revoked)
                        // Sleep longer before retry to prevent 100% CPU spin on a closed fd
                        Timber.tag(TAG).w("TUN read returned EOF — interface may be closing. Backing off.")
                        delay(500L)
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Packet loop error")
        } finally {
            inputStream.close()
            outputStream.close()
            runCatching { sharedDnsSocket.close() }
        }
    }
    
    private fun forwardDns(packetBuffer: ByteBuffer, length: Int, outputStream: FileOutputStream) {
        try {
            // Extract original IP and UDP headers to reconstruct response
            val ipHeaderLength = (packetBuffer.get(0).toInt() and 0x0F) * 4
            val udpOffset = ipHeaderLength
            val dnsOffset = udpOffset + 8
            val dnsPayloadLength = length - dnsOffset
            
            val dnsPayload = ByteArray(dnsPayloadLength)
            packetBuffer.position(dnsOffset)
            packetBuffer.get(dnsPayload)
            
            // Use shared socket instead of creating a new one per query
            val receivePacket = queryUpstreamDns(sharedDnsSocket, dnsPayload)
            val responseArray = buildTunDnsResponse(
                packetBuffer = packetBuffer,
                dnsOffset = dnsOffset,
                ipHeaderLength = ipHeaderLength,
                udpOffset = udpOffset,
                receivePacket = receivePacket
            )

            outputStream.write(responseArray)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error forwarding DNS")
        }
    }

    private fun buildTunDnsResponse(
        packetBuffer: ByteBuffer,
        dnsOffset: Int,
        ipHeaderLength: Int,
        udpOffset: Int,
        receivePacket: DatagramPacket
    ): ByteArray {
        val responseLength = dnsOffset + receivePacket.length
        val responseBuffer = ByteBuffer.allocate(responseLength)

        packetBuffer.position(0)
        val headerBytes = ByteArray(dnsOffset)
        packetBuffer.get(headerBytes)
        responseBuffer.put(headerBytes)
        responseBuffer.put(receivePacket.data, 0, receivePacket.length)

        val responseArray = responseBuffer.array()

        responseArray[2] = (responseLength shr 8).toByte()
        responseArray[3] = responseLength.toByte()

        val udpLength = responseLength - ipHeaderLength
        responseArray[udpOffset + 4] = (udpLength shr 8).toByte()
        responseArray[udpOffset + 5] = udpLength.toByte()

        swapIpAddresses(responseArray)
        swapUdpPorts(responseArray, udpOffset)

        responseArray[udpOffset + 6] = 0
        responseArray[udpOffset + 7] = 0

        responseArray[10] = 0
        responseArray[11] = 0
        val ipChecksum = calculateChecksum(responseArray, 0, ipHeaderLength)
        responseArray[10] = (ipChecksum shr 8).toByte()
        responseArray[11] = ipChecksum.toByte()

        return responseArray
    }

    private fun swapIpAddresses(responseArray: ByteArray) {
        for (i in 0..3) {
            val temp = responseArray[12 + i]
            responseArray[12 + i] = responseArray[16 + i]
            responseArray[16 + i] = temp
        }
    }

    private fun swapUdpPorts(responseArray: ByteArray, udpOffset: Int) {
        for (i in 0..1) {
            val temp = responseArray[udpOffset + i]
            responseArray[udpOffset + i] = responseArray[udpOffset + 2 + i]
            responseArray[udpOffset + 2 + i] = temp
        }
    }

    private fun queryUpstreamDns(socket: DatagramSocket, dnsPayload: ByteArray): DatagramPacket {
        socket.soTimeout = DNS_TIMEOUT_MILLIS
        var lastFailure: Exception? = null

        for (resolver in dnsConfig.resolvers) {
            try {
                val queryPacket = DatagramPacket(
                    dnsPayload,
                    dnsPayload.size,
                    InetAddress.getByName(resolver),
                    DNS_PORT
                )
                socket.send(queryPacket)

                val responseData = ByteArray(MAX_DNS_RESPONSE_BYTES)
                return DatagramPacket(responseData, responseData.size).also(socket::receive)
            } catch (e: Exception) {
                lastFailure = e
            }
        }

        throw lastFailure ?: IllegalStateException("No DNS resolver configured")
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

    private companion object {
        const val DNS_PORT = 53
        const val DNS_TIMEOUT_MILLIS = 3000
        const val MAX_DNS_RESPONSE_BYTES = 4096
    }
}
