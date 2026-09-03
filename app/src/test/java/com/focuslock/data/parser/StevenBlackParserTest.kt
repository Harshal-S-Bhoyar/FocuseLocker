package com.focuslock.data.parser

import com.focuslock.data.local.entity.CategoryKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

class StevenBlackParserTest {

    @Test
    fun `parseHostsFile streams data correctly and ignores invalid lines`() {
        val simulatedHostsContent = """
            # This is a comment
            
            127.0.0.1 localhost
            0.0.0.0 0.0.0.0
            
            0.0.0.0 bad-domain.com
            0.0.0.0 www.another-bad.com # inline comment
            
            # Some invalid stuff
            0.0.0.0 has#hash.com
            0.0.0.0 has<bracket.com
            
            plain-domain.org
        """.trimIndent()
        
        val inputStream = ByteArrayInputStream(simulatedHostsContent.toByteArray(Charsets.UTF_8))
        
        val entities = StevenBlackParser.parseHostsFile(inputStream, CategoryKey.GENERAL)
        
        // We expect:
        // bad-domain.com
        // another-bad.com (www stripped)
        // plain-domain.org
        
        assertEquals(3, entities.size)
        
        assertTrue(entities.any { it.domain == "bad-domain.com" })
        assertTrue(entities.any { it.domain == "another-bad.com" })
        assertTrue(entities.any { it.domain == "plain-domain.org" })
        
        // Verify all have correct category
        assertTrue(entities.all { it.categoryKey == CategoryKey.GENERAL })
        
        // Verify defaults
        assertTrue(entities.all { !it.isAutoBlocked })
        assertTrue(entities.all { it.blockStatus == "PERMANENT" })
    }

    @Test
    fun `parseHostsFile returns empty list for empty input`() {
        val entities = StevenBlackParser.parseHostsFile(
            ByteArrayInputStream(ByteArray(0)),
            CategoryKey.ADULT
        )

        assertTrue(entities.isEmpty())
    }

    @Test
    fun `parseHostsFile skips all reserved host entries`() {
        val content = """
            127.0.0.1 localhost
            0.0.0.0 localhost
            255.255.255.255 broadcasthost
            0.0.0.0 127.0.0.1
        """.trimIndent()

        val entities = StevenBlackParser.parseHostsFile(
            ByteArrayInputStream(content.toByteArray(Charsets.UTF_8)),
            CategoryKey.NEWS
        )

        assertTrue(entities.isEmpty())
    }

    @Test
    fun `parseHostsFile handles tabs and strips www prefix`() {
        val content = "0.0.0.0\twww.example.com"

        val entities = StevenBlackParser.parseHostsFile(
            ByteArrayInputStream(content.toByteArray(Charsets.UTF_8)),
            CategoryKey.SOCIAL_MEDIA
        )

        assertEquals(listOf("example.com"), entities.map { it.domain })
        assertTrue(entities.all { it.categoryKey == CategoryKey.SOCIAL_MEDIA })
    }

    @Test
    fun `parseHostsFile uses the first token for plain domain lines`() {
        val content = "example.org extra-token # comment"

        val entities = StevenBlackParser.parseHostsFile(
            ByteArrayInputStream(content.toByteArray(Charsets.UTF_8)),
            CategoryKey.GAMBLING
        )

        assertEquals(listOf("example.org"), entities.map { it.domain })
        assertTrue(entities.all { it.blockStatus == "PERMANENT" })
    }
}
