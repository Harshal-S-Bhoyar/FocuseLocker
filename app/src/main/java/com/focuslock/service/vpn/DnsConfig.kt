package com.focuslock.service.vpn

import android.content.Context

data class DnsConfig(
    val virtualIpv4: String,
    val virtualIpv6: String,
    val dohEndpoint: String
) {
    companion object {
        private const val PREFS_NAME = "focuslock_prefs"
        private const val PREFS_KEY = "NEXTDNS_PROFILE_ID"

        /**
         * Resolves the NextDNS profile ID from SharedPreferences → BuildConfig fallback.
         * Same resolution chain used by DoHQueryUseCase to prevent profile drift.
         */
        fun resolveProfileId(context: Context): String {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getString(PREFS_KEY, com.focuslock.BuildConfig.NEXTDNS_PROFILE_ID) ?: ""
        }

        fun fromContext(context: Context): DnsConfig {
            val profileId = resolveProfileId(context)
            val endpoint = if (profileId.isBlank()) {
                com.focuslock.BuildConfig.FALLBACK_DNS_ENDPOINT
            } else {
                "${com.focuslock.BuildConfig.NEXTDNS_BASE_URL}$profileId"
            }
            return DnsConfig(
                virtualIpv4 = com.focuslock.BuildConfig.VIRTUAL_IPV4,
                virtualIpv6 = com.focuslock.BuildConfig.VIRTUAL_IPV6,
                dohEndpoint = endpoint
            )
        }

        // ponytail: kept for tests that don't need a Context. Uses BuildConfig default.
        val DEFAULT = DnsConfig(
            virtualIpv4 = com.focuslock.BuildConfig.VIRTUAL_IPV4,
            virtualIpv6 = com.focuslock.BuildConfig.VIRTUAL_IPV6,
            dohEndpoint = if (com.focuslock.BuildConfig.NEXTDNS_PROFILE_ID.isBlank()) {
                com.focuslock.BuildConfig.FALLBACK_DNS_ENDPOINT
            } else {
                "${com.focuslock.BuildConfig.NEXTDNS_BASE_URL}${com.focuslock.BuildConfig.NEXTDNS_PROFILE_ID}"
            }
        )
    }
}
