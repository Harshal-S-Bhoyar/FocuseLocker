package com.focuslock.data.apps

import android.content.Context
import android.content.Intent
import com.focuslock.domain.apps.BlockedAppExclusions
import com.focuslock.domain.apps.InstalledAppsProvider
import com.focuslock.domain.model.InstalledApp
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PackageManagerInstalledAppsProvider @Inject constructor(
    @ApplicationContext private val context: Context
) : InstalledAppsProvider {

    // ponytail: launcher-intent query avoids QUERY_ALL_PACKAGES; shared safelist prevents self-lockout
    private val excludedPackages = BlockedAppExclusions.SETTINGS_PACKAGES

    override suspend fun getLaunchableApps(): List<InstalledApp> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val selfPkg = context.packageName
        val launcherPkg = resolveLauncherPackage()
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        // O(N log N) sort on bounded launcher list; O(1) exclusion via Set
        val infos = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(intent, android.content.pm.PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION") pm.queryIntentActivities(intent, 0)
        }
        infos.mapNotNull { ri ->
            val pkg = ri.activityInfo?.packageName ?: return@mapNotNull null
            if (pkg == selfPkg || pkg == launcherPkg || pkg in excludedPackages) return@mapNotNull null
            val label = ri.loadLabel(pm)?.toString() ?: pkg
            InstalledApp(packageName = pkg, label = label)
        }.sortedBy { it.label.lowercase() }
    }

    private fun resolveLauncherPackage(): String? {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        return context.packageManager.resolveActivity(home, 0)?.activityInfo?.packageName
    }
}
