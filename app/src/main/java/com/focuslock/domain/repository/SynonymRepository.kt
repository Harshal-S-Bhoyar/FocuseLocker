package com.focuslock.domain.repository

interface SynonymRepository {
    fun getSynonyms(): Map<String, List<String>>
}
