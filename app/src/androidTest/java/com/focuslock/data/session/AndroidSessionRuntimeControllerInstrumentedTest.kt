package com.focuslock.data.session

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.focuslock.domain.session.SessionEnforcementStateStore
import com.focuslock.notification.SessionNotificationManager
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidSessionRuntimeControllerInstrumentedTest {

    @Test
    fun stopRuntimeClearsEnforcementState() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val stateStore = FakeSessionEnforcementStateStore(isActive = true)
        val controller = AndroidSessionRuntimeController(
            context = context,
            notificationManager = SessionNotificationManager(context),
            enforcementStateStore = stateStore
        )

        controller.stopRuntime()

        assertFalse(stateStore.isSessionActive())
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
