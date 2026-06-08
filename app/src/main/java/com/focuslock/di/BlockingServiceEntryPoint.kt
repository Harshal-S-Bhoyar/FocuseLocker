package com.focuslock.di

import com.focuslock.domain.repository.SessionRepository
import com.focuslock.domain.usecase.CheckUrlForBlockedContentUseCase
import com.focuslock.domain.usecase.GetActiveBlockedDomainsUseCase
import com.focuslock.domain.usecase.GetAllBlockedDomainsFlowUseCase
import com.focuslock.domain.usecase.GetActiveKeywordsUseCase
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface BlockingServiceEntryPoint {
    fun checkUrlForBlockedContentUseCase(): CheckUrlForBlockedContentUseCase
    fun getActiveKeywordsUseCase(): GetActiveKeywordsUseCase
    fun getActiveBlockedDomainsUseCase(): GetActiveBlockedDomainsUseCase
    fun getAllBlockedDomainsFlowUseCase(): GetAllBlockedDomainsFlowUseCase
    fun sessionRepository(): SessionRepository
    fun smartScanUseCase(): com.focuslock.domain.usecase.SmartScanUseCase
}
