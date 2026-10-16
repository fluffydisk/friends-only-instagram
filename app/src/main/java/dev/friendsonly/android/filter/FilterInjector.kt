package dev.friendsonly.android.filter

import android.content.Context
import android.webkit.WebView
import dev.friendsonly.android.navigation.NavigationPolicy

class FilterInjector(context: Context, private val settings: FilterSettings) {
    private val source = context.assets.open("filters.js").bufferedReader().use { it.readText() }

    fun apply(webView: WebView) {
        if (!NavigationPolicy.canFilter(webView.url.orEmpty())) return
        // No JavascriptInterface or result callback: no page data crosses into Kotlin.
        webView.evaluateJavascript(
            "(function(){if(location.protocol!=='https:' || !['instagram.com','www.instagram.com','m.instagram.com'].includes(location.hostname))return;\n" +
                source + "\nwindow.FriendsOnlyFilters.configure(" + settings.asJson() + ");})();",
            null
        )
    }
}
