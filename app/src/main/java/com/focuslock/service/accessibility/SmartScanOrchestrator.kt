package com.focuslock.service.accessibility

import com.focuslock.domain.model.DoHResult
import com.focuslock.domain.model.SmartScanResult
import com.focuslock.domain.usecase.DoHQueryUseCase
import com.focuslock.domain.usecase.SmartScanUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext

class SmartScanOrchestrator(
    private val dohQueryUseCase: DoHQueryUseCase,
    private val smartScanUseCase: SmartScanUseCase,
    private val serviceScope: CoroutineScope,
    private val onBlock: (String, String) -> Unit
) {
    private var pendingSmartScanJob: Job? = null

    fun startScan(
        url: String, 
        activeKeywords: List<String>, 
        activeCategories: List<String> = emptyList()
    ) {
        pendingSmartScanJob?.cancel()
        pendingSmartScanJob = serviceScope.launch {
            try {
                supervisorScope {
                    val dohJob = async { dohQueryUseCase.checkAndCacheDoH(url, activeCategories) }
                    val metaJob = async { smartScanUseCase(url, activeKeywords, activeCategories) }

                    var dohWon = false
                    var metaWon = false
                    val blockedResult = select<Pair<String, String>?> {
                        dohJob.onAwait { dohResult ->
                            dohWon = true
                            if (dohResult is DoHResult.Blocked) {
                                metaJob.cancel()
                                Pair("category_block", "NEXTDNS_DOH")
                            } else {
                                null
                            }
                        }
                        metaJob.onAwait { result ->
                            metaWon = true
                            if (result is SmartScanResult.Blocked) {
                                dohJob.cancel()
                                Pair(result.keyword, "AUTO_BLOCKED: ${result.signal}")
                            } else {
                                null
                            }
                        }
                    }

                    if (blockedResult != null) {
                        withContext(Dispatchers.Main) {
                            onBlock(blockedResult.first, blockedResult.second)
                        }
                        return@supervisorScope
                    }

                    // Wait for remaining jobs if one finished cleanly
                    if (!dohWon) {
                        val dohResult = dohJob.await()
                        if (dohResult is DoHResult.Blocked) {
                            withContext(Dispatchers.Main) {
                                onBlock("category_block", "NEXTDNS_DOH")
                            }
                        }
                    }
                    if (!metaWon) {
                        val result = metaJob.await()
                        if (result is SmartScanResult.Blocked) {
                            withContext(Dispatchers.Main) {
                                onBlock(result.keyword, "AUTO_BLOCKED: ${result.signal}")
                            }
                        }
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                timber.log.Timber.e(e, "SmartScanOrchestrator: scan failed")
            }
        }
    }
}
