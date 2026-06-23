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

    suspend fun runLoop() = withContext(Dispatchers.IO) {
        val inputStream = injectedInputStream ?: FileInputStream(vpnInterface.fileDescriptor)
        val outputStream = injectedOutputStream ?: FileOutputStream(vpnInterface.fileDescriptor)
        
        val packet = ByteBuffer.allocate(32767)
        
        try {
            while (isActive) {
                yield()
                val length = inputStream.read(packet.array())
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
                            if (length >= ipHeaderLength + 8) {
                                val protocol = packet.get(9).toInt() and 0xFF
                                if (protocol == 17) {
                                    isUdp = true
                                    udpOffset = ipHeaderLength
                                }
                            }
                        } else if (version == 6) {
                            ipHeaderLength = 40
                            isIpv6 = true
                            if (length >= ipHeaderLength + 8) {
                                val nextHeader = packet.get(6).toInt() and 0xFF
                                if (nextHeader == 17) {
                                    isUdp = true
                                    udpOffset = ipHeaderLength
                                }
                            }
                        }

                        if (!isUdp) {
                            Timber.tag(TAG).d("PACKET IGNORED: Non-UDP packet")
                            continue
                        }

                        val destPort = ((packet.get(udpOffset + 2).toInt() and 0xFF) shl 8) or (packet.get(udpOffset + 3).toInt() and 0xFF)
                        if (destPort != 53) {
                            Timber.tag(TAG).d("PACKET IGNORED: Non-DNS UDP packet (port $destPort)")
                            continue
                        }

                        val domain = DnsPacketParser.extractDomainName(packet, length)
                        if (domain != null) {
                            if (isDomainBlocked(domain)) {
                                Timber.tag(TAG).v("DNS BLOCKED (local): $domain")
                                val response = DnsResponseBuilder.buildNxDomainResponse(packet, length)
                                outputStream.write(response)
                            } else {
                                val dnsOffset = udpOffset + 8
                                val dnsPayloadLength = length - dnsOffset
                                val dnsPayload = ByteArray(dnsPayloadLength)
                                packet.position(dnsOffset)
                                packet.get(dnsPayload)

                                val packetBytes = ByteArray(length)
                                packet.position(0)
                                packet.get(packetBytes)

                                loopScope.launch {
                                    forwardDnsViaDoH(
                                        packetBytes = packetBytes,
                                        outputStream = outputStream,
                                        domain = domain,
                                        dnsPayload = dnsPayload,
                                        isIpv6 = isIpv6,
                                        ipHeaderLength = ipHeaderLength,
                                        udpOffset = udpOffset,
                                        dnsOffset = dnsOffset
                                    )
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
        dnsPayload: ByteArray,
        isIpv6: Boolean,
        ipHeaderLength: Int,
        udpOffset: Int,
        dnsOffset: Int
    ) = withContext(Dispatchers.IO) {
        try {
            val endpoint = dnsConfig.dohEndpoint
            Timber.tag(TAG).d("DoH fwd: domain=$domain ep=$endpoint payload=${dnsPayload.size}B")
            val mediaType = "application/dns-message".toMediaType()
            val requestBody = dnsPayload.toRequestBody(mediaType)
            val request = Request.Builder()
                .url(endpoint)
                .post(requestBody)
                .addHeader("Accept", "application/dns-message")
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                val body = try { response.body?.string()?.take(300) } catch (ex: Exception) { "unreadable" }
                Timber.tag(TAG).e("DoH FAIL: domain=$domain code=${response.code} ep=$endpoint body=$body")
                return@withContext
            }

            val responseData = response.body?.bytes()
            if (responseData == null) {
                Timber.tag(TAG).e("DoH NULL body: domain=$domain ep=$endpoint")
                return@withContext
            }

            Timber.tag(TAG).d("DoH OK: domain=$domain resp=${responseData.size}B")

            val responseArray = buildTunDnsResponse(
                packetBytes = packetBytes,
                dnsOffset = dnsOffset,
                ipHeaderLength = ipHeaderLength,
                udpOffset = udpOffset,
                responseData = responseData,
                isIpv6 = isIpv6
            )

            synchronized(outputStream) {
                outputStream.write(responseArray)
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "DoH EXC: domain=$domain ep=${dnsConfig.dohEndpoint} ${e.javaClass.simpleName}:${e.message}")
        }
    }

    private fun buildTunDnsResponse(

        packetBytes: ByteArray,
        dnsOffset: Int,
        ipHeaderLength: Int,
        udpOffset: Int,
        responseData: ByteArray,
        isIpv6: Boolean
    ): ByteArray {
        val responseLength = dnsOffset + responseData.size
        val responseBuffer = ByteBuffer.allocate(responseLength)

        responseBuffer.put(packetBytes, 0, dnsOffset)
        responseBuffer.put(responseData)

        val responseArray = responseBuffer.array()

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

        return responseArray
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
