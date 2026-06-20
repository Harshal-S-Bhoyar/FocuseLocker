package com.focuslock.data.parser

import com.focuslock.data.local.entity.BlockedDomainEntity
import com.focuslock.data.local.entity.CategoryKey

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
    fun parseHostsFile(raw: String, categoryKey: String): List<BlockedDomainEntity> {
        val result = mutableListOf<BlockedDomainEntity>()
        val lines = raw.split("\n")
        
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue
            
            var domain = if (trimmed.startsWith("0.0.0.0 ") || trimmed.startsWith("127.0.0.1 ")) {
                val parts = trimmed.split("\\s+".toRegex())
                if (parts.size >= 2) parts[1] else continue
            } else {
                // Plain domain list
                val parts = trimmed.split("\\s+".toRegex())
                parts[0] // take the first token to avoid inline comments if any
            }
            
            if (domain == "0.0.0.0" || domain == "localhost" || domain == "127.0.0.1" || domain == "255.255.255.255" || domain == "broadcasthost") continue
            
            if (domain.startsWith("www.")) {
                domain = domain.substring(4)
            }
            
            // basic check to ensure domain is somewhat valid and not a comment
            if (domain.contains("#") || domain.contains("<") || domain.contains(">")) continue

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
        
        return result
    }
}
