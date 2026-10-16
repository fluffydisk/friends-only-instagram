package dev.friendsonly.android.web

import android.graphics.Bitmap
import android.net.http.SslError
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import dev.friendsonly.android.R
import dev.friendsonly.android.navigation.NavigationPolicy

class InstagramWebViewClient(private val callbacks: Callbacks) : WebViewClient() {
    interface Callbacks {
        fun externalLink(url: String)
        fun blockedLink()
        fun pageStarted(url: String)
        fun pageReady(view: WebView)
        fun pageFailed(message: Int)
        fun rendererGone(view: WebView)
    }

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        // A frame cannot launch another app. No interception of request bodies or credentials.
        if (!request.isForMainFrame) {
            if (request.url.toString() == "about:blank") return false
            return NavigationPolicy.classify(request.url.toString()) == NavigationPolicy.Destination.BLOCKED
        }
        return when (NavigationPolicy.classify(request.url.toString())) {
            NavigationPolicy.Destination.INSTAGRAM -> false
            NavigationPolicy.Destination.BROWSER -> { callbacks.externalLink(request.url.toString()); true }
            NavigationPolicy.Destination.BLOCKED -> { callbacks.blockedLink(); true }
        }
    }

    override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
        // shouldOverrideUrlLoading is not called for every POST or app-initiated load.
        when (NavigationPolicy.classify(url)) {
            NavigationPolicy.Destination.BROWSER -> {
                view.stopLoading()
                callbacks.externalLink(url)
                return
            }
            NavigationPolicy.Destination.BLOCKED -> {
                view.stopLoading()
                callbacks.blockedLink()
                return
            }
            NavigationPolicy.Destination.INSTAGRAM -> Unit
        }
        callbacks.pageStarted(url)
    }

    override fun onPageCommitVisible(view: WebView, url: String) { callbacks.pageReady(view) }
    override fun onPageFinished(view: WebView, url: String) { callbacks.pageReady(view) }
    override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) { callbacks.pageReady(view) }

    override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
        if (request.isForMainFrame) callbacks.pageFailed(R.string.network_error)
    }

    override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
        if (request.isForMainFrame && response.statusCode >= 400) callbacks.pageFailed(R.string.http_error)
    }

    override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
        handler.cancel()
        // SSL errors can also concern subresources. Only show the page banner for its URL.
        if (error.url == view.url) callbacks.pageFailed(R.string.tls_error)
    }

    override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
        callbacks.rendererGone(view)
        return true
    }
}
