package dev.friendsonly.android.filter

import android.content.Context
import android.content.res.Configuration
import android.webkit.WebView
import dev.friendsonly.android.navigation.NavigationPolicy

class FilterInjector(private val context: Context, private val settings: FilterSettings) {
    private val source = context.assets.open("filters.js").bufferedReader().use { it.readText() }
    private val styleSheet = context.assets.open("focus.css").bufferedReader().use { it.readText() }

    fun apply(webView: WebView) {
        if (!NavigationPolicy.canFilter(webView.url.orEmpty())) return
        val darkMode = context.resources.configuration.uiMode and
            Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        // No JavascriptInterface or result callback: no page data crosses into Kotlin.
        webView.evaluateJavascript(
            "(function(){if(location.protocol!=='https:' || !['instagram.com','www.instagram.com','m.instagram.com'].includes(location.hostname))return;\n" +
                source + "\nwindow.FriendsOnlyFilters.configure(" + settings.asJson(darkMode, styleSheet) + ");})();",
            null
        )
    }
}
