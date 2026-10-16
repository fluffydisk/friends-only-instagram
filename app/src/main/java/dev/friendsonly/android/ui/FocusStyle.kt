package dev.friendsonly.android.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.view.Gravity
import android.view.View
import android.widget.TextView
import dev.friendsonly.android.R

/** Shared native colors follow the system day/night theme. No remote assets are used. */
internal class FocusStyle(private val context: Context) {
    val background = context.getColor(R.color.focus_background)
    val surface = context.getColor(R.color.focus_surface)
    val text = context.getColor(R.color.focus_text)
    val muted = context.getColor(R.color.focus_muted)
    val accent = context.getColor(R.color.focus_accent)
    val selected = context.getColor(R.color.focus_selected)
    val divider = context.getColor(R.color.focus_divider)

    fun dp(value: Int) = (value * context.resources.displayMetrics.density + 0.5f).toInt()

    fun shape(color: Int, radius: Int = 18) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
    }

    fun ripple(color: Int, radius: Int = 18) = RippleDrawable(
        ColorStateList.valueOf(Color.argb(32, Color.red(accent), Color.green(accent), Color.blue(accent))),
        shape(color, radius),
        shape(Color.WHITE, radius),
    )

    fun label(label: Int, size: Float = 14f, color: Int = text, medium: Boolean = false) =
        TextView(context).apply {
            setText(label)
            textSize = size
            setTextColor(color)
            typeface = Typeface.create(if (medium) "sans-serif-medium" else "sans-serif", Typeface.NORMAL)
            includeFontPadding = false
        }

    fun heading(view: View) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) view.isAccessibilityHeading = true
    }

    fun action(label: Int, filled: Boolean = false, onClick: () -> Unit) =
        FocusActionView(context).apply {
            setText(label)
            textSize = 14f
            setTextColor(accent)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            gravity = Gravity.CENTER
            minHeight = dp(48)
            setPadding(dp(16), dp(12), dp(16), dp(12))
            background = ripple(if (filled) selected else Color.TRANSPARENT, 16)
            setOnClickListener { onClick() }
        }
}

/** Text actions expose the same accessibility role as standard buttons. */
internal class FocusActionView(context: Context) : TextView(context) {
    override fun getAccessibilityClassName(): CharSequence = android.widget.Button::class.java.name
}
