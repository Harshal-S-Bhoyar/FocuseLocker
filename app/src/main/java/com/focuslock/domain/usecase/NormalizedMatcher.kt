package com.focuslock.domain.usecase

object NormalizedMatcher {
    private val LEET_MAP = mapOf('0' to 'o', '4' to 'a', '5' to 's', '@' to 'a', '$' to 's')
    // Intentionally EXCLUDE '1'→'i' and '3'→'e' (false positives: mp3→mpe, win32→wini2)

    fun normalize(input: String): String {
        val nfd = java.text.Normalizer.normalize(input.lowercase(), java.text.Normalizer.Form.NFD)
        return buildString {
            for (c in nfd) {
                if (c.code > 127) continue  // strip combining marks + non-ASCII
                val mapped = LEET_MAP[c] ?: c
                if (mapped in setOf('-', '_', '.', '+')) continue  // strip separators
                append(mapped)
            }
        }.replace(Regex("\\s+"), " ").trim()
    }

    fun matches(keyword: String, content: String): Boolean {
        val nk = normalize(keyword)
        val nc = normalize(content)
        return if (nk.length <= 4) {
            Regex("""\b${Regex.escape(nk)}\b""").containsMatchIn(nc)
        } else {
            nc.contains(nk)
        }
    }
}
