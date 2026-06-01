package com.focuslock.service.overlay

import org.junit.Assert.assertEquals
import org.junit.Test

class OverlayContractTest {

    @Test
    fun `show overlay contract keeps stable action and payload keys`() {
        assertEquals("com.focuslock.action.SHOW_OVERLAY", OverlayContract.ACTION_SHOW_OVERLAY)
        assertEquals("matched_term", OverlayContract.EXTRA_MATCHED_TERM)
        assertEquals("block_reason", OverlayContract.EXTRA_BLOCK_REASON)
        assertEquals("time_remaining", OverlayContract.EXTRA_TIME_REMAINING)
    }
}
