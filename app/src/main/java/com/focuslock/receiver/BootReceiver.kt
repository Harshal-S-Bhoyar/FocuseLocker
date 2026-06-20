package com.focuslock.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.focuslock.domain.model.SessionStatus
import timber.log.Timber
import com.focuslock.domain.repository.SessionRepository
import com.focuslock.domain.session.SessionRuntimeController
import com.focuslock.domain.worker.SessionWorkerScheduler
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

    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED && context != null) {
            Timber.tag(TAG).d("Device reboot completed, checking if active session needs recovery.")

            val pendingResult = goAsync()
            val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            receiverScope.launch {
                try {
                    val session = sessionRepository.getActiveSessionSync()
                    when (val decision = BootRecoveryDecision.evaluate(session?.expiresAt, System.currentTimeMillis())) {
                        BootRecoveryDecision.NoSession -> Unit
                        is BootRecoveryDecision.Recover -> {
                            Timber.tag(TAG).d("Active session found. Recovering timers and notification.")
                            session?.let {
                                sessionWorkerScheduler.scheduleSessionTimers(it.expiresAt)
                                sessionRuntimeController.startRuntime(decision.remainingMillis)
                            }
                        }
                        BootRecoveryDecision.CompleteExpired -> {
                            Timber.tag(TAG).d("Session expired while device was off.")
                            session?.let { sessionRepository.endSession(it.id, SessionStatus.COMPLETED) }
                            sessionRuntimeController.stopRuntime()
                        }
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
