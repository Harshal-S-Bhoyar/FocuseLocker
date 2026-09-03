package com.focuslock.worker

import org.junit.Assert.assertEquals
import org.junit.Test

class SessionWorkerDecisionTest {

    @Test
    fun `evaluate returns no active session when session is absent`() {
        assertEquals(SessionWorkerDecision.NoActiveSession, SessionWorkerDecision.evaluate(null, 100L))
    }

    @Test
    fun `evaluate returns expired when expiry is not after current time`() {
        assertEquals(SessionWorkerDecision.Expired, SessionWorkerDecision.evaluate(100L, 100L))
    }

    @Test
    fun `evaluate returns remaining duration for active session`() {
        assertEquals(SessionWorkerDecision.Active(50L), SessionWorkerDecision.evaluate(150L, 100L))
    }

    @Test
    fun `evaluate returns expired when current time is after expiry`() {
        assertEquals(SessionWorkerDecision.Expired, SessionWorkerDecision.evaluate(99L, 100L))
    }

    @Test
    fun `evaluate preserves a one millisecond active remainder`() {
        assertEquals(SessionWorkerDecision.Active(1L), SessionWorkerDecision.evaluate(101L, 100L))
    }
}
