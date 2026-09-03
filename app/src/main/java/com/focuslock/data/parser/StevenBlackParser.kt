package com.focuslock.data.parser

import com.focuslock.data.local.entity.BlockedDomainEntity
import com.focuslock.data.local.entity.CategoryKey
import timber.log.Timber

object StevenBlackConfig {
    val categoryToUrlMap = mapOf(
        CategoryKey.ADULT to "https://raw.githubusercontent.com/StevenBlack/hosts/master/alternates/porn/hosts",
        CategoryKey.GAMBLING to "https://raw.githubusercontent.com/hagezi/dns-blocklists/main/domains/gambling.txt",
        CategoryKey.SOCIAL_MEDIA to "https://raw.githubusercontent.com/StevenBlack/hosts/master/alternates/fakenews-gambling-porn-social/hosts",
        CategoryKey.NEWS to "https://raw.githubusercontent.com/StevenBlack/hosts/master/alternates/fakenews/hosts",
        CategoryKey.GENERAL to "https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts"
    )
}

object StevenBlackParser {
    // ponytail: precompile regex once instead of 58,000 times inside loop
    private val WHITESPACE_REGEX = Regex("\\s+")

    fun parseHostsFile(inputStream: java.io.InputStream, categoryKey: String): List<BlockedDomainEntity> {
        val result = mutableListOf<BlockedDomainEntity>()
        inputStream.bufferedReader().useLines { lines ->
            for (line in lines) {
                val trimmed = line.trim()
                if (trimmed.isEmpty() || trimmed.startsWith("#")) continue
                
                val parts = trimmed.split(WHITESPACE_REGEX)
                var domain = if (
                    parts.size >= 2 &&
                    (parts[0] == "0.0.0.0" || parts[0] == "127.0.0.1" || parts[0] == "::1")
                ) {
                    parts[1]
                } else {
                    // Plain domain list
                    parts[0] // take the first token to avoid inline comments if any
                }

                if (domain == "0.0.0.0" || domain == "::1" || domain == "localhost" || domain == "127.0.0.1" || domain == "255.255.255.255" || domain == "broadcasthost") continue
                
                if (domain.startsWith("www.")) {
                    domain = domain.substring(4)
                }
                
                if (domain.contains("#") || domain.contains("<") || domain.contains(">")) {
                    Timber.w("STEVEN_BLACK: Domain parse skipped due to invalid characters: $domain")
                    continue
                }

                result.add(
                    BlockedDomainEntity(
                        domain = domain,
                        categoryKey = categoryKey,
                        isAutoBlocked = false,
                        matchedKeyword = null,
                        autoBlockedAt = null,
                        blockStatus = "PERMANENT",
                        expiresAt = null
                    )
                )
            }
        }
        
        return result
    }
}
