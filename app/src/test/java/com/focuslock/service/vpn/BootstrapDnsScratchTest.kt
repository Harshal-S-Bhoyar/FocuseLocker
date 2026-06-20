package com.focuslock.service.vpn

import org.junit.Test
import org.junit.Assert.*

class BootstrapDnsScratchTest {
    @Test
    fun testLookup() {
        val dns = BootstrapDns(null)
        val result = dns.lookup("google.com")
        println("Result: $result")
        assertTrue(result.isNotEmpty())
    }
}
