package com.focuslock.domain.usecase

import com.focuslock.domain.repository.SynonymRepository
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Singleton keyword scanner backed by Aho-Corasick.
 * Call [rebuild] when keywords change → atomically swaps the automaton.
 * Call [scan] from SmartScanUseCase → O(n) single-pass matching.
 */
@Singleton
class KeywordScanner @Inject constructor(
    private val synonymRepository: SynonymRepository
) {
    private val automatonRef = AtomicReference<AhoCorasick?>(null)
    private val keywordMapRef = AtomicReference<Map<String, String>>(emptyMap())

    // Loaded once from SynonymRepository
    private val synonymsMap: Map<String, List<String>> by lazy {
        synonymRepository.getSynonyms()
    }

    /**
     * Rebuild the automaton with new keywords. Thread-safe via AtomicReference swap.
     * Maps each synonym/expansion back to its parent keyword for match reporting.
     */
    fun rebuild(keywords: List<String>) {
        val builder = AhoCorasick.Builder()
        val termToKeyword = mutableMapOf<String, String>()

        for (keyword in keywords) {
            val kw = keyword.lowercase()
            val normalized = NormalizedMatcher.normalize(kw)
            builder.addPattern(normalized)
            termToKeyword[normalized] = kw

            val synonyms = synonymsMap[kw] ?: emptyList()
            for (syn in synonyms) {
                val normalizedSyn = NormalizedMatcher.normalize(syn)
                builder.addPattern(normalizedSyn)
                termToKeyword[normalizedSyn] = kw
            }
        }

        automatonRef.set(builder.build())
        keywordMapRef.set(termToKeyword)
    }

    /**
     * Scan signals map for any keyword match. Returns (parentKeyword, signalName) or null.
     * Uses Aho-Corasick O(n) single-pass for long keywords (≥5 chars).
     * Short keywords (≤4 chars) still use NormalizedMatcher.matches() for word-boundary
     * safety — prevents "ass" matching inside "class" or "assassin".
     */
    fun scan(signals: Map<String, String>): Pair<String, String>? {
        val automaton = automatonRef.get() ?: return null
        val termMap = keywordMapRef.get()

        for ((signalName, signalValue) in signals) {
            val normalizedContent = NormalizedMatcher.normalize(signalValue)

            // Phase 1: Aho-Corasick pass for long patterns (≥5 chars, substring match)
            val match = automaton.searchFirst(normalizedContent)
            if (match != null && match.keyword.length >= 5) {
                val parentKeyword = termMap[match.keyword] ?: match.keyword
                return Pair(parentKeyword, signalName)
            }

            // Phase 2: Word-boundary check for short patterns (≤4 chars)
            // AC can't enforce \b boundaries, so we fall back to NormalizedMatcher
            for ((term, parentKw) in termMap) {
                if (term.length <= 4 && NormalizedMatcher.matches(term, signalValue)) {
                    return Pair(parentKw, signalName)
                }
            }
        }
        return null
    }
}
