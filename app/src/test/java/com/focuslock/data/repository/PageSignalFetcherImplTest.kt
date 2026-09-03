package com.focuslock.data.repository

import com.focuslock.domain.model.FetchOutcome
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.SocketTimeoutException

@OptIn(ExperimentalCoroutinesApi::class)
class PageSignalFetcherImplTest {

    private lateinit var fetcher: PageSignalFetcherImpl
    private lateinit var mockInterceptor: MockInterceptor

    class MockInterceptor : Interceptor {
        var responseBody: String? = null
        var responseCode: Int = 200
        var shouldThrow: Exception? = null
        val requests = mutableListOf<String>()

        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            requests.add(request.url.toString())
            
            shouldThrow?.let { throw it }

            return Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(responseCode)
                .message(if (responseCode == 200) "OK" else "Error")
                .body(responseBody?.toResponseBody("text/html".toMediaType()))
                .build()
        }
    }

    @Before
    fun setup() {
        mockInterceptor = MockInterceptor()
        val client = OkHttpClient.Builder()
            .addInterceptor(mockInterceptor)
            .build()
        fetcher = PageSignalFetcherImpl(client)
    }

    @Test
    fun `test caching normalizes full URL correctly`() = runTest {
        val padding = "a".repeat(150)
        mockInterceptor.responseBody = "<html><head><meta name=\"description\" content=\"Test Description\"></head><body>$padding</body></html>"
        
        // First fetch for a path
        val outcome1 = fetcher.fetchPageSignals("https://example.com/path1")
        assertTrue(outcome1 is FetchOutcome.Success)
        assertEquals(1, mockInterceptor.requests.size)

        // Second fetch for the same path should hit cache
        val outcome2 = fetcher.fetchPageSignals("example.com/path1")
        assertTrue(outcome2 is FetchOutcome.Success)
        assertEquals(1, mockInterceptor.requests.size) // No new request
        
        // Fetch for a different path on same host should NOT hit cache (distinct page)
        val outcome3 = fetcher.fetchPageSignals("example.com/path2")
        assertTrue(outcome3 is FetchOutcome.Success)
        assertEquals(2, mockInterceptor.requests.size) // New request made
    }

    @Test
    fun `test fetch failure does not cache empty result`() = runTest {
        mockInterceptor.shouldThrow = SocketTimeoutException("Timeout")

        // First fetch fails
        val outcome1 = fetcher.fetchPageSignals("https://example.com/error")
        assertTrue(outcome1 is FetchOutcome.FetchError || outcome1 is FetchOutcome.FetchTimeout)
        
        // Fix mock to return success
        val padding = "a".repeat(150)
        mockInterceptor.shouldThrow = null
        mockInterceptor.responseBody = "<html><head><meta name=\"description\" content=\"Success\"></head><body>$padding</body></html>"
        
        // Second fetch should attempt network again (not hit empty cache)
        val outcome2 = fetcher.fetchPageSignals("https://example.com/error")
        assertTrue(outcome2 is FetchOutcome.Success)
        assertEquals("Success", (outcome2 as FetchOutcome.Success).signals["meta_description"])
    }

    @Test
    fun `test short successful body returns empty and is cached`() = runTest {
        mockInterceptor.responseBody = "<html><body>short</body></html>"

        val first = fetcher.fetchPageSignals("https://example.com/short")
        assertTrue(first is FetchOutcome.FetchEmpty)
        assertEquals(1, mockInterceptor.requests.size)

        val second = fetcher.fetchPageSignals("https://example.com/short")
        assertTrue(second is FetchOutcome.FetchEmpty)
        assertEquals(1, mockInterceptor.requests.size)
    }

    @Test
    fun `test non-success HTTP response is returned as fetch error`() = runTest {
        mockInterceptor.responseCode = 503
        mockInterceptor.responseBody = "service unavailable"

        val outcome = fetcher.fetchPageSignals("https://example.com/unavailable")

        assertTrue(outcome is FetchOutcome.FetchError)
        assertTrue((outcome as FetchOutcome.FetchError).reason.contains("HTTP error: 503"))
    }

    @Test
    fun `test invalid URL is rejected before network access`() = runTest {
        val outcome = fetcher.fetchPageSignals("https://[invalid")

        assertTrue(outcome is FetchOutcome.FetchError)
        assertEquals(0, mockInterceptor.requests.size)
    }

    @Test
    fun `test private address is blocked by SSRF protection`() = runTest {
        val outcome = fetcher.fetchPageSignals("http://127.0.0.1/private")

        assertTrue(outcome is FetchOutcome.FetchError)
        assertTrue((outcome as FetchOutcome.FetchError).reason.contains("private address"))
        assertEquals(0, mockInterceptor.requests.size)
    }
}
