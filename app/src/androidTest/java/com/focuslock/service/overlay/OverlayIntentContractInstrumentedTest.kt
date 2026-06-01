package com.focuslock.service.overlay

import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OverlayIntentContractInstrumentedTest {

    @Test
    fun showIntentCarriesStableOverlayPayload() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val intent = Intent(context, BlockingOverlayService::class.java).apply {
            action = OverlayContract.ACTION_SHOW_OVERLAY
            putExtra(OverlayContract.EXTRA_MATCHED_TERM, "example")
            putExtra(OverlayContract.EXTRA_BLOCK_REASON, "DOMAIN_BLOCKED")
            putExtra(OverlayContract.EXTRA_TIME_REMAINING, 1000L)
        }

        assertEquals(OverlayContract.ACTION_SHOW_OVERLAY, intent.action)
        assertEquals("example", intent.getStringExtra(OverlayContract.EXTRA_MATCHED_TERM))
        assertEquals("DOMAIN_BLOCKED", intent.getStringExtra(OverlayContract.EXTRA_BLOCK_REASON))
        assertEquals(1000L, intent.getLongExtra(OverlayContract.EXTRA_TIME_REMAINING, 0L))
    }
}
