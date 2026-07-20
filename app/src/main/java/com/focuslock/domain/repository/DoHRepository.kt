package com.focuslock.domain.repository

import com.focuslock.domain.model.DoHResult

interface DoHRepository {
    suspend fun queryDoH(domain: String): DoHResult
}
