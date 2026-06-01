package com.focuslock.service.admin

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.focuslock.R
import com.focuslock.domain.session.SessionEnforcementStateStore
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * FocusDeviceAdminReceiver is the device administrator receiver for FocusLock.
 * It is registered in the AndroidManifest.xml and is responsible for managing
 * the Device Admin permissions. Having Device Admin enabled prevents the user
 * from uninstalling the FocusLock app while a focus session is active.
 *
 * This implementation is beginner-friendly, providing clear logs and comments
 * to explain each lifecycle callback.
 */
@AndroidEntryPoint
class FocusDeviceAdminReceiver : DeviceAdminReceiver() {

    companion object {
        private const val TAG = "FocusDeviceAdmin"
    }

    @Inject
    lateinit var enforcementStateStore: SessionEnforcementStateStore

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Log.d(TAG, "Device Administrator has been successfully enabled.")
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence? {
        return if (enforcementStateStore.isSessionActive()) {
            context.getString(R.string.device_admin_disable_blocked)
        } else {
            super.onDisableRequested(context, intent)
        }
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Log.d(TAG, "Device Administrator has been disabled.")
    }
}
