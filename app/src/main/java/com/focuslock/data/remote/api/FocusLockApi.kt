package com.focuslock.data.remote.api

import com.focuslock.data.remote.dto.ConfirmContactRequest
import com.focuslock.data.remote.dto.ConfirmContactResponse
import com.focuslock.data.remote.dto.RequestUnlockRequest
import com.focuslock.data.remote.dto.RequestUnlockResponse
import com.focuslock.data.remote.dto.VerifyContactRequest
import com.focuslock.data.remote.dto.VerifyContactResponse
import com.focuslock.data.remote.dto.VerifyUnlockOtpRequest
import com.focuslock.data.remote.dto.VerifyUnlockOtpResponse
import retrofit2.http.Body
import retrofit2.http.POST

interface FocusLockApi {
    
    @POST("/api/verify-contact")
    suspend fun verifyContact(
        @Body request: VerifyContactRequest
    ): VerifyContactResponse
    
    @POST("/api/confirm-contact")
    suspend fun confirmContact(
        @Body request: ConfirmContactRequest
    ): ConfirmContactResponse
    
    @POST("/api/request-unlock")
    suspend fun requestUnlock(
        @Body request: RequestUnlockRequest
    ): RequestUnlockResponse
    
    @POST("/api/verify-unlock-otp")
    suspend fun verifyUnlockOtp(
        @Body request: VerifyUnlockOtpRequest
    ): VerifyUnlockOtpResponse
}
