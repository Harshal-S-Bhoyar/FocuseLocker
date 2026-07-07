package com.focuslock.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

import org.robolectric.util.ReflectionHelpers
import android.os.Build

@RunWith(RobolectricTestRunner::class)
@Config(manifest=Config.NONE)
class OemAutostartHelperTest {

    @Test
    fun testGetAutostartIntent_WithXiaomi() {
    private var originalManufacturer: String? = null

    @org.junit.Before
    fun setUp() {
        originalManufacturer = Build.MANUFACTURER
    }

    @org.junit.After
    fun tearDown() {
        ReflectionHelpers.setStaticField(Build::class.java, "MANUFACTURER", originalManufacturer)
    }

        ReflectionHelpers.setStaticField(Build::class.java, "MANUFACTURER", "xiaomi")
        val context = mockk<Context>()
        val packageManager = mockk<PackageManager>()
        every { context.packageManager } returns packageManager
        
        // Mock that the intent resolves
        every { packageManager.queryIntentActivities(any(), any<Int>()) } returns listOf(mockk<ResolveInfo>())

        val intent = OemAutostartHelper.getAutostartIntent(context)
        assertNotNull(intent)
        assertEquals("com.miui.securitycenter", intent?.component?.packageName)
        assertEquals("com.miui.permcenter.autostart.AutoStartManagementActivity", intent?.component?.className)
    }

    @Test
    fun testGetAutostartIntent_WithUnsupportedManufacturer() {
        ReflectionHelpers.setStaticField(Build::class.java, "MANUFACTURER", "unknown_brand")
        val context = mockk<Context>()
        
        val intent = OemAutostartHelper.getAutostartIntent(context)
        assertNull(intent)
    }
}
    }

    @Test
    fun testGetAutostartIntent_WithUnsupportedManufacturer() {
        ReflectionHelpers.setStaticField(Build::class.java, "MANUFACTURER", "unknown_brand")
        val context = mockk<Context>()
        
        val intent = OemAutostartHelper.getAutostartIntent(context)
        assertNull(intent)
    }
}
