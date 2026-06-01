package com.focuslock.service.vpn

data class DnsConfig(
    val primaryResolver: String,
    val fallbackResolver: String
) {
    val resolvers: List<String> = listOf(primaryResolver, fallbackResolver)

    companion object {
        val DEFAULT = DnsConfig(
            primaryResolver = "8.8.8.8",
            fallbackResolver = "8.8.4.4"
        )
    }
}
