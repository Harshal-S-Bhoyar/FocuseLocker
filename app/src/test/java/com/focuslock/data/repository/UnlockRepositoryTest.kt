package com.focuslock.data.repository

import com.focuslock.data.local.dao.UnlockRequestDao
import com.focuslock.data.local.entity.UnlockRequestEntity
import com.focuslock.data.local.entity.UnlockRequestStatus as EntityStatus
import com.focuslock.domain.model.UnlockRequestStatus
import com.focuslock.data.remote.api.FocusLockApi
import com.focuslock.data.remote.dto.RequestUnlockRequest
import com.focuslock.data.remote.dto.RequestUnlockResponse
import com.focuslock.data.remote.dto.VerifyUnlockOtpRequest
import com.focuslock.data.remote.dto.VerifyUnlockOtpResponse
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class UnlockRepositoryTest {

    private lateinit var unlockRequestDao: UnlockRequestDao
    private lateinit var api: FocusLockApi
    private lateinit var repository: UnlockRepository

    @Before
    fun setup() {
        unlockRequestDao = mockk(relaxed = true)
        api = mockk(relaxed = true)
        repository = UnlockRepository(unlockRequestDao, api)
    }

    @Test
    fun `getLatestRequest maps correctly`() = runTest {
        val entity = UnlockRequestEntity(1L, 100L, 5000L, EntityStatus.PENDING, 2, 0L)
        every { unlockRequestDao.getLatestRequestBySession(100L) } returns flowOf(entity)

        val request = repository.getLatestRequest(100L).first()
        assertEquals(1L, request?.id)
        assertEquals(UnlockRequestStatus.PENDING, request?.status)
        assertEquals(2, request?.otpAttempts)
    }

    @Test
    fun `createRequest inserts correctly initialized entity`() = runTest {
        coEvery { unlockRequestDao.insertRequest(any()) } returns 5L

        val id = repository.createRequest(100L)

        assertEquals(5L, id)
        coVerify { 
            unlockRequestDao.insertRequest(withArg { 
                assertEquals(100L, it.sessionId)
                assertEquals(EntityStatus.PENDING, it.status)
                assertEquals(0, it.otpAttempts)
                assertEquals(null, it.cooldownEndsAt)
            }) 
        }
    }

    @Test
    fun `updateStatus maps and updates correctly`() = runTest {
        repository.updateStatus(1L, UnlockRequestStatus.OTP_SENT)
        coVerify { unlockRequestDao.updateRequestStatus(1L, EntityStatus.OTP_SENT) }
    }

    @Test
    fun `incrementOtpAttempts increments and retrieves count`() = runTest {
        coEvery { unlockRequestDao.getOtpAttempts(1L) } returns 3

        val attempts = repository.incrementOtpAttempts(1L)

        assertEquals(3, attempts)
        coVerify { unlockRequestDao.incrementOtpAttempts(1L) }
    }

    @Test
    fun `setCooldownEndsAt updates timestamp`() = runTest {
        repository.setCooldownEndsAt(1L, 9999L)
        coVerify { unlockRequestDao.updateCooldownEndsAt(1L, 9999L) }
    }

    @Test
    fun `getLatestRequestSync maps correctly`() = runTest {
        val entity = UnlockRequestEntity(1L, 100L, 5000L, EntityStatus.COMPLETED, 0, null)
        coEvery { unlockRequestDao.getLatestRequestBySessionSync(100L) } returns entity

        val request = repository.getLatestRequestSync(100L)
        assertEquals(UnlockRequestStatus.COMPLETED, request?.status)
    }

    @Test
    fun `verifyOtp returns success on valid api response`() = runTest {
        coEvery { api.verifyUnlockOtp(any()) } returns VerifyUnlockOtpResponse(true)
        
        val result = repository.verifyOtp("test@example.com", "123456")
        
        assertTrue(result.isSuccess)
        assertEquals(true, result.getOrNull())
        coVerify { 
            api.verifyUnlockOtp(VerifyUnlockOtpRequest("test@example.com", "123456"))
        }
    }

    @Test
    fun `verifyOtp returns failure on api exception`() = runTest {
        coEvery { api.verifyUnlockOtp(any()) } throws RuntimeException("Network Error")
        
        val result = repository.verifyOtp("test@example.com", "123456")
        
        assertTrue(result.isFailure)
        assertEquals("Network Error", result.exceptionOrNull()?.message)
    }

    @Test
    fun `requestUnlock returns success on valid api response`() = runTest {
        coEvery { api.requestUnlock(any()) } returns RequestUnlockResponse(true)
        
        val result = repository.requestUnlock(
            "test@example.com", 
            listOf("SOCIAL"), 
            listOf("games"), 
            "2 hours"
        )
        
        assertTrue(result.isSuccess)
        assertEquals(true, result.getOrNull())
        
        coVerify { 
            api.requestUnlock(withArg { 
                assertEquals("test@example.com", it.trustedContactEmail)
                assertEquals(listOf("SOCIAL"), it.blockedCategories)
                assertEquals(listOf("games"), it.blockedKeywords)
                assertEquals("2 hours", it.timeRemaining)
            }) 
        }
    }

    @Test
    fun `requestUnlock returns failure on api exception`() = runTest {
        coEvery { api.requestUnlock(any()) } throws RuntimeException("Server Error")
        
        val result = repository.requestUnlock(
            "test@example.com", 
            emptyList(), 
            emptyList(), 
            "1 hour"
        )
        
        assertTrue(result.isFailure)
        assertEquals("Server Error", result.exceptionOrNull()?.message)
    }
}
