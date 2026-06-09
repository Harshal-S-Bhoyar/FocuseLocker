package com.focuslock.data.repository

import android.content.Context
import com.focuslock.data.local.dao.BlockedCategoryDao
import com.focuslock.data.local.dao.BlockedDomainDao
import com.focuslock.data.local.dao.BlockedKeywordDao
import com.focuslock.data.local.entity.BlockedCategoryEntity
import com.focuslock.data.local.entity.BlockedDomainEntity
import com.focuslock.data.local.entity.BlockedKeywordEntity
import com.focuslock.data.local.entity.CategoryKey
import com.focuslock.domain.model.BlockedCategory
import com.focuslock.domain.model.BlockedKeyword
import com.focuslock.domain.repository.BlockingRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

private const val MAX_KEYWORDS = 10

@Singleton
class BlockingRepositoryImpl @Inject constructor(
    private val keywordDao: BlockedKeywordDao,
    private val categoryDao: BlockedCategoryDao,
    private val domainDao: BlockedDomainDao,
    @ApplicationContext private val context: Context
) : BlockingRepository {

    // ── Keywords ──────────────────────────────────────────────────────────────

    override fun getActiveKeywords(): Flow<List<BlockedKeyword>> {
        return keywordDao.getActiveKeywords().map { list -> list.map { it.toDomain() } }
    }

    override fun getAllKeywords(): Flow<List<BlockedKeyword>> {
        return keywordDao.getAllKeywords().map { list -> list.map { it.toDomain() } }
    }

    override suspend fun addKeyword(keyword: String): Result<Unit> {
        val normalized = keyword.trim().lowercase()
        if (normalized.isBlank()) return Result.failure(IllegalArgumentException("Keyword cannot be blank"))
        if (keywordDao.getKeywordCount() >= MAX_KEYWORDS) {
            return Result.failure(IllegalStateException("Maximum $MAX_KEYWORDS keywords reached"))
        }
        if (keywordDao.keywordExists(normalized)) {
            return Result.failure(IllegalArgumentException("Keyword already exists"))
        }
        keywordDao.insertKeyword(
            BlockedKeywordEntity(
                keyword = normalized,
                isActive = true,
                createdAt = System.currentTimeMillis()
            )
        )
        return Result.success(Unit)
    }

    override suspend fun removeKeyword(keywordId: Long) {
        keywordDao.deleteKeyword(keywordId)
    }

    override suspend fun getKeywordCount(): Int = keywordDao.getKeywordCount()

    // ── Categories ────────────────────────────────────────────────────────────

    override fun getAllCategories(): Flow<List<BlockedCategory>> {
        return categoryDao.getAllCategories().map { list -> list.map { it.toDomain() } }
    }

    override fun getActiveCategories(): Flow<List<BlockedCategory>> {
        return categoryDao.getActiveCategories().map { list -> list.map { it.toDomain() } }
    }

    override suspend fun setCategoryActive(categoryKey: String, isActive: Boolean) {
        categoryDao.updateCategoryStatus(categoryKey, isActive)
    }

    override suspend fun getActiveCategoryKeys(): List<String> {
        return categoryDao.getActiveCategoryKeys()
    }

    // ── Domains ───────────────────────────────────────────────────────────────

    override suspend fun getActiveBlockedDomains(): List<String> {
        return domainDao.getActiveBlockedDomainList()
    }

    override suspend fun getAllBlockedDomains(): List<String> {
        return domainDao.getAllBlockedDomainList()
    }

    override fun getAllBlockedDomainsFlow(): Flow<List<String>> {
        return domainDao.getAllBlockedDomainListFlow()
    }

    override suspend fun addAutoBlockedDomain(domain: String, keyword: String) {
        if (domainDao.countByDomain(domain) == 0) {
            domainDao.insertDomain(
                BlockedDomainEntity(
                    domain = domain,
                    categoryKey = "AUTO_BLOCKED", // We will add this to CategoryKey if needed, but strings work.
                    isAutoBlocked = true,
                    matchedKeyword = keyword,
                    autoBlockedAt = System.currentTimeMillis()
                )
            )
        }
    }

    override suspend fun seedDomainDataIfNeeded() {
        // Ensure all category rows exist. IGNORE means existing rows keep user prefs.
        categoryDao.insertCategories(
            CategoryKey.ALL.map { key ->
                BlockedCategoryEntity(
                    categoryKey = key,
                    isActive = true
                )
            }
        )

        // AUTO_BLOCKED must always be active for dynamic blocking to work
        categoryDao.ensureAutoBlockedActive()

        // Seed domains from bundled JSON
        loadDomainsFromAssets()
    }

    private suspend fun loadDomainsFromAssets() {
        try {
            val json = context.assets.open("blocked_domains.json")
                .bufferedReader()
                .use { it.readText() }

            val domainList = Json.decodeFromString<List<DomainEntry>>(json)
            val entities = domainList.map { entry ->
                BlockedDomainEntity(domain = entry.domain, categoryKey = entry.category)
            }
            // Insert in batches of 500 to avoid SQLite limits
            entities.chunked(500).forEach { batch ->
                domainDao.insertDomains(batch)
            }
        } catch (e: Exception) {
            // Assets file missing — app still works with keyword-only blocking
        }
    }

    // ── Mappers ───────────────────────────────────────────────────────────────

    private fun BlockedKeywordEntity.toDomain() = BlockedKeyword(
        id = id,
        keyword = keyword,
        isActive = isActive,
        createdAt = createdAt
    )

    private fun BlockedCategoryEntity.toDomain() = BlockedCategory(
        id = id,
        categoryKey = categoryKey,
        isActive = isActive
    )
}

@Serializable
private data class DomainEntry(
    val domain: String,
    val category: String
)
