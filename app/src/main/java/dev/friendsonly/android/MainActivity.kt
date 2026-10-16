package dev.friendsonly.android

import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.webkit.CookieManager
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.widget.Button
import android.widget.CheckBox
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import android.window.OnBackInvokedDispatcher
import dev.friendsonly.android.filter.FilterInjector
import dev.friendsonly.android.filter.FilterSettings
import dev.friendsonly.android.navigation.NavigationPolicy
import dev.friendsonly.android.web.InstagramWebView
import dev.friendsonly.android.web.InstagramWebViewClient

class MainActivity : Activity(), InstagramWebViewClient.Callbacks {
    private lateinit var webView: WebView
    private lateinit var webContainer: FrameLayout
    private lateinit var progress: ProgressBar
    private lateinit var errorBar: LinearLayout
    private lateinit var errorText: TextView
    private lateinit var home: Button
    private lateinit var messages: Button
    private lateinit var filters: FilterSettings
    private lateinit var injector: FilterInjector
    private var currentUrl = NavigationPolicy.HOME
    private var pageHasError = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        filters = FilterSettings(this)
        injector = FilterInjector(this, filters)
        buildLayout()
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

    private fun buildLayout() {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
            root.setOnApplyWindowInsetsListener { view, insets ->
                val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout() or WindowInsets.Type.ime())
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
                WindowInsets.CONSUMED
            }
        } else {
            root.fitsSystemWindows = true
        }
        val toolbar = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(4), dp(8), dp(4))
        }
        toolbar.addView(TextView(this).apply {
            text = getString(R.string.app_name)
            textSize = 20f
            setTypeface(null, Typeface.BOLD)
            setHeading(this)
        }, LinearLayout.LayoutParams(0, dp(48), 1f).apply { gravity = Gravity.CENTER_VERTICAL })
        toolbar.addView(Button(this).apply {
            text = "↻"
            contentDescription = getString(R.string.refresh)
            setOnClickListener { retry() }
        }, LinearLayout.LayoutParams(dp(52), dp(48)))
        toolbar.addView(Button(this).apply {
            text = getString(R.string.settings)
            setOnClickListener { showSettings() }
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(48)))
        root.addView(toolbar)

        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            contentDescription = getString(R.string.loading)
        }
        root.addView(progress, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(3)))
        errorBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(4), dp(8), dp(4))
            visibility = View.GONE
        }
        errorText = TextView(this)
        errorBar.addView(errorText, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        errorBar.addView(Button(this).apply {
            text = getString(R.string.retry)
            setOnClickListener { retry() }
        })
        root.addView(errorBar)

        webContainer = FrameLayout(this)
        root.addView(webContainer, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        val navigation = LinearLayout(this)
        home = Button(this).apply {
            text = getString(R.string.home)
            setOnClickListener { navigate(NavigationPolicy.HOME) }
        }
        messages = Button(this).apply {
            text = getString(R.string.messages)
            setOnClickListener { navigate(NavigationPolicy.MESSAGES) }
        }
        navigation.addView(home, LinearLayout.LayoutParams(0, dp(56), 1f))
        navigation.addView(messages, LinearLayout.LayoutParams(0, dp(56), 1f))
        root.addView(navigation)
        setContentView(root)
    }

    private fun setHeading(view: TextView) {
        view.gravity = Gravity.CENTER_VERTICAL
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) view.isAccessibilityHeading = true
    }

    private fun createWebView() {
        webView = InstagramWebView.create(this)
        webView.webViewClient = InstagramWebViewClient(this)
        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                progress.progress = newProgress
                progress.visibility = if (newProgress >= 100 || pageHasError) View.INVISIBLE else View.VISIBLE
            }
            override fun onPermissionRequest(request: PermissionRequest) { request.deny() }
        }
        webContainer.addView(webView, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
    }

    private fun navigate(url: String) { webView.loadUrl(url) }

    private fun retry() {
        pageHasError = false
        errorBar.visibility = View.GONE
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
        errorBar.visibility = View.GONE
        progress.visibility = View.VISIBLE
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
        progress.visibility = View.INVISIBLE
        errorText.setText(message)
        errorBar.visibility = View.VISIBLE
    }

    override fun rendererGone(view: WebView) {
        webContainer.removeView(view)
        view.destroy()
        createWebView()
        pageFailed(R.string.renderer_error)
    }

    private fun updateSelection(url: String) {
        val direct = NavigationPolicy.isMessages(url)
        home.isSelected = !direct
        messages.isSelected = direct
        home.setTypeface(null, if (direct) Typeface.NORMAL else Typeface.BOLD)
        messages.setTypeface(null, if (direct) Typeface.BOLD else Typeface.NORMAL)
    }

    private fun showSettings() {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), dp(16))
        }
        fun option(label: Int, value: Boolean, update: (Boolean) -> Unit) {
            content.addView(CheckBox(this).apply {
                setText(label)
                isChecked = value
                setOnCheckedChangeListener { _, checked -> update(checked); injector.apply(webView) }
            })
        }
        option(R.string.hide_reels, filters.hideReels) { filters.hideReels = it }
        option(R.string.hide_explore, filters.hideExplore) { filters.hideExplore = it }
        option(R.string.hide_suggestions, filters.hideSuggestions) { filters.hideSuggestions = it }
        content.addView(TextView(this).apply {
            setText(R.string.filter_note)
            textSize = 14f
            setPadding(0, dp(12), 0, 0)
        })
        AlertDialog.Builder(this)
            .setTitle(R.string.settings)
            .setView(ScrollView(this).apply { addView(content) })
            .setPositiveButton(R.string.done, null)
            .show()
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
        webContainer.removeView(webView)
        webView.stopLoading()
        webView.destroy()
        super.onDestroy()
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
