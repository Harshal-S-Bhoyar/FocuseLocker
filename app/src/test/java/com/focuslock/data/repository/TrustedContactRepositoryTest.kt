package com.focuslock.data.repository

import com.focuslock.data.local.dao.TrustedContactDao
import com.focuslock.data.local.entity.TrustedContactEntity
import com.focuslock.data.remote.api.FocusLockApi
import com.focuslock.data.remote.dto.ConfirmContactRequest
import com.focuslock.data.remote.dto.ConfirmContactResponse
import com.focuslock.data.remote.dto.VerifyContactRequest
import com.focuslock.data.remote.dto.VerifyContactResponse
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TrustedContactRepositoryTest {

    private lateinit var trustedContactDao: TrustedContactDao
    private lateinit var api: FocusLockApi
    private lateinit var repository: TrustedContactRepository

    @Before
    fun setup() {
        trustedContactDao = mockk(relaxed = true)
        api = mockk(relaxed = true)
        repository = TrustedContactRepository(trustedContactDao, api)
    }

    @Test
    fun `getContact maps correctly`() = runTest {
        val entity = TrustedContactEntity(1L, "test@example.com", true, 1000L, 500L)
        every { trustedContactDao.getContact() } returns flowOf(entity)

        val contact = repository.getContact().first()
        assertEquals(1L, contact?.id)
        assertEquals("test@example.com", contact?.email)
        assertEquals(true, contact?.isVerified)
    }

    @Test
    fun `getVerifiedContact maps correctly`() = runTest {
        val entity = TrustedContactEntity(1L, "verified@example.com", true, 1000L, 500L)
        every { trustedContactDao.getVerifiedContact() } returns flowOf(entity)

        val contact = repository.getVerifiedContact().first()
        assertEquals(1L, contact?.id)
        assertEquals("verified@example.com", contact?.email)
    }

    @Test
    fun `saveContact deletes old contacts and inserts new one`() = runTest {
        coEvery { trustedContactDao.insertContact(any()) } returns 5L

        val id = repository.saveContact("new@example.com")

        assertEquals(5L, id)
        coVerify(exactly = 1) { trustedContactDao.deleteAllContacts() }
        coVerify { 
            trustedContactDao.insertContact(withArg { 
                assertEquals("new@example.com", it.email)
                assertFalse(it.isVerified)
            }) 
        }
    }

    @Test
    fun `markVerified updates status`() = runTest {
        repository.markVerified(1L)
        coVerify { trustedContactDao.updateVerificationStatus(eq(1L), eq(true), any()) }
    }

    @Test
    fun `hasVerifiedContact returns true when verified`() = runTest {
        val entity = TrustedContactEntity(1L, "test@example.com", true, 1000L, 500L)
        coEvery { trustedContactDao.getContactSync() } returns entity

        assertTrue(repository.hasVerifiedContact())
    }

    @Test
    fun `hasVerifiedContact returns false when not verified`() = runTest {
        val entity = TrustedContactEntity(1L, "test@example.com", false, null, 500L)
        coEvery { trustedContactDao.getContactSync() } returns entity

        assertFalse(repository.hasVerifiedContact())
    }

    @Test
    fun `sendVerificationOtp returns api response success`() = runTest {
        coEvery { api.verifyContact(VerifyContactRequest("test@example.com")) } returns VerifyContactResponse(true)
        assertTrue(repository.sendVerificationOtp("test@example.com"))

        coEvery { api.verifyContact(VerifyContactRequest("fail@example.com")) } returns VerifyContactResponse(false)
        assertFalse(repository.sendVerificationOtp("fail@example.com"))
    }

    @Test
    fun `confirmOtp returns api response verified`() = runTest {
        coEvery { api.confirmContact(ConfirmContactRequest("test@example.com", "123456")) } returns ConfirmContactResponse(true)
        assertTrue(repository.confirmOtp("test@example.com", "123456"))

        coEvery { api.confirmContact(ConfirmContactRequest("fail@example.com", "000000")) } returns ConfirmContactResponse(false)
        assertFalse(repository.confirmOtp("fail@example.com", "000000"))
    }
}
