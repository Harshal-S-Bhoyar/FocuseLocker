package com.focuslock.service.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import timber.log.Timber
import androidx.core.app.NotificationCompat
import com.focuslock.R
import com.focuslock.di.BlockingServiceEntryPoint
import com.focuslock.domain.repository.SessionRepository
import com.focuslock.domain.usecase.GetActiveBlockedDomainsUseCase
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class FocusVpnService : VpnService() {

    private val TAG = "FocusVpnService"
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    private var vpnInterface: ParcelFileDescriptor? = null
    private var packetLoopJob: Job? = null
    private var sessionMonitorJob: Job? = null
    
    private lateinit var getActiveBlockedDomainsUseCase: GetActiveBlockedDomainsUseCase
    private lateinit var sessionRepository: SessionRepository
    
    // HashSet gives O(1) lookup — previously was mutableSetOf which could be slow
    // This matters because isDomainBlocked() is called on EVERY DNS packet
    private val activeDomains = HashSet<String>()
    private val dnsConfig = DnsConfig.DEFAULT

    override fun onCreate() {
        super.onCreate()
        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            BlockingServiceEntryPoint::class.java
        )
        getActiveBlockedDomainsUseCase = entryPoint.getActiveBlockedDomainsUseCase()
        sessionRepository = entryPoint.sessionRepository()
        
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Timber.tag(TAG).d("VPN Service starting")
        
        val notification = createNotification()
        startForeground(NOTIFICATION_ID, notification)
        
        serviceScope.launch {
            // Load blocked domains into memory for O(1) lookup
            val domains = getActiveBlockedDomainsUseCase()
            activeDomains.clear()
            // Store lowercase for O(1) case-insensitive lookup in isDomainBlocked()
            activeDomains.addAll(domains.map { it.lowercase() })
            
            // Monitor session status
            sessionMonitorJob?.cancel()
            sessionMonitorJob = launch {
                sessionRepository.getActiveSession().collectLatest { session ->
                    if (session == null) {
                        stopVpn()
                    }
                }
            }
            
            startVpn()
        }
        
        return START_STICKY
    }

    private fun startVpn() {
        if (vpnInterface != null) return
        
        try {
            val builder = Builder()
            builder.addAddress("10.0.0.2", 32)
            // Only DNS is routed through the TUN. Routing all traffic would require
            // a userspace TCP/IP stack and would otherwise drop normal internet access.
            dnsConfig.resolvers.forEach { resolver ->
                builder.addRoute(resolver, 32)
            }
            builder.addDnsServer(dnsConfig.primaryResolver)
            builder.setSession("FocusLock VPN")
            builder.setMtu(1500)
            
            vpnInterface = builder.establish()
            
            vpnInterface?.let { activeInterface ->
                Timber.tag(TAG).d("TUN interface established")
                packetLoopJob = serviceScope.launch {
                    val packetLoop = VpnPacketLoop(
                        this@FocusVpnService,
                        activeInterface,
                        dnsConfig = dnsConfig,
                        isDomainBlocked = { domain ->
                            // O(1) HashSet lookup instead of O(n) linear scan
                            // First check exact match, then check if it's a subdomain of a blocked domain
                            activeDomains.contains(domain.lowercase()) ||
                                domain.contains('.') && activeDomains.contains(
                                    domain.substringAfter('.').lowercase()
                                )
                        }
                    )
                    packetLoop.runLoop()
                }
            } ?: run {
                Timber.tag(TAG).e("Failed to establish TUN interface")
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error starting VPN")
        }
    }

    private fun stopVpn() {
        packetLoopJob?.cancel()
        sessionMonitorJob?.cancel()
        vpnInterface?.close()
        vpnInterface = null
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopVpn()
        serviceScope.cancel()
    }

    override fun onRevoke() {
        stopVpn()
        super.onRevoke()
    }
    
    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.vpn_notification_channel),
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.vpn_notification_title))
            .setContentText(getString(R.string.vpn_notification_text))
            .setSmallIcon(R.drawable.ic_focuslock_secure)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "vpn_service_channel"
        private const val NOTIFICATION_ID = 2
    }
}
