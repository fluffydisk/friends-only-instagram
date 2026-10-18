package dev.friendsonly.patch.runtime;

import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.AbsListView;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.TextView;

import java.lang.ref.WeakReference;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Locale;
import java.util.WeakHashMap;

/**
 * A small, native Friends Only shell over the supported app's real navigation and Home toolbar.
 *
 * The content, navigation click listeners and media interactions stay with Instagram. This class
 * neither reads content nor creates a substitute feed. It runs from the existing throttled tab
 * observer; there is no extra global observer, timer, whole-screen polling or touch interception.
 */
final class QuietChrome {
    private static final int MAX_TABS = 96;
    private static final int MAX_TAB_DEPTH = 5;
    private static final int MAX_HEADER_VIEWS = 256;
    private static final int MAX_HEADER_DEPTH = 8;
    private static final String[] TAB_NAMES = {"feed_tab", "direct_tab", "profile_tab"};
    // Controllers must never strongly retain their weak map keys or an Activity context.
    private static final WeakHashMap<ViewGroup, Controller> CONTROLLERS = new WeakHashMap<>();
    private static final WeakHashMap<ImageView, Boolean> STARTUP_LOGOS = new WeakHashMap<>();

    private QuietChrome() {}

    /** Called on the main thread at the end of the existing bounded tab-controller update. */
    static void apply(ViewGroup tabBar, boolean enabled, Runnable settingsAction) {
        if (tabBar == null) return;
        if (!enabled) {
            detach(tabBar);
            return;
        }
        Controller controller = CONTROLLERS.get(tabBar);
        if (controller == null) {
            controller = new Controller();
            CONTROLLERS.put(tabBar, controller);
        }
        try {
            controller.apply(tabBar, settingsAction);
        } catch (RuntimeException ignored) {
            // A server-configured or changed native layout falls back to its existing controls.
            controller.restore();
        }
    }

    /** Removes only our decoration when the native tab root is detached or the setting is off. */
    static void detach(ViewGroup tabBar) {
        Controller controller = CONTROLLERS.remove(tabBar);
        if (controller != null) controller.restore();
    }

    /** Prefer the native screen's explicit theme override over the phone's system night mode. */
    static boolean isDarkTheme(View view) {
        try {
            int attribute = view.getResources().getIdentifier("igds_color_primary_background", "attr",
                    view.getContext().getPackageName());
            TypedValue resolved = new TypedValue();
            if (attribute != 0 && view.getContext().getTheme().resolveAttribute(attribute, resolved, true)) {
                Integer color = null;
                if (resolved.type >= TypedValue.TYPE_FIRST_COLOR_INT && resolved.type <= TypedValue.TYPE_LAST_COLOR_INT) {
                    color = resolved.data;
                } else if (resolved.resourceId != 0) {
                    color = view.getResources().getColor(resolved.resourceId, view.getContext().getTheme());
                }
                if (color != null && (color >>> 24) >= 128) {
                    int red = (color >>> 16) & 255;
                    int green = (color >>> 8) & 255;
                    int blue = color & 255;
                    return 299 * red + 587 * green + 114 * blue < 128000;
                }
            }
        } catch (RuntimeException ignored) {
            // A changed native theme may remove or change this optional color attribute.
        }
        return (view.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
    }

    private static final class Controller {
        private final ArrayList<TabDecoration> tabs = new ArrayList<>();
        private HeaderDecoration header;

        void apply(ViewGroup tabBar, Runnable settingsAction) {
            Palette palette = new Palette(tabBar);
            String language = DesignSystem.language(tabBar.getContext());
            View home = null;
            for (String resource : TAB_NAMES) {
                View tab = findNamed(tabBar, resource, MAX_TABS, MAX_TAB_DEPTH, false);
                if (tab == null) continue;
                if ("feed_tab".equals(resource)) home = tab;
                TabDecoration decoration = decorationFor(tab);
                decoration.update(tab, QuietChromePolicy.tabLabel(resource, language), palette);
            }
            for (Iterator<TabDecoration> previous = tabs.iterator(); previous.hasNext();) {
                TabDecoration decoration = previous.next();
                View view = decoration.view.get();
                if (view == null || !within(view, tabBar, MAX_TAB_DEPTH)) {
                    decoration.restore();
                    previous.remove();
                }
            }
            // One bounded scan also removes two exact promotional controls.
            // Content lists, account/login, Story viewers and generic back/edit controls stay out.
            FrameLayout discoveredHeader = findMainChrome(tabBar);
            if (home == null || !selectedWithin(home) || !tabBar.isShown()) {
                restoreHeader();
                return;
            }
            FrameLayout nativeHeader = header == null ? null : header.host.get();
            if (nativeHeader == null || !nativeHeader.isAttachedToWindow() || !nativeHeader.isShown()) {
                restoreHeader();
                nativeHeader = discoveredHeader;
            }
            if (nativeHeader == null || !nativeHeader.isShown() || nativeHeader.getHeight() == 0) return;
            if (header == null) header = new HeaderDecoration(nativeHeader, settingsAction);
            header.update(palette);
        }

        private TabDecoration decorationFor(View view) {
            for (TabDecoration decoration : tabs) if (decoration.view.get() == view) return decoration;
            TabDecoration decoration = new TabDecoration(view);
            tabs.add(decoration);
            return decoration;
        }

        private void restoreHeader() {
            if (header != null) header.restore();
            header = null;
        }

        void restore() {
            restoreHeader();
            for (TabDecoration decoration : tabs) decoration.restore();
            tabs.clear();
        }
    }

    private static final class TabDecoration {
        final WeakReference<View> view;
        private final CharSequence originalDescription;
        private final int originalAccessibility;
        private final ArrayList<AccessibilityState> children = new ArrayList<>();
        private final ArrayList<BadgeState> badges = new ArrayList<>();
        private final WeakReference<TabLabelDrawable> label;

        TabDecoration(View nativeTab) {
            view = new WeakReference<>(nativeTab);
            originalDescription = nativeTab.getContentDescription();
            originalAccessibility = nativeTab.getImportantForAccessibility();
            TabLabelDrawable drawable = new TabLabelDrawable(nativeTab);
            label = new WeakReference<>(drawable);
            nativeTab.getOverlay().add(drawable);
        }

        void update(View nativeTab, String text, Palette palette) {
            TabLabelDrawable drawable = label.get();
            if (drawable == null || text == null) return;
            // The opaque decoration covers the familiar glyph, profile photo and numeric/red
            // badge. The actual tab button and its ordinary/long-click listeners stay intact.
            drawable.update(nativeTab.getWidth(), nativeTab.getHeight(), text,
                    selectedWithin(nativeTab), palette);
            if (!text.contentEquals(nativeTab.getContentDescription() == null
                    ? "" : nativeTab.getContentDescription())) nativeTab.setContentDescription(text);
            if (nativeTab.getImportantForAccessibility() != View.IMPORTANT_FOR_ACCESSIBILITY_YES) {
                nativeTab.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
            }
            maskDirectChildren(nativeTab, children, null);
            hideBadge(nativeTab, "notification");
            hideBadge(nativeTab, "tab_button_count");
            hideBadge(nativeTab, "profile_tab_badge_count_view");
        }

        private void hideBadge(View nativeTab, String name) {
            for (Iterator<BadgeState> previous = badges.iterator(); previous.hasNext();) {
                BadgeState state = previous.next();
                View badge = state.view.get();
                if (badge == null || !(nativeTab instanceof ViewGroup)
                        || !within(badge, (ViewGroup) nativeTab, 4)) {
                    if (badge != null && badge.getVisibility() == View.GONE) badge.setVisibility(state.visibility);
                    previous.remove();
                }
            }
            View badge = findNamed(nativeTab, name, 32, 4, false);
            if (badge == null || badge == nativeTab) return;
            boolean known = false;
            for (BadgeState state : badges) if (state.view.get() == badge) { known = true; break; }
            if (!known) badges.add(new BadgeState(badge));
            if (badge.getVisibility() != View.GONE) badge.setVisibility(View.GONE);
        }

        void restore() {
            View nativeTab = view.get();
            TabLabelDrawable drawable = label.get();
            if (nativeTab != null) {
                if (drawable != null) nativeTab.getOverlay().remove(drawable);
                nativeTab.setContentDescription(originalDescription);
                nativeTab.setImportantForAccessibility(originalAccessibility);
            }
            restoreAccessibility(children);
            for (BadgeState state : badges) {
                View badge = state.view.get();
                if (badge != null && badge.getVisibility() == View.GONE) badge.setVisibility(state.visibility);
            }
            badges.clear();
        }
    }

    private static final class BadgeState {
        final WeakReference<View> view;
        final int visibility;
        BadgeState(View view) { this.view = new WeakReference<>(view); visibility = view.getVisibility(); }
    }

    private static final class HeaderDecoration {
        final WeakReference<FrameLayout> host;
        private final WeakReference<FrameLayout> replacement;
        private final WeakReference<TextView> title;
        private final WeakReference<TextView> settings;
        private final ArrayList<AccessibilityState> children = new ArrayList<>();
        private Boolean lastDark;

        HeaderDecoration(FrameLayout nativeHeader, final Runnable settingsAction) {
            host = new WeakReference<>(nativeHeader);
            FrameLayout shell = new FrameLayout(nativeHeader.getContext());
            shell.setTag("friends_only_home_toolbar");
            shell.setClickable(true);
            shell.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);

            TextView brand = new TextView(nativeHeader.getContext());
            brand.setText(QuietChromePolicy.tabLabel("feed_tab", DesignSystem.language(nativeHeader.getContext())));
            brand.setTextSize(24);
            brand.setTypeface(DesignSystem.font(nativeHeader.getContext(), true));
            brand.setGravity(Gravity.CENTER_VERTICAL);
            brand.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
            if (Build.VERSION.SDK_INT >= 28) brand.setAccessibilityHeading(true);
            FrameLayout.LayoutParams brandParams = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT,
                    Gravity.START | Gravity.CENTER_VERTICAL);
            brandParams.setMarginStart(dp(nativeHeader, 20));
            brandParams.setMarginEnd(dp(nativeHeader, 104));
            shell.addView(brand, brandParams);

            TextView preferences = new TextView(nativeHeader.getContext());
            preferences.setText(DesignSystem.text(nativeHeader.getContext(), "Ayarlar", "Settings", "Optionen"));
            preferences.setContentDescription(DesignSystem.text(nativeHeader.getContext(), "Friends Only ayarları", "Friends Only settings", "Friends Only Einstellungen"));
            preferences.setTextSize(14);
            preferences.setTypeface(DesignSystem.font(nativeHeader.getContext(), true));
            preferences.setGravity(Gravity.CENTER);
            preferences.setFocusable(true);
            preferences.setClickable(true);
            preferences.setMinWidth(dp(nativeHeader, 76));
            preferences.setMinHeight(dp(nativeHeader, 48));
            preferences.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View ignored) {
                    try {
                        if (settingsAction != null) settingsAction.run();
                    } catch (RuntimeException ignoredFailure) {
                        // The native Activity may be finishing while this optional button fires.
                    }
                }
            });
            FrameLayout.LayoutParams preferencesParams = new FrameLayout.LayoutParams(
                    dp(nativeHeader, 84), ViewGroup.LayoutParams.MATCH_PARENT,
                    Gravity.END | Gravity.CENTER_VERTICAL);
            preferencesParams.setMarginEnd(dp(nativeHeader, 12));
            shell.addView(preferences, preferencesParams);
            nativeHeader.addView(shell, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            replacement = new WeakReference<>(shell);
            title = new WeakReference<>(brand);
            settings = new WeakReference<>(preferences);
        }

        void update(Palette palette) {
            FrameLayout nativeHeader = host.get();
            FrameLayout shell = replacement.get();
            TextView brand = title.get();
            TextView preferences = settings.get();
            if (nativeHeader == null || shell == null || brand == null || preferences == null) return;
            maskDirectChildren(nativeHeader, children, shell);
            // A native badge or button can be inserted after our first attach. The replacement
            // remains on top of the Home toolbar only, with no window-wide overlay.
            if (nativeHeader.getChildAt(nativeHeader.getChildCount() - 1) != shell) shell.bringToFront();
            if (lastDark != null && lastDark.booleanValue() == palette.dark) return;
            lastDark = palette.dark;
            shell.setBackgroundColor(palette.background);
            brand.setTextColor(palette.text);
            preferences.setTextColor(palette.secondary);
            GradientDrawable background = new GradientDrawable();
            background.setColor(palette.background);
            background.setCornerRadius(dp(nativeHeader, 12));
            background.setStroke(dp(nativeHeader, 1), palette.outline);
            preferences.setBackground(background);
        }

        void restore() {
            FrameLayout nativeHeader = host.get();
            FrameLayout shell = replacement.get();
            if (nativeHeader != null && shell != null && shell.getParent() == nativeHeader) {
                nativeHeader.removeView(shell);
            }
            restoreAccessibility(children);
        }
    }

    private static final class AccessibilityState {
        final WeakReference<View> view;
        final int original;
        AccessibilityState(View view) {
            this.view = new WeakReference<>(view);
            original = view.getImportantForAccessibility();
        }
    }

    private static void maskDirectChildren(View root, ArrayList<AccessibilityState> states, View except) {
        if (!(root instanceof ViewGroup)) return;
        ViewGroup group = (ViewGroup) root;
        for (Iterator<AccessibilityState> previous = states.iterator(); previous.hasNext();) {
            AccessibilityState state = previous.next();
            View child = state.view.get();
            if (child == null || child.getParent() != group) {
                if (child != null) child.setImportantForAccessibility(state.original);
                previous.remove();
            }
        }
        // Native tab and toolbar containers are small. Unexpected children beyond this bound
        // remain unchanged; feed rows, message text and Story content are never inspected.
        for (int i = 0; i < group.getChildCount() && i < 32; i++) {
            View child = group.getChildAt(i);
            if (child == except) continue;
            boolean known = false;
            for (AccessibilityState state : states) if (state.view.get() == child) { known = true; break; }
            if (!known) states.add(new AccessibilityState(child));
            if (child.getImportantForAccessibility() != View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS) {
                child.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
            }
        }
    }

    private static void restoreAccessibility(ArrayList<AccessibilityState> states) {
        for (AccessibilityState state : states) {
            View view = state.view.get();
            if (view != null) view.setImportantForAccessibility(state.original);
        }
        states.clear();
    }

    private static final class TabLabelDrawable extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float density;
        private final float textSize;
        private String text = "";
        private boolean selected;
        private Palette palette;

        TabLabelDrawable(View view) {
            density = view.getResources().getDisplayMetrics().density;
            textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 12.5f,
                    view.getResources().getDisplayMetrics());
            paint.setTypeface(DesignSystem.font(view.getContext(), true));
            paint.setTextAlign(Paint.Align.CENTER);
        }

        void update(int width, int height, String text, boolean selected, Palette palette) {
            boolean changed = getBounds().width() != width || getBounds().height() != height
                    || !this.text.equals(text) || this.selected != selected
                    || this.palette == null || this.palette.dark != palette.dark;
            this.text = text;
            this.selected = selected;
            this.palette = palette;
            if (getBounds().width() != width || getBounds().height() != height) setBounds(0, 0, width, height);
            if (changed) invalidateSelf();
        }

        @Override public void draw(Canvas canvas) {
            if (palette == null) return;
            paint.setColor(palette.background);
            canvas.drawRect(getBounds(), paint);
            float centerX = getBounds().exactCenterX();
            float centerY = getBounds().exactCenterY();
            // Flat text navigation with one quiet underline, no selected pill.
            if (selected) {
                paint.setColor(palette.text);
                float halfWidth = Math.min(18 * density, getBounds().width() / 4f);
                canvas.drawRect(centerX - halfWidth, getBounds().bottom - 4 * density,
                        centerX + halfWidth, getBounds().bottom - 2 * density, paint);
            }
            paint.setTextSize(textSize);
            float availableWidth = Math.max(0, getBounds().width() - 16 * density);
            float measuredWidth = paint.measureText(text);
            if (measuredWidth > availableWidth && measuredWidth > 0) {
                paint.setTextSize(textSize * availableWidth / measuredWidth);
            }
            paint.setColor(selected ? palette.text : palette.secondary);
            Paint.FontMetrics metrics = paint.getFontMetrics();
            canvas.drawText(text, centerX, centerY - (metrics.ascent + metrics.descent) / 2f, paint);
        }

        @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); invalidateSelf(); }
        @Override public void setColorFilter(ColorFilter filter) { paint.setColorFilter(filter); invalidateSelf(); }
        @SuppressWarnings("deprecation")
        @Override public int getOpacity() { return PixelFormat.OPAQUE; }
    }

    private static final class Palette {
        final boolean dark;
        final int background;
        final int text;
        final int secondary;
        final int selected;
        final int outline;

        Palette(View view) {
            dark = isDarkTheme(view);
            background = dark ? 0xff151a17 : 0xfff8f7f4;
            text = dark ? 0xfff2f3ee : 0xff242b27;
            secondary = dark ? 0xffa2ada6 : 0xff626f67;
            selected = dark ? 0xff29332e : 0xffe5ebe3;
            outline = dark ? 0xff3c4841 : 0xffd6ddd4;
        }
    }

    private static FrameLayout findMainChrome(ViewGroup tabBar) {
        ViewGroup scope = null;
        View cursor = tabBar;
        for (int i = 0; i < 4; i++) {
            ViewParent parent = cursor.getParent();
            if (!(parent instanceof ViewGroup)) break;
            cursor = (ViewGroup) parent;
            if ("layout_container_main_wrapper".equals(nameOf(cursor))) { scope = (ViewGroup) cursor; break; }
        }
        // Do not scan the whole Activity/window when the supported main-wrapper is absent.
        if (scope == null) return null;
        ArrayDeque<Node> pending = new ArrayDeque<>();
        pending.add(new Node(scope, 0));
        int visited = 0;
        FrameLayout homeHeader = null;
        while (!pending.isEmpty() && visited++ < MAX_HEADER_VIEWS) {
            Node node = pending.removeFirst();
            View view = node.view;
            replaceStartupLogo(view);
            String resource = nameOf(view);
            // Exact promotional controls only; neither message content nor ordinary profile
            // buttons qualify, and content RecyclerViews are never traversed.
            if ("profile_header_barcelona_app_switcher_button".equals(resource)
                    || "direct_composer_meta_ai_composer_upsell".equals(resource)) {
                if (view.getVisibility() != View.GONE) view.setVisibility(View.GONE);
            }
            if (view instanceof FrameLayout && QuietChromePolicy.isHomeHeaderClass(view.getClass().getName())) {
                if (view.isShown()) homeHeader = (FrameLayout) view;
                continue;
            }
            if (node.depth >= MAX_HEADER_DEPTH || isContentList(view) || !(view instanceof ViewGroup)) continue;
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount() && pending.size() + visited < MAX_HEADER_VIEWS; i++) {
                pending.addLast(new Node(group.getChildAt(i), node.depth + 1));
            }
        }
        return homeHeader;
    }

    private static void replaceStartupLogo(View view) {
        // The supported internal MainActivity startup logo only. Login/authentication logos are
        // outside this exact-name, main-wrapper-scoped search and are never modified here.
        if (!(view instanceof ImageView) || !"main_activity_splash_logo".equals(nameOf(view))
                || STARTUP_LOGOS.containsKey((ImageView) view)) return;
        int icon = view.getResources().getIdentifier("friends_only_icon", "drawable",
                view.getContext().getPackageName());
        if (icon != 0) {
            ((ImageView) view).setImageResource(icon);
            STARTUP_LOGOS.put((ImageView) view, Boolean.TRUE);
        }
    }

    private static boolean isContentList(View view) {
        if (view instanceof AbsListView || view instanceof ScrollView || view instanceof HorizontalScrollView) return true;
        Class<?> type = view.getClass();
        for (int depth = 0; type != null && depth < 7; depth++, type = type.getSuperclass()) {
            if ("androidx.recyclerview.widget.RecyclerView".equals(type.getName())) {
                // ViewPager2 itself hosts pages in one RecyclerView. Descend through that page
                // host, then stop at the feed/message/media lists inside the selected page.
                ViewParent parent = view.getParent();
                if (parent instanceof View && ("swipeable_tab_view_pager".equals(nameOf((View) parent))
                        || "androidx.viewpager2.widget.ViewPager2".equals(parent.getClass().getName()))) return false;
                return true;
            }
        }
        return false;
    }

    private static View findNamed(View root, String wanted, int limit, int maxDepth, boolean selectionOnly) {
        ArrayDeque<Node> pending = new ArrayDeque<>();
        pending.add(new Node(root, 0));
        int visited = 0;
        while (!pending.isEmpty() && visited++ < limit) {
            Node node = pending.removeFirst();
            if (selectionOnly ? node.view.isSelected() || node.view.isActivated() : wanted.equals(nameOf(node.view))) {
                return node.view;
            }
            if (node.depth >= maxDepth || !(node.view instanceof ViewGroup)) continue;
            ViewGroup group = (ViewGroup) node.view;
            for (int i = 0; i < group.getChildCount() && pending.size() + visited < limit; i++) {
                pending.addLast(new Node(group.getChildAt(i), node.depth + 1));
            }
        }
        return null;
    }

    private static boolean selectedWithin(View tab) { return findNamed(tab, "", 24, 3, true) != null; }

    private static boolean within(View view, ViewGroup parent, int maxDepth) {
        for (int i = 0; i <= maxDepth; i++) {
            if (view == parent) return true;
            if (!(view.getParent() instanceof View)) return false;
            view = (View) view.getParent();
        }
        return false;
    }

    private static String nameOf(View view) {
        if (view.getId() == View.NO_ID) return "";
        try {
            return view.getResources().getResourceEntryName(view.getId());
        } catch (RuntimeException ignored) {
            return "";
        }
    }

    private static int dp(View view, int value) {
        return Math.round(value * view.getResources().getDisplayMetrics().density);
    }

    private static final class Node {
        final View view;
        final int depth;
        Node(View view, int depth) { this.view = view; this.depth = depth; }
    }
}
