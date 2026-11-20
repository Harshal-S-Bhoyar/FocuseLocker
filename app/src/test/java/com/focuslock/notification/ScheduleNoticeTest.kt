package com.focuslock.notification

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ScheduleNoticeTest {

    private lateinit var context: Context
    private lateinit var manager: SessionNotificationManager

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        Shadows.shadowOf(context.applicationContext as Application)
            .grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        manager = SessionNotificationManager(context)
    }

    @Test
    fun `scheduled notice names the fired rule`() {
        manager.notifyScheduleFired("Monday 20:00")

        val notifications = Shadows.shadowOf(
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        ).allNotifications
        assertTrue(notifications.size == 1)
        assertTrue(notifications[0].extras.getString("android.text")!!.contains("Monday 20:00"))
    }
}
