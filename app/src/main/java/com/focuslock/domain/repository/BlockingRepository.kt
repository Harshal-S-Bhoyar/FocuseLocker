package com.focuslock.domain.repository

import com.focuslock.domain.model.BlockedCategory
import com.focuslock.domain.model.BlockedKeyword
import kotlinx.coroutines.flow.Flow

interface BlockingRepository {
    // Keywords
    fun getActiveKeywords(): Flow<List<BlockedKeyword>>
    fun getAllKeywords(): Flow<List<BlockedKeyword>>
    suspend fun addKeyword(keyword: String): Result<Unit>
    suspend fun removeKeyword(keywordId: Long)
    suspend fun getKeywordCount(): Int

    // Categories
    fun getAllCategories(): Flow<List<BlockedCategory>>
    fun getActiveCategories(): Flow<List<BlockedCategory>>
    suspend fun setCategoryActive(categoryKey: String, isActive: Boolean)
    suspend fun getActiveCategoryKeys(): List<String>

    // Domains (used by VPN service)
    suspend fun getActiveBlockedDomains(): List<String>
    suspend fun seedDomainDataIfNeeded()
}
