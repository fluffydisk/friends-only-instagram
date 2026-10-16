package dev.friendsonly.android.web

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView

object InstagramWebView {
    @SuppressLint("SetJavaScriptEnabled") // Instagram's own website requires JavaScript.
    fun create(context: Context): WebView {
        val view = WebView(context).apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                allowFileAccess = false
                allowContentAccess = false
                mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                javaScriptCanOpenWindowsAutomatically = false
                setSupportMultipleWindows(false)
                mediaPlaybackRequiresUserGesture = true
                builtInZoomControls = false
                displayZoomControls = false
                setGeolocationEnabled(false)
                // Keep the real Android WebView user agent. Do not disguise this client.
            }
        }
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(view, false)
        }
        WebView.setWebContentsDebuggingEnabled(false)
        return view
    }
}
