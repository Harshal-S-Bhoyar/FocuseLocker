package com.focuslock.data.session

import android.content.Context
import android.content.Intent
import android.net.VpnService
import androidx.core.content.ContextCompat
import com.focuslock.domain.session.SessionEnforcementStateStore
import com.focuslock.domain.session.SessionRuntimeController
import com.focuslock.notification.SessionNotificationManager
import com.focuslock.service.overlay.BlockingOverlayService
import com.focuslock.service.vpn.FocusVpnService
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidSessionRuntimeController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val notificationManager: SessionNotificationManager,
    private val enforcementStateStore: SessionEnforcementStateStore
) : SessionRuntimeController {

    override fun startRuntime(remainingMillis: Long) {
        enforcementStateStore.setSessionActive(true)
        notificationManager.updateNotification(remainingMillis)

        if (VpnService.prepare(context) == null) {
            ContextCompat.startForegroundService(context, Intent(context, FocusVpnService::class.java))
        }
    }

    override fun stopRuntime() {
        enforcementStateStore.setSessionActive(false)
        context.stopService(Intent(context, FocusVpnService::class.java))
        context.stopService(Intent(context, BlockingOverlayService::class.java))
        notificationManager.cancelNotification()
    }
}
