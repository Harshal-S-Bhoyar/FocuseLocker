package com.focuslock.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Test

class MetaDescriptionFetcherTest {

    @Test
    fun `extractSignals extracts meta description`() {
        val fetcher = MetaDescriptionFetcher()
        val html = """<html><head><meta name="description" content="This is a test description"></head></html>"""
        val result = fetcher.extractSignals(html)
        assertEquals("This is a test description", result["meta_description"])
    }

    @Test
    fun `extractSignals extracts og description`() {
        val fetcher = MetaDescriptionFetcher()
        val html = """<html><head><meta property="og:description" content="OG test description"></head></html>"""
        val result = fetcher.extractSignals(html)
        assertEquals("OG test description", result["og_description"])
    }

    @Test
    fun `extractSignals extracts page title and body text`() {
        val fetcher = MetaDescriptionFetcher()
        val html = """<html><head><title>Page Title</title></head><body><p>Body content</p></body></html>"""
        val result = fetcher.extractSignals(html)
        assertEquals("Page Title", result["page_title"])
        assertEquals("Body content", result["body_text"])
    }
}
