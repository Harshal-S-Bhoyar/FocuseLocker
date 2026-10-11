package com.focuslock.domain.apps

// ponytail: [single safelist for every blocking surface; add OEM settings pkgs here, not per-caller]
object BlockedAppExclusions {
    val SETTINGS_PACKAGES = setOf(
        "com.android.settings",
        "com.android.systemui",
        "com.android.phone",
        "android",
        "com.samsung.android.app.settings",
        "com.miui.securitycenter",
        "com.coloros.safecenter",
        "com.vivo.permissionmanager",
        "com.oneplus.security",
        "com.huawei.systemmanager",
        "com.letv.android.letvsafe",
        "com.samsung.android.lool",
        "com.asus.mobilemanager"
    )
}
