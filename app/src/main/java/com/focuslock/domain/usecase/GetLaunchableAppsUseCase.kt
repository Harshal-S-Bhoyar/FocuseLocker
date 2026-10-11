package com.focuslock.domain.usecase

import com.focuslock.domain.apps.InstalledAppsProvider
import com.focuslock.domain.model.InstalledApp
import javax.inject.Inject

class GetLaunchableAppsUseCase @Inject constructor(
    private val provider: InstalledAppsProvider
) {
    suspend operator fun invoke(query: String = ""): List<InstalledApp> {
        val apps = provider.getLaunchableApps()
        if (query.isBlank()) return apps
        return apps.filter { it.label.contains(query, ignoreCase = true) || it.packageName.contains(query, ignoreCase = true) }
    }
}
