package com.focuslock.domain.apps

import com.focuslock.domain.model.InstalledApp

interface InstalledAppsProvider {
    suspend fun getLaunchableApps(): List<InstalledApp>
}
