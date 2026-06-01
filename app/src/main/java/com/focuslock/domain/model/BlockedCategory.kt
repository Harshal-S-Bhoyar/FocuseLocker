package com.focuslock.domain.model

data class BlockedCategory(
    val id: Long,
    val categoryKey: String,
    val isActive: Boolean
) {
    val displayName: String get() = when (categoryKey) {
        "SOCIAL_MEDIA" -> "Social Media"
        "ADULT"        -> "Adult Content"
        "GAMBLING"     -> "Gambling"
        "NEWS"         -> "News"
        "GAMING"       -> "Gaming"
        "SHOPPING"     -> "Shopping"
        else           -> categoryKey
    }

    val exampleDomains: String get() = when (categoryKey) {
        "SOCIAL_MEDIA" -> "facebook.com, instagram.com, twitter.com, tiktok.com"
        "ADULT"        -> "Adult content sites"
        "GAMBLING"     -> "bet365.com, pokerstars.com, draftkings.com"
        "NEWS"         -> "cnn.com, bbc.com, reddit.com, news.google.com"
        "GAMING"       -> "twitch.tv, steam.com, ign.com"
        "SHOPPING"     -> "amazon.com, flipkart.com, ebay.com"
        else           -> ""
    }
}
