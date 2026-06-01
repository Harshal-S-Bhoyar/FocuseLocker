package com.focuslock.notification

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SessionNotificationManagerInstrumentedTest {

    @Test
    fun notificationUsesAppOwnedSmallIcon() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val notification = SessionNotificationManager(context).getNotification(60_000L)

        assertNotEquals(0, notification.smallIcon.resId)
    }
}
