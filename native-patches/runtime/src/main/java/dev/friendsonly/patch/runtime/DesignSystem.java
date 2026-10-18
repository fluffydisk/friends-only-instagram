package dev.friendsonly.patch.runtime;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.content.res.ColorStateList;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Shared native design tokens. No account, media or network access. */
final class DesignSystem {
    static final String VERSION = "0.4.0-alpha.1";
    private static Typeface font;
    private static Typeface regular;
    private static Typeface semibold;
    private DesignSystem() {}

    static int dp(Context context, float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    static String language(Context context) {
        return Build.VERSION.SDK_INT >= 24
                ? context.getResources().getConfiguration().getLocales().get(0).getLanguage()
                : context.getResources().getConfiguration().locale.getLanguage();
    }

    static String text(Context context, String tr, String en, String de) {
        String language = language(context);
        return "tr".equals(language) ? tr : "de".equals(language) ? de : en;
    }

    static synchronized Typeface font(Context context, boolean medium) {
        if (font == null) {
            try { font = Typeface.createFromAsset(context.getAssets(), "friends_only/Manrope.ttf"); }
            catch (RuntimeException ignored) { font = Typeface.create("sans-serif", Typeface.NORMAL); }
        }
        if (regular == null) {
            regular = Build.VERSION.SDK_INT >= 28 ? Typeface.create(font, 400, false) : Typeface.create(font, Typeface.NORMAL);
            semibold = Build.VERSION.SDK_INT >= 28 ? Typeface.create(font, 600, false) : Typeface.create(font, Typeface.BOLD);
        }
        return medium ? semibold : regular;
    }

    static final class Colors {
        final boolean dark;
        final int canvas, surface, text, secondary, accent, line;
        Colors(boolean dark) {
            this.dark = dark;
            canvas = dark ? 0xff151a17 : 0xfff8f7f4;
            surface = dark ? 0xff202722 : 0xffedeee9;
            text = dark ? 0xfff2f3ee : 0xff242b27;
            secondary = dark ? 0xffb3bdb5 : 0xff626b65;
            accent = dark ? 0xffc0cfc4 : 0xff344d43;
            line = dark ? 0xff3c4841 : 0xffd6ddd4;
        }
    }

    @SuppressWarnings("deprecation")
    static void window(android.view.Window window, Colors colors) {
        window.setStatusBarColor(colors.canvas);
        window.setNavigationBarColor(colors.canvas);
        View decor = window.getDecorView();
        int flags = decor.getSystemUiVisibility();
        int light = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        if (Build.VERSION.SDK_INT >= 26) light |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        decor.setSystemUiVisibility(colors.dark ? flags & ~light : flags | light);
        if (Build.VERSION.SDK_INT >= 30) window.setDecorFitsSystemWindows(false);
    }

    static GradientDrawable surface(Context context, int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(context, radius));
        return drawable;
    }

    static TextView label(Context context, String value, float size, int color, boolean medium) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setTypeface(font(context, medium));
        view.setIncludeFontPadding(false);
        view.setLineSpacing(dp(context, 3), 1f);
        return view;
    }

    static TextView button(Context context, String value, Colors colors, boolean primary) {
        TextView view = label(context, value, 16, primary ? colors.canvas : colors.text, true);
        view.setGravity(Gravity.CENTER);
        view.setMinHeight(dp(context, 52));
        view.setPadding(dp(context, 16), dp(context, 12), dp(context, 16), dp(context, 12));
        view.setClickable(true);
        view.setFocusable(true);
        view.setBackground(new RippleDrawable(ColorStateList.valueOf(colors.line),
                surface(context, primary ? colors.text : colors.surface, 8), null));
        view.setAccessibilityDelegate(new View.AccessibilityDelegate() {
            @Override public void onInitializeAccessibilityNodeInfo(View host, android.view.accessibility.AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                info.setClassName("android.widget.Button");
            }
        });
        return view;
    }

    static void heading(TextView view) {
        if (Build.VERSION.SDK_INT >= 28) view.setAccessibilityHeading(true);
    }

    static void add(LinearLayout parent, View child, int top) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(parent.getContext(), top);
        parent.addView(child, params);
    }
}
