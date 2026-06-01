package com.focuslock.service.vpn

import org.junit.Assert.assertEquals
import org.junit.Test

class DnsConfigTest {

    @Test
    fun `default config exposes primary resolver before fallback resolver`() {
        assertEquals(listOf("8.8.8.8", "8.8.4.4"), DnsConfig.DEFAULT.resolvers)
    }
}
