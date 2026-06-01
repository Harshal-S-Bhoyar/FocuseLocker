package com.focuslock.receiver

import org.junit.Assert.assertEquals
import org.junit.Test

class BootRecoveryDecisionTest {

    @Test
    fun `evaluate returns no session when expiry is absent`() {
        assertEquals(BootRecoveryDecision.NoSession, BootRecoveryDecision.evaluate(null, 100L))
    }

    @Test
    fun `evaluate returns recover when session has remaining time`() {
        assertEquals(BootRecoveryDecision.Recover(50L), BootRecoveryDecision.evaluate(150L, 100L))
    }

    @Test
    fun `evaluate returns complete expired when expiry is reached`() {
        assertEquals(BootRecoveryDecision.CompleteExpired, BootRecoveryDecision.evaluate(100L, 100L))
    }
}
