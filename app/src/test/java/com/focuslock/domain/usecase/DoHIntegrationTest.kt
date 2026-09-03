package com.focuslock.domain.usecase

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.focuslock.data.local.FocusDatabase
import com.focuslock.data.repository.BlockingRepository
import com.focuslock.domain.model.DoHResult
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import com.focuslock.domain.repository.ConfigRepository
import com.focuslock.data.repository.DoHRepositoryImpl
import io.mockk.every
import io.mockk.coEvery
import io.mockk.verify

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class DoHIntegrationTest {

    private lateinit var db: FocusDatabase
    private lateinit var repository: BlockingRepository
    private lateinit var useCase: DoHQueryUseCase
    private lateinit var configRepository: ConfigRepository
    private val okHttpClient: OkHttpClient = mockk()

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(
            context, FocusDatabase::class.java
        ).build()
        
        repository = BlockingRepository(
            keywordDao = db.blockedKeywordDao(),
            categoryDao = db.blockedCategoryDao(),
            domainDao = db.blockedDomainDao(),
            context = context
        )

        val mockBuilder = mockk<OkHttpClient.Builder>(relaxed = true)
        every { okHttpClient.newBuilder() } returns mockBuilder
        every { mockBuilder.connectionPool(any()) } returns mockBuilder
        every { mockBuilder.dns(any()) } returns mockBuilder
        every { mockBuilder.connectTimeout(any(), any()) } returns mockBuilder
        every { mockBuilder.readTimeout(any(), any()) } returns mockBuilder
        every { mockBuilder.writeTimeout(any(), any()) } returns mockBuilder
        every { mockBuilder.proxy(any()) } returns mockBuilder
        every { mockBuilder.build() } returns okHttpClient

        val prefs = context.getSharedPreferences("focuslock_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("auto_mode_enabled", true).apply()
        
        configRepository = mockk(relaxed = true)
        coEvery { configRepository.isAutoModeEnabled() } returns true
        coEvery { configRepository.getNextDnsProfileId() } returns "71421a"
        val dohRepository = DoHRepositoryImpl(configRepository, okHttpClient)

        useCase = DoHQueryUseCase(repository, configRepository, dohRepository)
        
        runBlocking {
            db.blockedCategoryDao().insertCategory(
                com.focuslock.data.local.entity.BlockedCategoryEntity(
                    categoryKey = "AUTO_BLOCKED",
                    isActive = true
                )
            )
        }
    }

    @After
    fun closeDb() {
        db.close()
    }

    private fun mockHttpResponse(jsonBody: String) {
        val mockCall = mockk<Call>()
        val mockResponse = Response.Builder()
            .request(Request.Builder().url("https://example.com").build())
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(jsonBody.toResponseBody("application/json".toMediaTypeOrNull()))
            .build()
        
        every { okHttpClient.newCall(any()) } returns mockCall
        every { mockCall.execute() } returns mockResponse
    }

    @Test
    fun testDoHQueryAndDatabasePersistence() = runBlocking {
        // 1. Initial state: Database has no cache
        assertEquals(null, repository.getCachedDomainStatus("testdomain.com"))

        // 2. Mock DoH response as Blocked
        mockHttpResponse("""{"Status": 3}""")

        // 3. checkAndCacheDoH should query network and return Blocked
        val result1 = useCase.checkAndCacheDoH("testdomain.com")
        assertEquals(DoHResult.Blocked, result1)

        // 4. Verification: Domain should now be stored in the repository as PERMANENT
        assertEquals("PERMANENT", repository.getCachedDomainStatus("testdomain.com"))

        // 5. Change network response to Clean just to prove the network isn't queried again
        mockHttpResponse("""{"Status": 0}""")

        // 6. checkAndCacheDoH should return Blocked (from cache)
        val result2 = useCase.checkAndCacheDoH("testdomain.com")
        assertEquals(DoHResult.Blocked, result2)
    }

    @Test
    fun `clean DoH response is cached and reused`() = runBlocking {
        mockHttpResponse("""{"Status": 0}""")

        val firstResult = useCase.checkAndCacheDoH("clean.example.com")
        assertEquals(DoHResult.Clean, firstResult)
        assertEquals("CLEAN_CACHE", repository.getCachedDomainStatus("clean.example.com"))

        mockHttpResponse("""{"Status": 3}""")
        val cachedResult = useCase.checkAndCacheDoH("clean.example.com")

        assertEquals(DoHResult.Clean, cachedResult)
    }

    @Test
    fun `successful DNS response containing a blocking address is cached as blocked`() = runBlocking {
        mockHttpResponse("""{"Status": 0, "Answer": [{"data": "0.0.0.0"}]}""")

        val result = useCase.checkAndCacheDoH("sink.example.com")

        assertEquals(DoHResult.Blocked, result)
        assertEquals("PERMANENT", repository.getCachedDomainStatus("sink.example.com"))
    }

    @Test
    fun `unknown DNS status is returned without creating a cache entry`() = runBlocking {
        mockHttpResponse("""{"Status": 2}""")

        val result = useCase.checkAndCacheDoH("unknown.example.com")

        assertEquals(DoHResult.Unknown, result)
        assertEquals(null, repository.getCachedDomainStatus("unknown.example.com"))
    }

    @Test
    fun `network failure is returned without creating a cache entry`() = runBlocking {
        val mockCall = mockk<Call>()
        every { okHttpClient.newCall(any()) } returns mockCall
        every { mockCall.execute() } throws java.io.IOException("network unavailable")

        val result = useCase.checkAndCacheDoH("offline.example.com")

        assertEquals(DoHResult.QueryFailed, result)
        assertEquals(null, repository.getCachedDomainStatus("offline.example.com"))
    }

    @Test
    fun `invalid URL returns query failure without querying DNS`() = runBlocking {
        val result = useCase.checkAndCacheDoH("http://[invalid")

        assertEquals(DoHResult.QueryFailed, result)
        assertEquals(null, repository.getCachedDomainStatus("http://[invalid"))
        verify(exactly = 0) { okHttpClient.newCall(any()) }
    }

    @Test
    fun `disabled auto mode returns clean without network or persistence`() = runBlocking {
        coEvery { configRepository.isAutoModeEnabled() } returns false
        mockHttpResponse("""{"Status": 3}""")

        val result = useCase.checkAndCacheDoH("disabled.example.com")

        assertEquals(DoHResult.Clean, result)
        assertEquals(null, repository.getCachedDomainStatus("disabled.example.com"))
        verify(exactly = 0) { okHttpClient.newCall(any()) }
    }
}
