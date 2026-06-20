package com.focuslock.service.vpn

import org.junit.Assert.assertEquals
import org.junit.Test

class DnsConfigTest {

    @Test
    fun `default config exposes virtual IPs`() {
        assertEquals(com.focuslock.BuildConfig.VIRTUAL_IPV4, DnsConfig.DEFAULT.virtualIpv4)
        assertEquals(com.focuslock.BuildConfig.VIRTUAL_IPV6, DnsConfig.DEFAULT.virtualIpv6)
    }

    @Test
    fun `fromContext resolves profile ID from SharedPreferences`() {
        val mockContext = io.mockk.mockk<android.content.Context>(relaxed = true)
        val mockPrefs = io.mockk.mockk<android.content.SharedPreferences>(relaxed = true)
        
        io.mockk.every { mockContext.getSharedPreferences("focuslock_prefs", android.content.Context.MODE_PRIVATE) } returns mockPrefs
        io.mockk.every { mockPrefs.getString("NEXTDNS_PROFILE_ID", any()) } returns "mock-id-123"

        val config = DnsConfig.fromContext(mockContext)

        assertEquals("${com.focuslock.BuildConfig.NEXTDNS_BASE_URL}mock-id-123", config.dohEndpoint)
        assertEquals(com.focuslock.BuildConfig.VIRTUAL_IPV4, config.virtualIpv4)
    }

    @Test
    fun `fromContext falls back to adguard if profile ID is blank`() {
        val mockContext = io.mockk.mockk<android.content.Context>(relaxed = true)
        val mockPrefs = io.mockk.mockk<android.content.SharedPreferences>(relaxed = true)
        
        io.mockk.every { mockContext.getSharedPreferences("focuslock_prefs", android.content.Context.MODE_PRIVATE) } returns mockPrefs
        io.mockk.every { mockPrefs.getString("NEXTDNS_PROFILE_ID", any()) } returns ""

        val config = DnsConfig.fromContext(mockContext)

        assertEquals(com.focuslock.BuildConfig.FALLBACK_DNS_ENDPOINT, config.dohEndpoint)
    }
}
