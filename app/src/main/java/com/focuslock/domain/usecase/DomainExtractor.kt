package com.focuslock.domain.usecase

/**
 * Single source of truth for URL → domain extraction.
 * Strips www., m., mobile. prefixes for consistent cache keying.
 */
object DomainExtractor {
    fun extract(url: String): String? {
        return try {
            val uri = java.net.URI(if (url.startsWith("http")) url else "https://$url")
            val host = uri.host?.lowercase() ?: return null
            host.removePrefix("www.").removePrefix("m.").removePrefix("mobile.")
        } catch (e: Exception) {
            null
        }
    }
}
