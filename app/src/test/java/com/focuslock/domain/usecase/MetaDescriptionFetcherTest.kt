package com.focuslock.domain.usecase

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class MetaDescriptionFetcherTest {

    @Test
    fun `extractDescriptionFromHtml returns content when name precedes content`() {
        val fetcher = MetaDescriptionFetcher()
        val html = """<html><head><meta name="description" content="This is a test description"></head></html>"""
        val result = fetcher.extractDescriptionFromHtml(html)
        org.junit.Assert.assertEquals("This is a test description", result)
    }

    @Test
    fun `extractDescriptionFromHtml returns content when content precedes name`() {
        val fetcher = MetaDescriptionFetcher()
        val html = """<html><head><meta content="This is a test description" name="description"></head></html>"""
        val result = fetcher.extractDescriptionFromHtml(html)
        org.junit.Assert.assertEquals("This is a test description", result)
    }

    @Test
    fun `extractDescriptionFromHtml returns og description if standard description is missing`() {
        val fetcher = MetaDescriptionFetcher()
        val html = """<html><head><meta property="og:description" content="OG test description"></head></html>"""
        val result = fetcher.extractDescriptionFromHtml(html)
        org.junit.Assert.assertEquals("OG test description", result)
    }
}
