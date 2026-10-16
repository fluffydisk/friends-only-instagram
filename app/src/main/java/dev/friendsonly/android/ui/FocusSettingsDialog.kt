package dev.friendsonly.android.ui

import android.app.Dialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import dev.friendsonly.android.R

/** Local, reversible controls. Instagram account management stays on Instagram. */
object FocusSettingsDialog {
    data class State(
        val minimalAppearance: Boolean,
        val hideReels: Boolean,
        val hideExplore: Boolean,
        val hideDistractions: Boolean,
        val hideSuggestions: Boolean,
    )

    fun show(
        context: Context,
        state: State,
        onChange: (State) -> Unit,
        onReload: () -> Unit,
        onAccount: () -> Unit,
    ): Dialog {
        val style = FocusStyle(context)
        val match = ViewGroup.LayoutParams.MATCH_PARENT
        val wrap = ViewGroup.LayoutParams.WRAP_CONTENT
        var current = state
        var resetting = false
        val switches = mutableListOf<Switch>()
        val dialog = Dialog(context).apply { requestWindowFeature(Window.FEATURE_NO_TITLE) }
        val shell = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(style.dp(24), style.dp(26), style.dp(24), style.dp(16))
            background = style.shape(style.surface, 28)
        }
        shell.addView(style.label(R.string.settings_title, 25f, medium = true).also(style::heading))
        shell.addView(style.label(R.string.settings_subtitle, 14f, style.muted),
            LinearLayout.LayoutParams(match, wrap).apply { topMargin = style.dp(8) })
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, style.dp(8), 0, style.dp(8))
        }

        fun section(label: Int) {
            content.addView(style.label(label, 11f, style.muted, medium = true).apply {
                letterSpacing = 0.1f
                setPadding(0, style.dp(22), 0, style.dp(8))
                style.heading(this)
            }, LinearLayout.LayoutParams(match, wrap))
        }

        fun option(label: Int, detail: Int, checked: Boolean, update: (State, Boolean) -> State) {
            val row = LinearLayout(context).apply {
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = style.dp(76)
                setPadding(0, style.dp(12), 0, style.dp(12))
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }
            val control = Switch(context).apply {
                id = View.generateViewId()
                isChecked = checked
                contentDescription = context.getString(label) + ". " + context.getString(detail)
                thumbTintList = ColorStateList(
                    arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                    intArrayOf(style.accent, style.muted),
                )
                trackTintList = ColorStateList(
                    arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                    intArrayOf(style.selected, style.divider),
                )
                minWidth = style.dp(52)
                minHeight = style.dp(48)
                setOnCheckedChangeListener { _, enabled ->
                    if (!resetting) {
                        current = update(current, enabled)
                        onChange(current)
                    }
                }
            }
            val labels = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            }
            labels.addView(style.label(label, 15f, medium = true).apply { labelFor = control.id })
            labels.addView(style.label(detail, 12f, style.muted),
                LinearLayout.LayoutParams(match, wrap).apply { topMargin = style.dp(5) })
            row.addView(labels, LinearLayout.LayoutParams(0, wrap, 1f).apply { marginEnd = style.dp(12) })
            row.addView(control, LinearLayout.LayoutParams(wrap, wrap))
            row.setOnClickListener { control.isChecked = !control.isChecked }
            row.isFocusable = false
            content.addView(row, LinearLayout.LayoutParams(match, wrap))
            switches.add(control)
        }

        fun action(label: Int, detail: Int? = null, onClick: () -> Unit) {
            val column = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                minimumHeight = style.dp(48)
                gravity = Gravity.CENTER_VERTICAL
                setPadding(style.dp(12), style.dp(12), style.dp(12), style.dp(12))
                background = style.ripple(style.background, 14)
                isFocusable = true
                contentDescription = if (detail == null) context.getString(label)
                    else context.getString(label) + ". " + context.getString(detail)
                setOnClickListener { onClick() }
            }
            column.addView(style.label(label, 14f, style.accent, medium = true).apply {
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            })
            if (detail != null) column.addView(style.label(detail, 12f, style.muted).apply {
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, LinearLayout.LayoutParams(match, wrap).apply { topMargin = style.dp(5) })
            content.addView(column, LinearLayout.LayoutParams(match, wrap).apply { topMargin = style.dp(8) })
        }

        section(R.string.section_appearance)
        option(R.string.minimal_appearance, R.string.minimal_appearance_detail, state.minimalAppearance) { s, v ->
            s.copy(minimalAppearance = v)
        }
        section(R.string.section_focus)
        option(R.string.hide_reels, R.string.hide_reels_detail, state.hideReels) { s, v -> s.copy(hideReels = v) }
        option(R.string.hide_explore, R.string.hide_explore_detail, state.hideExplore) { s, v -> s.copy(hideExplore = v) }
        option(R.string.hide_distractions, R.string.hide_distractions_detail, state.hideDistractions) { s, v ->
            s.copy(hideDistractions = v)
        }
        option(R.string.hide_suggestions, R.string.hide_suggestions_detail, state.hideSuggestions) { s, v ->
            s.copy(hideSuggestions = v)
        }
        content.addView(style.label(R.string.settings_filter_note, 12f, style.muted).apply {
            setPadding(0, style.dp(12), 0, style.dp(8))
            setLineSpacing(style.dp(2).toFloat(), 1f)
        }, LinearLayout.LayoutParams(match, wrap))
        action(R.string.restore_original, R.string.restore_original_detail) {
            current = State(false, false, false, false, false)
            resetting = true
            switches.forEach { it.isChecked = false }
            resetting = false
            onChange(current)
        }
        section(R.string.section_account)
        action(R.string.refresh) { dialog.dismiss(); onReload() }
        action(R.string.instagram_settings, R.string.instagram_settings_detail) { dialog.dismiss(); onAccount() }

        // Reserve the title and Done action before allocating scrolling space, even
        // in landscape or with large system text. The dialog stays within the screen.
        val scroll = ScrollView(context).apply {
            isFillViewport = false
            clipToPadding = false
            addView(content)
        }
        shell.addView(scroll, LinearLayout.LayoutParams(match, 0, 1f))
        shell.addView(style.action(R.string.done, filled = true) { dialog.dismiss() },
            LinearLayout.LayoutParams(match, wrap).apply { topMargin = style.dp(12) })
        dialog.setContentView(shell)
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setDimAmount(0.45f)
            setWindowAnimations(0)
        }
        dialog.show()
        dialog.window?.setLayout(
            minOf(context.resources.displayMetrics.widthPixels - style.dp(32), style.dp(560)),
            minOf((context.resources.displayMetrics.heightPixels * 0.88f).toInt(), style.dp(800)),
        )
        return dialog
    }
}
