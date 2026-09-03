package com.focuslock.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Process
import com.focuslock.domain.model.SessionStatus
import timber.log.Timber
import com.focuslock.util.PermissionChecker
import com.focuslock.data.repository.SessionRepository
import com.focuslock.data.session.SessionRuntimeController
import com.focuslock.data.worker.SessionWorkerScheduler
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * BootReceiver intercepts the system BOOT_COMPLETED intent.
 * When the user's device is restarted, it runs to verify if there is an active
 * FocusLock session in the database and restarts the blocking services immediately.
 */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "RCV_BOOT"
    }

    @Inject
    lateinit var sessionRepository: SessionRepository

    @Inject
    lateinit var sessionWorkerScheduler: SessionWorkerScheduler

    @Inject
    lateinit var sessionRuntimeController: SessionRuntimeController

    var ioDispatcher: kotlinx.coroutines.CoroutineDispatcher = kotlinx.coroutines.Dispatchers.IO


    override fun onReceive(context: Context, intent: Intent) {
        Timber.tag(TAG).d("onReceive: Intercepted intent with action ${intent.action}")
        // ponytail: accept SYSTEM_UID and own process; reject third-party callers
        val callingUid = Binder.getCallingUid()
        if (callingUid != Process.SYSTEM_UID && callingUid != Process.myUid()) {
            Timber.tag(TAG).w("Blocked non-system caller uid=%d", callingUid)
            return
        }
        processBootIntent(context, intent)
    }

    internal fun processBootIntent(context: Context?, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED && context != null) {
            Timber.tag(TAG).d("Device reboot completed, checking if active session needs recovery.")

            val pendingResult = goAsync()
            val receiverScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + ioDispatcher)
            receiverScope.launch {
                try {
                    val session = sessionRepository.getActiveSessionSync()
                    var isActiveSession = false
                    val expiresAt = session?.expiresAt
                    if (expiresAt != null) {
                        val remainingMillis = expiresAt - System.currentTimeMillis()
                        if (remainingMillis > 0) {
                            Timber.tag(TAG).d("Active session found. Recovering timers and notification.")
                            sessionWorkerScheduler.scheduleSessionTimers(expiresAt)
                            sessionRuntimeController.startRuntime(remainingMillis)
                            isActiveSession = true
                        } else {
                            Timber.tag(TAG).d("Session expired while device was off.")
                            sessionRepository.endSession(session.id, SessionStatus.COMPLETED)
                            sessionRuntimeController.stopRuntime()
                        }
                    }
                    
                    // Start the watchdog service to ensure persistent enforcement
                    com.focuslock.service.watchdog.PermissionWatchdogService.start(context)

                    val isAccessibilityAlive = PermissionChecker.isAccessibilityServiceEnabled(context)
                    val isOverlayGranted = PermissionChecker.isOverlayPermissionGranted(context)
                    if ((!isAccessibilityAlive || !isOverlayGranted) && isActiveSession) {
                        Timber.tag(TAG).w("Critical permissions missing after boot! Launching enforcement overlay.")
                        com.focuslock.service.overlay.PermissionEnforcementService.start(context)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.tag(TAG).e(e, "Error recovering session on boot")
                } finally {
                    pendingResult.finish()
                    receiverScope.cancel()
                }
            }
        }
    }
}
