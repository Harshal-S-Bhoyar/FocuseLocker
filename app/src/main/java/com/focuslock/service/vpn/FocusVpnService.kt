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
import com.focuslock.data.repository.SessionRepository
import com.focuslock.domain.usecase.GetActiveBlockedDomainsUseCase
import com.focuslock.domain.usecase.GetAllBlockedDomainsFlowUseCase
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

class FocusVpnService : VpnService() {

    private val TAG = "SVC_VPN"
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    private var vpnInterface: ParcelFileDescriptor? = null
    private var packetLoopJob: Job? = null
    private var sessionMonitorJob: Job? = null
    private var domainObserverJob: Job? = null

    private lateinit var blockingRepository: com.focuslock.data.repository.BlockingRepository
    private lateinit var sessionRepository: SessionRepository

    @Volatile
    private var activeDomains: HashSet<String> = HashSet()
    
    private lateinit var dnsConfig: DnsConfig
    private lateinit var okHttpClient: okhttp3.OkHttpClient

    override fun onCreate() {
        super.onCreate()
        dnsConfig = DnsConfig.fromContext(this)
        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            BlockingServiceEntryPoint::class.java
        )
        blockingRepository = entryPoint.blockingRepository()
        sessionRepository = entryPoint.sessionRepository()
        okHttpClient = entryPoint.okHttpClient()
        
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Timber.tag(TAG).d("VPN Service starting")
        
        val notification = createNotification()
        startForeground(NOTIFICATION_ID, notification)
        
        serviceScope.launch {
            // Monitor session status and scope activeDomains strictly to selectedCategoryKeys, updating dynamically
            sessionMonitorJob?.cancel()
            sessionMonitorJob = launch {
                sessionRepository.getActiveSession().flatMapLatest { session ->
                    if (session == null) {
                        flowOf(null)
                    } else {
                        val keys = session.selectedCategoryKeys.filter { it.isNotBlank() }
                        if (keys.isNotEmpty()) {
                            blockingRepository.getDomainsByCategoriesFlow(keys)
                        } else {
                            flowOf(emptyList())
                        }
                    }
                }.collectLatest { domains ->
                    if (domains == null) {
                        activeDomains = HashSet()
                        stopVpn()
                    } else {
                        activeDomains = HashSet(domains.map { it.lowercase() })
                        Timber.tag(TAG).d("VPN Blocklist updated for session: ${activeDomains.size} domains active")
                        // ponytail: invoke startVpn only after active session blocklist is configured
                        startVpn()
                    }
                }
            }
        }
        
        return START_STICKY
    }

    private fun startVpn() {
        if (vpnInterface != null) return
        
        try {
            val builder = Builder()
            builder.addAddress(dnsConfig.virtualIpv4, 32)
            builder.addAddress(dnsConfig.virtualIpv6, 128)
            
            val dnsIpv4 = "10.0.0.3"
            val dnsIpv6 = "fd00:1:fd00:1:fd00:1:fd00:2"
            
            // Route only the virtual DNS IPs into the TUN.
            // All other traffic, including DoH to NextDNS, goes over the physical network.
            builder.addRoute(dnsIpv4, 32)
            builder.addRoute(dnsIpv6, 128)
            
            builder.addDnsServer(dnsIpv4)
            builder.addDnsServer(dnsIpv6)
            builder.setSession("FocusLock VPN")
            builder.setMtu(1500)
            
            vpnInterface = builder.establish()
            
            vpnInterface?.let { activeInterface ->
                Timber.tag(TAG).d("TUN interface established — DNS captured locally and forwarded via DoH")
                packetLoopJob = serviceScope.launch {
                    val dohClient = okHttpClient.newBuilder()
                        .dns(BootstrapDns(this@FocusVpnService))
                        .socketFactory(ProtectedSocketFactory(this@FocusVpnService))
                        .connectTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
                        .readTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
                        .cache(null)
                        .build()

                    val packetLoop = VpnPacketLoop(
                        vpnService = this@FocusVpnService,
                        vpnInterface = activeInterface,
                        dnsConfig = dnsConfig,
                        okHttpClient = dohClient,
                        isDomainBlocked = { domain ->
                            isDomainOrParentBlocked(domain)
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
        domainObserverJob?.cancel()
        vpnInterface?.close()
        vpnInterface = null
        stopSelf()
    }

    internal fun isDomainOrParentBlocked(domain: String): Boolean {
        val currentDomains = activeDomains // Grab volatile reference
        var blocked = false
        var currentDomain = domain.lowercase()
        
        // Recursive subdomain check
        while (currentDomain.isNotEmpty()) {
            if (currentDomains.contains(currentDomain)) {
                blocked = true
                break
            }
            val dotIndex = currentDomain.indexOf('.')
            if (dotIndex == -1) break
            currentDomain = currentDomain.substring(dotIndex + 1)
        }
        return blocked
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
