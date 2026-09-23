package my.noveldokusha.network

import com.google.gson.Gson

/**
 * Configuration for Cloudflare bypass detection.
 * Defines rules for detecting when Cloudflare Turnstile has blocked content.
 */
data class CloudflareConfig(
    val detectionRules: List<DetectionRule> = emptyList()
) {
    fun toJson(): String = gson.toJson(this)

    companion object {
        private val gson = Gson()
    }
}

/**
 * A rule for detecting Cloudflare blocking.
 */
data class DetectionRule(
    val urlPattern: String,           // URL pattern to match (e.g., "search.php")
    val responseCode: Int = 200,      // Expected HTTP response code (usually 200)
    val requiredContent: String? = null,  // Content that must be present/absent
    val blockedWhenMissing: Boolean = true  // true = blocked when content missing, false = blocked when content present
)
