package com.focuslock.service.vpn

import android.net.VpnService
import android.os.ParcelFileDescriptor
import timber.log.Timber
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer

class VpnPacketLoop(
    private val vpnService: VpnService,
    private val vpnInterface: ParcelFileDescriptor,
    private val dnsConfig: DnsConfig,
    private val okHttpClient: OkHttpClient,
    private val isDomainBlocked: (String) -> Boolean,
    private val injectedInputStream: java.io.InputStream? = null,
    private val injectedOutputStream: java.io.OutputStream? = null
) {
    private val TAG = "SVC_VPN_PACKET"
    private val loopScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val bufferPool = java.util.concurrent.ConcurrentLinkedQueue<ByteArray>()

    private fun getBuffer(): ByteArray {
        return bufferPool.poll() ?: ByteArray(32767)
    }

    private fun releaseBuffer(buffer: ByteArray) {
        if (bufferPool.size < 10) {
            bufferPool.offer(buffer)
        }
    }

    suspend fun runLoop() = withContext(Dispatchers.IO) {
        val inputStream = injectedInputStream ?: FileInputStream(vpnInterface.fileDescriptor)
        val outputStream = injectedOutputStream ?: FileOutputStream(vpnInterface.fileDescriptor)
        
        // ponytail: reuse single backing array; ByteBuffer is a lightweight view, not an allocation per iteration
        val packetArray = ByteArray(32767)
        val packet = ByteBuffer.wrap(packetArray)
        val nxBuffer = ByteArray(32767)
        
        try {
            while (isActive) {
                yield()
                val length = inputStream.read(packetArray)
                try {
                    when {
                        length > 0 -> {
                            packet.limit(length)
                            packet.position(0)
                            
                            val version = packet.get(0).toInt() ushr 4
                            var isUdp = false
                            var udpOffset = 0
                            var ipHeaderLength = 0
                            var isIpv6 = false

                            if (version == 4) {
                                ipHeaderLength = (packet.get(0).toInt() and 0x0F) * 4
                                if (length >= ipHeaderLength + 8 && length >= 10) {
                                    val protocol = packet.get(9).toInt() and 0xFF
                                    if (protocol == 17) {
                                        isUdp = true
                                        udpOffset = ipHeaderLength
                                    }
                                }
                            } else if (version == 6) {
                                ipHeaderLength = 40
                                isIpv6 = true
                                if (length >= ipHeaderLength + 8 && length >= 48) {
                                    val nextHeader = packet.get(6).toInt() and 0xFF
                                    if (nextHeader == 17) {
                                        isUdp = true
                                        udpOffset = ipHeaderLength
                                    }
                                }
                            }

                            if (!isUdp) {

                                continue
                            }

                            val destPort = ((packet.get(udpOffset + 2).toInt() and 0xFF) shl 8) or (packet.get(udpOffset + 3).toInt() and 0xFF)
                            if (destPort != 53) {
                                Timber.tag(TAG).v("PACKET IGNORED: Non-DNS UDP packet (port %d)", destPort)
                                continue
                            }

                            val domain = DnsPacketParser.extractDomainName(packet, length)
                            if (domain != null) {
                                if (isDomainBlocked(domain)) {
                                    Timber.tag(TAG).v("DNS BLOCKED (local): $domain")
                                    val responseLength = buildNxDomainResponse(packet, length, nxBuffer)
                                    outputStream.write(nxBuffer, 0, responseLength)
                                } else {
                                    val dnsOffset = udpOffset + 8
                                    val dnsPayloadLength = length - dnsOffset

                                    val packetBytes = getBuffer()
                                    packet.position(0)
                                    packet.get(packetBytes, 0, length)

                                    loopScope.launch {
                                        try {
                                            forwardDnsViaDoH(
                                                packetBytes = packetBytes,
                                                outputStream = outputStream,
                                                domain = domain,
                                                dnsPayloadLength = dnsPayloadLength,
                                                isIpv6 = isIpv6,
                                                ipHeaderLength = ipHeaderLength,
                                                udpOffset = udpOffset,
                                                dnsOffset = dnsOffset
                                            )
                                        } finally {
                                            releaseBuffer(packetBytes)
                                        }
                                    }
                                }
                            } else {
                                Timber.tag(TAG).w("DNS PARSE FAILED: Could not extract domain from packet")
                            }
                        }
                        length == 0 -> {
                            delay(100L)
                        }
                        else -> {
                            Timber.tag(TAG).w("TUN read returned EOF — interface may be closing. Backing off.")
                            delay(500L)
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.tag(TAG).w(e, "Error parsing individual DNS packet — skipping")
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Packet loop error")
        } finally {
            inputStream.close()
            outputStream.close()
            loopScope.cancel()
        }
    }
    
    private suspend fun forwardDnsViaDoH(
        packetBytes: ByteArray,
        outputStream: java.io.OutputStream,
        domain: String,
        dnsPayloadLength: Int,
        isIpv6: Boolean,
        ipHeaderLength: Int,
        udpOffset: Int,
        dnsOffset: Int
    ) = withContext(Dispatchers.IO) {
        try {
            val endpoint = dnsConfig.dohEndpoint
            Timber.tag(TAG).v("DoH fwd: ep=$endpoint payload=${dnsPayloadLength}B")
            val mediaType = "application/dns-message".toMediaType()
            val requestBody = packetBytes.toRequestBody(mediaType, dnsOffset, dnsPayloadLength)
            val request = Request.Builder()
                .url(endpoint)
                .post(requestBody)
                .addHeader("Accept", "application/dns-message")
                .build()

            var lastException: Exception? = null
            for (attempt in 0..1) {
                try {
                    okHttpClient.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) {
                            Timber.tag(TAG).e("DoH FAIL: code=${response.code} ep=$endpoint")
                            return@withContext
                        }

                        val responseData = response.body?.bytes()
                        if (responseData == null) {
                            Timber.tag(TAG).e("DoH NULL body: ep=$endpoint")
                            return@withContext
                        }

                        Timber.tag(TAG).v("DoH OK: resp=${responseData.size}B")

                        val (responseArray, responseLen) = buildTunDnsResponse(
                            packetBytes = packetBytes,
                            dnsOffset = dnsOffset,
                            ipHeaderLength = ipHeaderLength,
                            udpOffset = udpOffset,
                            responseData = responseData,
                            isIpv6 = isIpv6
                        )

                        try {
                            synchronized(outputStream) {
                                outputStream.write(responseArray, 0, responseLen)
                            }
                        } finally {
                            releaseBuffer(responseArray)
                        }
                        return@withContext
                    }

                } catch (e: java.net.ConnectException) {
                    lastException = e
                    if (attempt == 0) delay(200L)
                } catch (e: java.net.SocketException) {
                    lastException = e
                    if (attempt == 0) delay(200L)
                }
            }
            Timber.tag(TAG).w("DoH UNREACHABLE after retry: ${lastException?.message}")
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "DoH EXC: ep=${dnsConfig.dohEndpoint} ${e.javaClass.simpleName}:${e.message}")
        }
    }

    // ponytail: returns (responseArray, responseLength) using pooled buffer
    // ceiling: pool capped at 10; if exhausted, falls back to fresh alloc (same as before)
    private fun buildTunDnsResponse(
        packetBytes: ByteArray,
        dnsOffset: Int,
        ipHeaderLength: Int,
        udpOffset: Int,
        responseData: ByteArray,
        isIpv6: Boolean
    ): Pair<ByteArray, Int> {
        val responseLength = dnsOffset + responseData.size
        if (responseLength > 65535) {
            throw IllegalArgumentException("Response length $responseLength exceeds UDP/IP maximum")
        }
        val responseArray = if (responseLength > 32767) ByteArray(responseLength) else getBuffer()

        System.arraycopy(packetBytes, 0, responseArray, 0, dnsOffset)
        System.arraycopy(responseData, 0, responseArray, dnsOffset, responseData.size)

        if (isIpv6) {
            val payloadLength = responseLength - 40
            responseArray[4] = (payloadLength shr 8).toByte()
            responseArray[5] = payloadLength.toByte()
        } else {
            responseArray[2] = (responseLength shr 8).toByte()
            responseArray[3] = responseLength.toByte()
        }

        val udpLength = responseLength - ipHeaderLength
        responseArray[udpOffset + 4] = (udpLength shr 8).toByte()
        responseArray[udpOffset + 5] = udpLength.toByte()

        swapIpAddresses(responseArray, isIpv6)
        swapUdpPorts(responseArray, udpOffset)

        if (!isIpv6) {
            responseArray[10] = 0
            responseArray[11] = 0
            val ipChecksum = calculateChecksum(responseArray, 0, ipHeaderLength, 0)
            val ipChecksumInv = ipChecksum.inv() and 0xFFFF
            responseArray[10] = (ipChecksumInv shr 8).toByte()
            responseArray[11] = ipChecksumInv.toByte()
        }

        val udpChecksum = computeUdpChecksum(responseArray, ipHeaderLength, udpOffset, isIpv6)
        responseArray[udpOffset + 6] = (udpChecksum shr 8).toByte()
        responseArray[udpOffset + 7] = udpChecksum.toByte()

        return Pair(responseArray, responseLength)
    }

    /**
     * Builds an NXDOMAIN (domain does not exist) response based on the incoming query packet.
     * This effectively blocks the domain resolution.
     */
    private fun buildNxDomainResponse(packetBuffer: ByteBuffer, length: Int, response: ByteArray): Int {
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
            return length // Unknown IP version
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
        
        return length
    }

    private fun swapIpAddresses(responseArray: ByteArray, isIpv6: Boolean) {
        if (isIpv6) {
            for (i in 0..15) {
                val temp = responseArray[8 + i]
                responseArray[8 + i] = responseArray[24 + i]
                responseArray[24 + i] = temp
            }
        } else {
            for (i in 0..3) {
                val temp = responseArray[12 + i]
                responseArray[12 + i] = responseArray[16 + i]
                responseArray[16 + i] = temp
            }
        }
    }

    private fun swapUdpPorts(responseArray: ByteArray, udpOffset: Int) {
        for (i in 0..1) {
            val temp = responseArray[udpOffset + i]
            responseArray[udpOffset + i] = responseArray[udpOffset + 2 + i]
            responseArray[udpOffset + 2 + i] = temp
        }
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
