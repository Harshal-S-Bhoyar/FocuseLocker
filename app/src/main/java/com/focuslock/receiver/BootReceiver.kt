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
import com.focuslock.data.worker.WorkScheduler
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
        private const val ACTION_QUICKBOOT_POWERON = "android.intent.action.QUICKBOOT_POWERON"
    }

    private fun isRearmAction(action: String?): Boolean = action == Intent.ACTION_BOOT_COMPLETED ||
        action == Intent.ACTION_LOCKED_BOOT_COMPLETED ||
        action == ACTION_QUICKBOOT_POWERON ||
        action == Intent.ACTION_MY_PACKAGE_REPLACED

    @Inject
    lateinit var sessionRepository: SessionRepository

    @Inject
    lateinit var workScheduler: WorkScheduler

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
        val action = intent?.action
        if (context == null || !isRearmAction(action)) {
            return
        }
        if (action != Intent.ACTION_BOOT_COMPLETED) {
            Timber.tag(TAG).d("Rearm on %s", action)
            workScheduler.scheduleScheduleChecks()
            return
        }
        Timber.tag(TAG).d("Device reboot completed, checking if active session needs recovery.")

        workScheduler.scheduleScheduleChecks()
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
                            workScheduler.scheduleSessionTimers(expiresAt)
                            sessionRuntimeController.startRuntime(remainingMillis)
                            isActiveSession = true
                        } else {
                            Timber.tag(TAG).d("Session expired while device was off.")
                            sessionRepository.endSession(session.id, SessionStatus.COMPLETED)
                            sessionRuntimeController.stopRuntime()
                        }
                    }
                    
                    // Start the watchdog only with a live session; with nothing to
                    // guard it would poll forever (foreground service + DB + logs).
                    if (isActiveSession) {
                        com.focuslock.service.watchdog.PermissionWatchdogService.start(context)
                    } else {
                        Timber.tag(TAG).d("No active session after boot, skipping watchdog start.")
                    }

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
