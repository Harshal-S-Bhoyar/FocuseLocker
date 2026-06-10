package com.focuslock.domain.usecase

import com.focuslock.domain.model.BlockCheckResult
import com.focuslock.domain.model.BlockReason
import org.junit.Assert.assertEquals
import org.junit.Test

class CheckUrlForBlockedContentUseCaseTest {

    private val useCase = CheckUrlForBlockedContentUseCase()

    @Test
    fun `invoke with blocked keyword in path returns BLOCKED`() {
        val result = useCase(
            url = "https://example.com/gambling-tips.html",
            keywords = listOf("gambling", "social"),
            domains = listOf("facebook.com")
        )
        
        assertEquals(BlockCheckResult.Blocked("gambling", BlockReason.KEYWORD_MATCH), result)
    }

    @Test
    fun `invoke with blocked keyword in query param returns ALLOWED`() {
        val result = useCase(
            url = "https://google.com/search?q=free+GAMBLING+sites",
            keywords = listOf("gambling"),
            domains = emptyList()
        )
        
        assertEquals(BlockCheckResult.Allowed, result)
    }

    @Test
    fun `invoke with blocked domain returns BLOCKED`() {
        val result = useCase(
            url = "https://m.facebook.com/profile",
            keywords = emptyList(),
            domains = listOf("facebook.com", "instagram.com")
        )
        
        assertEquals(BlockCheckResult.Blocked("facebook.com", BlockReason.DOMAIN_BLOCKED), result)
    }

    @Test
    fun `invoke with clean url returns ALLOWED`() {
        val result = useCase(
            url = "https://wikipedia.org/wiki/Kotlin",
            keywords = listOf("gambling"),
            domains = listOf("facebook.com")
        )
        
        assertEquals(BlockCheckResult.Allowed, result)
    }
    
    @Test
    fun `invoke is case insensitive`() {
        val result = useCase(
            url = "https://example.com/GAMBLING",
            keywords = listOf("gambling"),
            domains = emptyList()
        )
        
        assertEquals(BlockCheckResult.Blocked("gambling", BlockReason.KEYWORD_MATCH), result)
    }

    @Test
    fun `invoke matches domain exactly or subdomains`() {
        val result1 = useCase("https://api.facebook.com/v1", emptyList(), listOf("facebook.com"))
        assertEquals(BlockCheckResult.Blocked("facebook.com", BlockReason.DOMAIN_BLOCKED), result1)
        
        val result2 = useCase("https://notfacebook.com", emptyList(), listOf("facebook.com"))
        assertEquals(BlockCheckResult.Allowed, result2)
    }

    @Test
    fun `invoke matches domain without schema`() {
        val result = useCase("facebook.com", emptyList(), listOf("facebook.com"))
        assertEquals(BlockCheckResult.Blocked("facebook.com", BlockReason.DOMAIN_BLOCKED), result)
    }
}
