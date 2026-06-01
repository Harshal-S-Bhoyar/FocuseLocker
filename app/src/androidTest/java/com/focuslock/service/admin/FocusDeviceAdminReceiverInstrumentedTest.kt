package com.focuslock.service.admin

import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.focuslock.R
import com.focuslock.domain.session.SessionEnforcementStateStore
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FocusDeviceAdminReceiverInstrumentedTest {

    @Test
    fun disableRequestedReturnsWarningDuringActiveSession() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val receiver = FocusDeviceAdminReceiver().apply {
            enforcementStateStore = FakeSessionEnforcementStateStore(isActive = true)
        }

        val warning = receiver.onDisableRequested(context, Intent())

        assertEquals(context.getString(R.string.device_admin_disable_blocked), warning)
    }

    private class FakeSessionEnforcementStateStore(
        private var isActive: Boolean
    ) : SessionEnforcementStateStore {
        override fun isSessionActive(): Boolean = isActive

        override fun setSessionActive(isActive: Boolean) {
            this.isActive = isActive
        }
    }
}
