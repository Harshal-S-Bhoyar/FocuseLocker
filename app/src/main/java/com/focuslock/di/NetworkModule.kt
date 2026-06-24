package com.focuslock.di

import com.focuslock.BuildConfig
import com.focuslock.data.remote.api.FocusLockApi
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import okhttp3.Cache
import java.io.File
import okhttp3.ConnectionPool

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    
    @Provides
    @Singleton
    fun provideJson(): Json {
        return Json {
            ignoreUnknownKeys = true
            isLenient = true
            encodeDefaults = true
        }
    }
    
    @Provides
    @Singleton
    fun provideOkHttpClient(@ApplicationContext context: Context): OkHttpClient {
        val cacheSize = 5L * 1024 * 1024 // 5 MB
        val cache = Cache(File(context.cacheDir, "okhttp_cache"), cacheSize)

        // ponytail: shared client is ONLY for backend API, not DoH. BootstrapDns(null) here
        // caused all API-host DNS lookups to send unprotected UDP to 8.8.8.8 which the TUN
        // intercepted, creating a recursion loop. Use system DNS for the API client.
        return OkHttpClient.Builder()
            .connectionPool(ConnectionPool(10, 10, TimeUnit.MINUTES))
            .cache(cache)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
            .proxy(java.net.Proxy.NO_PROXY)
            .addInterceptor { chain ->
                var request = chain.request()
                var response = chain.proceed(request)
                var redirectCount = 0

                while (response.isRedirect) {
                    redirectCount++
                    if (redirectCount > 3) {
                        throw java.io.IOException("Too many redirects: $redirectCount")
                    }
                    val location = response.header("Location") ?: break
                    val redirectUrl = request.url.resolve(location) ?: break
                    response.close()
                    request = request.newBuilder().url(redirectUrl).build()
                    response = chain.proceed(request)
                }
                response
            }
            .addInterceptor(
                HttpLoggingInterceptor().apply {
                    level = if (BuildConfig.DEBUG) {
                        HttpLoggingInterceptor.Level.BODY
                    } else {
                        HttpLoggingInterceptor.Level.NONE
                    }
                }
            )
            .build()
    }
    
    @Provides
    @Singleton
    fun provideRetrofit(
        okHttpClient: OkHttpClient,
        json: Json
    ): Retrofit {
        val contentType = "application/json".toMediaType()
        return Retrofit.Builder()
            .baseUrl(BuildConfig.BACKEND_URL)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
    }
    
    @Provides
    @Singleton
    fun provideFocusLockApi(retrofit: Retrofit): FocusLockApi {
        return retrofit.create(FocusLockApi::class.java)
    }
}
