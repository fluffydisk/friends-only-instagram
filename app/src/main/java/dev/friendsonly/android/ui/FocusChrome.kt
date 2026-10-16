package dev.friendsonly.android.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import dev.friendsonly.android.R

/** Friends Only's native shell. Instagram content remains inside [webContainer]. */
class FocusChrome(
    private val context: Context,
    onHome: () -> Unit,
    onMessages: () -> Unit,
    onSettings: () -> Unit,
    onBack: () -> Unit,
) {
    private val style = FocusStyle(context)
    private val match = ViewGroup.LayoutParams.MATCH_PARENT
    private val wrap = ViewGroup.LayoutParams.WRAP_CONTENT

    val root = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(style.background)
    }
    val webContainer = FrameLayout(context).apply { setBackgroundColor(style.background) }

    private fun iconButton(icon: Int, label: Int, onClick: () -> Unit) = ImageButton(context).apply {
        setImageResource(icon)
        imageTintList = ColorStateList.valueOf(style.text)
        contentDescription = context.getString(label)
        background = style.ripple(style.background, 16)
        setPadding(style.dp(12), style.dp(12), style.dp(12), style.dp(12))
        setOnClickListener { onClick() }
    }

    private val back = iconButton(R.drawable.ic_focus_back, R.string.back, onBack).apply {
        visibility = View.GONE
    }
    private val subtitle = style.label(R.string.home, 12f, style.muted)
    private val progress = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
        max = 100
        visibility = View.INVISIBLE
        progressTintList = ColorStateList.valueOf(style.accent)
        progressBackgroundTintList = ColorStateList.valueOf(style.background)
        contentDescription = context.getString(R.string.loading)
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    private val errorBar = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(style.dp(16), style.dp(10), style.dp(8), style.dp(10))
        setBackgroundColor(style.selected)
        accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        visibility = View.GONE
    }
    private val errorText = style.label(R.string.network_error, 13f)
    private var onRetry: (() -> Unit)? = null
    private val home = FocusTab(context, style, R.drawable.ic_focus_feed, R.string.home, onHome)
    private val messages = FocusTab(context, style, R.drawable.ic_focus_messages, R.string.messages, onMessages)
    private val navigation = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }

    init {
        val toolbar = LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = style.dp(76)
            setPadding(style.dp(20), style.dp(10), style.dp(12), style.dp(10))
        }
        toolbar.addView(back, LinearLayout.LayoutParams(style.dp(48), style.dp(48)).apply {
            marginEnd = style.dp(6)
        })
        val title = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, style.dp(2), style.dp(8), style.dp(2))
        }
        title.addView(style.label(R.string.app_name, 21f, medium = true).also(style::heading))
        title.addView(subtitle, LinearLayout.LayoutParams(wrap, wrap).apply { topMargin = style.dp(5) })
        toolbar.addView(title, LinearLayout.LayoutParams(0, wrap, 1f))
        toolbar.addView(iconButton(R.drawable.ic_focus_settings, R.string.settings, onSettings),
            LinearLayout.LayoutParams(style.dp(48), style.dp(48)))
        root.addView(toolbar, LinearLayout.LayoutParams(match, wrap))
        root.addView(progress, LinearLayout.LayoutParams(match, style.dp(2)))

        errorBar.addView(errorText, LinearLayout.LayoutParams(0, wrap, 1f))
        errorBar.addView(style.action(R.string.retry) { onRetry?.invoke() }, LinearLayout.LayoutParams(wrap, wrap))
        root.addView(errorBar, LinearLayout.LayoutParams(match, wrap))
        root.addView(webContainer, LinearLayout.LayoutParams(match, 0, 1f))

        navigation.addView(View(context).apply {
            setBackgroundColor(style.divider)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LinearLayout.LayoutParams(match, style.dp(1)))
        val tabs = LinearLayout(context).apply {
            gravity = Gravity.CENTER
            setPadding(style.dp(20), style.dp(10), style.dp(20), style.dp(10))
        }
        tabs.addView(home, LinearLayout.LayoutParams(0, wrap, 1f).apply { marginEnd = style.dp(6) })
        tabs.addView(messages, LinearLayout.LayoutParams(0, wrap, 1f).apply { marginStart = style.dp(6) })
        navigation.addView(tabs, LinearLayout.LayoutParams(match, wrap))
        root.addView(navigation, LinearLayout.LayoutParams(match, wrap))
        setPage(messages = false, canGoBack = false)
    }

    fun setPage(messages: Boolean, canGoBack: Boolean) {
        home.select(!messages)
        this.messages.select(messages)
        subtitle.setText(if (messages) R.string.messages else R.string.home)
        back.visibility = if (canGoBack) View.VISIBLE else View.GONE
    }

    fun setLoading(progress: Int, visible: Boolean) {
        this.progress.progress = progress.coerceIn(0, 100)
        this.progress.visibility = if (visible) View.VISIBLE else View.INVISIBLE
    }

    fun showError(messageRes: Int, onRetry: () -> Unit) {
        this.onRetry = onRetry
        errorText.setText(messageRes)
        errorBar.visibility = View.VISIBLE
    }

    fun clearError() {
        errorBar.visibility = View.GONE
        onRetry = null
    }

    /** Keep the whole conversation visible when Android opens the on-screen keyboard. */
    fun setKeyboardVisible(visible: Boolean) {
        navigation.visibility = if (visible) View.GONE else View.VISIBLE
    }
}

@SuppressLint("ViewConstructor") // Constructed in code with its navigation callback; never inflated from XML.
private class FocusTab(
    context: Context,
    private val style: FocusStyle,
    icon: Int,
    label: Int,
    onClick: () -> Unit,
) : LinearLayout(context) {
    private val iconView = ImageView(context).apply {
        setImageResource(icon)
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    private val labelView: TextView = style.label(label, 14f).apply {
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    init {
        gravity = Gravity.CENTER
        minimumHeight = style.dp(52)
        setPadding(style.dp(10), style.dp(12), style.dp(10), style.dp(12))
        contentDescription = context.getString(label)
        isFocusable = true
        addView(iconView, LayoutParams(style.dp(22), style.dp(22)).apply { marginEnd = style.dp(9) })
        addView(labelView, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        setOnClickListener { onClick() }
    }

    fun select(selected: Boolean) {
        isSelected = selected
        val color = if (selected) style.accent else style.muted
        iconView.imageTintList = ColorStateList.valueOf(color)
        labelView.setTextColor(color)
        labelView.typeface = Typeface.create(if (selected) "sans-serif-medium" else "sans-serif", Typeface.NORMAL)
        background = style.ripple(if (selected) style.selected else style.background, 18)
    }

    override fun getAccessibilityClassName(): CharSequence = android.widget.Button::class.java.name
}
