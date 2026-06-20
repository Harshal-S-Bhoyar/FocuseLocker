package com.focuslock.service.vpn

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Method

class FocusVpnServiceTest {

    private lateinit var vpnService: FocusVpnService
    @Before
    fun setup() {
        vpnService = FocusVpnService()
        
        // Use reflection to set the activeDomains volatile variable
        val activeDomainsField = FocusVpnService::class.java.getDeclaredField("activeDomains")
        activeDomainsField.isAccessible = true
        
        val testDomains = hashSetOf("evil.com", "ads.example.com", "tracker.org")
        activeDomainsField.set(vpnService, testDomains)
    }

    private fun checkDomain(domain: String): Boolean {
        return vpnService.isDomainOrParentBlocked(domain)
    }

    @Test
    fun `test exact match is blocked`() {
        assertTrue(checkDomain("evil.com"))
        assertTrue(checkDomain("tracker.org"))
    }

    @Test
    fun `test subdomain is blocked when parent is in list`() {
        assertTrue(checkDomain("sub.evil.com"))
        assertTrue(checkDomain("deep.sub.evil.com"))
        assertTrue(checkDomain("very.deep.sub.evil.com"))
    }

    @Test
    fun `test subdomain is blocked when exact subdomain is in list`() {
        assertTrue(checkDomain("ads.example.com"))
        assertTrue(checkDomain("sub.ads.example.com"))
    }

    @Test
    fun `test parent is not blocked when only subdomain is in list`() {
        assertFalse(checkDomain("example.com"))
        assertFalse(checkDomain("other.example.com"))
    }

    @Test
    fun `test unrelated domain is not blocked`() {
        assertFalse(checkDomain("google.com"))
        assertFalse(checkDomain("safe-evil.com")) // prefix match shouldn't block
    }
}
