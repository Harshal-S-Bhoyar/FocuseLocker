package com.focuslock.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DomainExtractorTest {

    @Test
    fun extract_validHttpUrls_returnsDomain() {
        assertEquals("example.com", DomainExtractor.extract("http://example.com"))
        assertEquals("example.com", DomainExtractor.extract("https://example.com"))
    }

    @Test
    fun extract_urlWithWww_returnsStrippedDomain() {
        assertEquals("example.com", DomainExtractor.extract("http://www.example.com"))
        assertEquals("example.com", DomainExtractor.extract("https://www.example.com"))
    }

    @Test
    fun extract_urlWithMobilePrefix_returnsStrippedDomain() {
        assertEquals("example.com", DomainExtractor.extract("http://m.example.com"))
        assertEquals("example.com", DomainExtractor.extract("https://mobile.example.com"))
    }

    @Test
    fun extract_urlWithMultiplePrefixes_returnsStrippedDomain() {
        // e.g. www.m.example.com might not be common but tests the loop logic
        assertEquals("example.com", DomainExtractor.extract("http://www.m.example.com"))
    }

    @Test
    fun extract_urlWithPathAndQuery_returnsJustDomain() {
        assertEquals("example.com", DomainExtractor.extract("https://example.com/path/to/resource?query=1"))
    }

    @Test
    fun extract_bareDomain_returnsDomain() {
        assertEquals("example.com", DomainExtractor.extract("example.com"))
        assertEquals("example.com", DomainExtractor.extract("www.example.com"))
        assertEquals("example.com", DomainExtractor.extract("m.example.com"))
    }

    @Test
    fun extract_invalidUrl_returnsNull() {
        // Note: URI parsing might still extract something from non-standard URLs
        // Testing some edge cases where it fails to parse a host
        assertNull(DomainExtractor.extract(""))
        assertNull(DomainExtractor.extract("http://"))
    }
}
