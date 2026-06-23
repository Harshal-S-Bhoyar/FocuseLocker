package com.focuslock.domain.usecase

import android.content.Context
import android.content.SharedPreferences
import com.focuslock.domain.model.DoHResult
import com.focuslock.data.repository.BlockingRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class DoHQueryUseCaseTest {

    private lateinit var blockingRepository: BlockingRepository
    private lateinit var context: Context
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var okHttpClient: OkHttpClient
    private lateinit var useCase: DoHQueryUseCase

    @Before
    fun setup() {
        blockingRepository = mockk(relaxed = true)
        context = mockk(relaxed = true)
        sharedPreferences = mockk(relaxed = true)
        okHttpClient = mockk(relaxed = true)

        every { context.getSharedPreferences(any(), any()) } returns sharedPreferences
        every { sharedPreferences.getString("NEXTDNS_PROFILE_ID", any()) } returns "71421a"

        val builder = mockk<OkHttpClient.Builder>(relaxed = true)
        every { okHttpClient.newBuilder() } returns builder
        every { builder.dns(any()) } returns builder
        every { builder.connectionPool(any()) } returns builder
        every { builder.connectTimeout(any(), any()) } returns builder
        every { builder.readTimeout(any(), any()) } returns builder
        every { builder.writeTimeout(any(), any()) } returns builder
        every { builder.proxy(any()) } returns builder
        every { builder.build() } returns okHttpClient

        useCase = DoHQueryUseCase(blockingRepository, context, okHttpClient)
    }

    private fun mockHttpResponse(body: String) {
        val call = mockk<Call>()
        val request = Request.Builder().url("https://dns.nextdns.io/71421a").build()
        val response = Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(body.toResponseBody())
            .build()
        every { okHttpClient.newCall(any()) } returns call
        every { call.execute() } returns response
    }

    @Test
    fun `test NXDOMAIN status 3 returns Blocked`() = runTest {
        val json = """
            {
              "Status": 3,
              "TC": false,
              "RD": true,
              "RA": true,
              "AD": false,
              "CD": false,
              "Question": [
                {
                  "name": "blocked.com.",
                  "type": 1
                }
              ]
            }
        """.trimIndent()
        mockHttpResponse(json)

        val result = useCase.queryDoH("blocked.com")
        assertEquals(DoHResult.Blocked, result)
    }

    @Test
    fun `test status 0 with 0_0_0_0 answer returns Blocked`() = runTest {
        val json = """
            {
              "Status": 0,
              "Answer": [
                {
                  "name": "blocked.com.",
                  "type": 1,
                  "data": "0.0.0.0"
                }
              ]
            }
        """.trimIndent()
        mockHttpResponse(json)

        val result = useCase.queryDoH("blocked.com")
        assertEquals(DoHResult.Blocked, result)
    }
    
    @Test
    fun `test status 0 with IPv6 answer returns Blocked`() = runTest {
        val json = """
            {
              "Status": 0,
              "Answer": [
                {
                  "name": "blocked.com.",
                  "type": 28,
                  "data": "::"
                }
              ]
            }
        """.trimIndent()
        mockHttpResponse(json)

        val result = useCase.queryDoH("blocked.com")
        assertEquals(DoHResult.Blocked, result)
    }

    @Test
    fun `test status 0 with normal IP returns Clean`() = runTest {
        val json = """
            {
              "Status": 0,
              "Answer": [
                {
                  "name": "google.com.",
                  "type": 1,
                  "data": "142.250.190.46"
                }
              ]
            }
        """.trimIndent()
        mockHttpResponse(json)

        val result = useCase.queryDoH("google.com")
        assertEquals(DoHResult.Clean, result)
    }

    @Test
    fun `test HTTP 429 returns QueryFailed`() = runTest {
        val call = mockk<Call>()
        val request = Request.Builder().url("https://dns.nextdns.io/71421a").build()
        val response = Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(429)
            .message("Too Many Requests")
            .body("".toResponseBody())
            .build()
        every { okHttpClient.newCall(any()) } returns call
        every { call.execute() } returns response

        val result = useCase.queryDoH("example.com")
        assertEquals(DoHResult.QueryFailed, result)
    }

    @Test
    fun `test malformed JSON returns QueryFailed`() = runTest {
        mockHttpResponse("{ invalid json ]")

        val result = useCase.queryDoH("example.com")
        assertEquals(DoHResult.QueryFailed, result)
    }

    @Test
    fun `test unknown status returns Unknown`() = runTest {
        val json = """
            {
              "Status": 5,
              "Answer": []
            }
        """.trimIndent()
        mockHttpResponse(json)

        val result = useCase.queryDoH("example.com")
        assertEquals(DoHResult.Unknown, result)
    }

    @Test
    fun `test network exception returns QueryFailed`() = runTest {
        val call = mockk<Call>()
        every { okHttpClient.newCall(any()) } returns call
        every { call.execute() } throws java.io.IOException("Network down")

        val result = useCase.queryDoH("example.com")
        assertEquals(DoHResult.QueryFailed, result)
    }

    @Test
    fun `checkAndCacheDoH returns cached PERMANENT without network call`() = runTest {
        io.mockk.coEvery { blockingRepository.getCachedDomainStatus("blocked.com") } returns "PERMANENT"

        val result = useCase.checkAndCacheDoH("blocked.com")
        assertEquals(DoHResult.Blocked, result)
        io.mockk.verify(exactly = 0) { okHttpClient.newCall(any()) }
    }

    @Test
    fun `checkAndCacheDoH returns cached CLEAN_CACHE without network call`() = runTest {
        io.mockk.coEvery { blockingRepository.getCachedDomainStatus("google.com") } returns "CLEAN_CACHE"

        val result = useCase.checkAndCacheDoH("google.com")
        assertEquals(DoHResult.Clean, result)
        io.mockk.verify(exactly = 0) { okHttpClient.newCall(any()) }
    }

    @Test
    fun `checkAndCacheDoH queries network and caches Blocked result`() = runTest {
        io.mockk.coEvery { blockingRepository.getCachedDomainStatus("blocked.com") } returns null
        val json = """{"Status": 3, "Question": [{"name": "blocked.com.", "type": 1}]}"""
        mockHttpResponse(json)

        val result = useCase.checkAndCacheDoH("blocked.com")
        assertEquals(DoHResult.Blocked, result)
        io.mockk.coVerify { blockingRepository.addAutoBlockedDomain("blocked.com", "", "PERMANENT", null) }
    }

    @Test
    fun `checkAndCacheDoH queries network and caches Clean result`() = runTest {
        io.mockk.coEvery { blockingRepository.getCachedDomainStatus("google.com") } returns null
        val json = """{"Status": 0, "Answer": [{"name": "google.com.", "type": 1, "data": "1.2.3.4"}]}"""
        mockHttpResponse(json)

        val result = useCase.checkAndCacheDoH("google.com")
        assertEquals(DoHResult.Clean, result)
        io.mockk.coVerify { blockingRepository.addAutoBlockedDomain("google.com", "", "CLEAN_CACHE", any()) }
    }

    @Test
    fun `checkAndCacheDoH queries network but does not cache QueryFailed`() = runTest {
        io.mockk.coEvery { blockingRepository.getCachedDomainStatus("example.com") } returns null
        val call = mockk<Call>()
        every { okHttpClient.newCall(any()) } returns call
        every { call.execute() } throws java.io.IOException("Timeout")

        val result = useCase.checkAndCacheDoH("example.com")
        assertEquals(DoHResult.QueryFailed, result)
        io.mockk.coVerify(exactly = 0) { blockingRepository.addAutoBlockedDomain(any(), any(), any(), any()) }
    }
}
