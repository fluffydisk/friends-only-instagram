package dev.friendsonly.android

import android.app.Activity
import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowInsets
import android.webkit.CookieManager
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.widget.FrameLayout
import android.widget.Toast
import android.window.OnBackInvokedDispatcher
import dev.friendsonly.android.filter.FilterInjector
import dev.friendsonly.android.filter.FilterSettings
import dev.friendsonly.android.navigation.NavigationPolicy
import dev.friendsonly.android.ui.FocusChrome
import dev.friendsonly.android.ui.FocusSettingsDialog
import dev.friendsonly.android.web.InstagramWebView
import dev.friendsonly.android.web.InstagramWebViewClient

class MainActivity : Activity(), InstagramWebViewClient.Callbacks {
    private lateinit var webView: WebView
    private lateinit var chrome: FocusChrome
    private lateinit var filters: FilterSettings
    private lateinit var injector: FilterInjector
    private var currentUrl = NavigationPolicy.HOME
    private var pageHasError = false
    private var settingsDialog: Dialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        filters = FilterSettings(this)
        injector = FilterInjector(this, filters)
        chrome = FocusChrome(
            this,
            onHome = { navigate(NavigationPolicy.HOME) },
            onMessages = { navigate(NavigationPolicy.MESSAGES) },
            onSettings = { showSettings() },
            onBack = { navigateBack() }
        )
        configureInsets()
        setContentView(chrome.root)
        createWebView()
        val restored = savedInstanceState?.getBundle("web")?.let { webView.restoreState(it) }
        if (restored == null || NavigationPolicy.classify(webView.url.orEmpty()) != NavigationPolicy.Destination.INSTAGRAM) {
            webView.loadUrl(NavigationPolicy.HOME)
        } else {
            currentUrl = webView.url ?: NavigationPolicy.HOME
            updateSelection(currentUrl)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT) { navigateBack() }
        }
    }

    private fun configureInsets() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            @Suppress("DEPRECATION")
            window.setDecorFitsSystemWindows(false)
            chrome.root.setOnApplyWindowInsetsListener { view, insets ->
                val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout() or WindowInsets.Type.ime())
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
                chrome.setKeyboardVisible(insets.isVisible(WindowInsets.Type.ime()))
                WindowInsets.CONSUMED
            }
        } else {
            chrome.root.fitsSystemWindows = true
        }
    }

    private fun createWebView() {
        webView = InstagramWebView.create(this)
        webView.setBackgroundColor(getColor(R.color.focus_background))
        webView.webViewClient = InstagramWebViewClient(this)
        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                chrome.setLoading(newProgress, newProgress < 100 && !pageHasError)
            }
            override fun onPermissionRequest(request: PermissionRequest) { request.deny() }
        }
        chrome.webContainer.addView(webView, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
    }

    private fun navigate(url: String) { webView.loadUrl(url) }

    private fun retry() {
        pageHasError = false
        chrome.clearError()
        webView.loadUrl(currentUrl)
    }

    private fun navigateBack() {
        if (webView.canGoBack()) webView.goBack() else finish()
    }

    @Deprecated("Used by Android 12 and earlier")
    override fun onBackPressed() { navigateBack() }

    override fun externalLink(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, R.string.external_unavailable, Toast.LENGTH_SHORT).show()
        }
    }

    override fun blockedLink() { Toast.makeText(this, R.string.blocked_link, Toast.LENGTH_SHORT).show() }

    override fun pageStarted(url: String) {
        if (NavigationPolicy.classify(url) == NavigationPolicy.Destination.INSTAGRAM) currentUrl = url
        pageHasError = false
        chrome.clearError()
        chrome.setLoading(0, true)
        updateSelection(url)
    }

    override fun pageReady(view: WebView) {
        injector.apply(view)
        val url = view.url.orEmpty()
        if (NavigationPolicy.classify(url) == NavigationPolicy.Destination.INSTAGRAM) currentUrl = url
        updateSelection(url)
    }

    override fun pageFailed(message: Int) {
        pageHasError = true
        chrome.setLoading(0, false)
        chrome.showError(message, ::retry)
    }

    override fun rendererGone(view: WebView) {
        chrome.webContainer.removeView(view)
        view.destroy()
        createWebView()
        pageFailed(R.string.renderer_error)
    }

    private fun updateSelection(url: String) {
        chrome.setPage(NavigationPolicy.isMessages(url), webView.canGoBack())
    }

    private fun showSettings() {
        settingsDialog?.dismiss()
        val dialog = FocusSettingsDialog.show(
            this,
            state = FocusSettingsDialog.State(
                minimalAppearance = filters.minimalAppearance,
                hideReels = filters.hideReels,
                hideExplore = filters.hideExplore,
                hideDistractions = filters.hideDistractions,
                hideSuggestions = filters.hideSuggestions
            ),
            onChange = { state ->
                filters.minimalAppearance = state.minimalAppearance
                filters.hideReels = state.hideReels
                filters.hideExplore = state.hideExplore
                filters.hideDistractions = state.hideDistractions
                filters.hideSuggestions = state.hideSuggestions
                injector.apply(webView)
            },
            onReload = ::retry,
            onAccount = { navigate(NavigationPolicy.ACCOUNT) }
        )
        settingsDialog = dialog
        dialog.setOnDismissListener {
            if (settingsDialog === dialog) settingsDialog = null
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        val state = Bundle()
        webView.saveState(state)
        outState.putBundle("web", state)
        super.onSaveInstanceState(outState)
    }

    override fun onResume() {
        super.onResume()
        webView.onResume()
        injector.apply(webView)
    }

    override fun onPause() {
        webView.onPause()
        CookieManager.getInstance().flush()
        super.onPause()
    }

    override fun onDestroy() {
        settingsDialog?.dismiss()
        settingsDialog = null
        chrome.webContainer.removeView(webView)
        webView.stopLoading()
        webView.destroy()
        super.onDestroy()
    }
}
