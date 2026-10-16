package dev.friendsonly.android.navigation

import java.net.URI
import java.util.Locale

/** Top-level navigation only. Subresources remain the website's responsibility. */
object NavigationPolicy {
    const val HOME = "https://www.instagram.com/"
    const val MESSAGES = "https://www.instagram.com/direct/inbox/"

    enum class Destination { INSTAGRAM, BROWSER, BLOCKED }

    fun classify(url: String): Destination {
        val uri = parse(url) ?: return Destination.BLOCKED
        val scheme = uri.scheme?.lowercase(Locale.ROOT)
        val host = uri.host?.lowercase(Locale.ROOT) ?: return Destination.BLOCKED
        if (uri.rawUserInfo != null || scheme !in setOf("https", "http")) {
            return Destination.BLOCKED
        }
        val instagram = host == "instagram.com" || host.endsWith(".instagram.com")
        return when {
            instagram && scheme == "https" && uri.port in setOf(-1, 443) -> Destination.INSTAGRAM
            instagram -> Destination.BLOCKED
            else -> Destination.BROWSER
        }
    }

    fun canFilter(url: String): Boolean {
        val uri = parse(url) ?: return false
        return classify(url) == Destination.INSTAGRAM &&
            uri.host.lowercase(Locale.ROOT) in setOf("instagram.com", "www.instagram.com", "m.instagram.com")
    }

    fun isMessages(url: String): Boolean =
        classify(url) == Destination.INSTAGRAM && parse(url)?.path?.startsWith("/direct/") == true

    private fun parse(url: String): URI? = try {
        URI(url)
    } catch (_: Exception) {
        null
    }
}
