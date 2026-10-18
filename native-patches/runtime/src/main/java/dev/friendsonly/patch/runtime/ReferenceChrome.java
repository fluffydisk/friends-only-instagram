package dev.friendsonly.patch.runtime;

import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.view.*;
import android.widget.*;
import java.lang.ref.WeakReference;
import java.util.*;

/** Reference-led presentation of existing native controls. No account/model/message access. */
public final class ReferenceChrome {
    private static final String HEADER = "instagram.features.feed.mainfeed.actionbar.MainFeedActionBar";
    private static final String STORY = "X.06hD";
    private static final WeakHashMap<View, WeakReference<Header>> HEADERS = new WeakHashMap<>();
    private static final WeakHashMap<Paint, ColorFilter> ORIGINAL_FILTERS = new WeakHashMap<>();
    private static final ColorFilter NEUTRAL_RING = new PorterDuffColorFilter(0xff96989c, PorterDuff.Mode.SRC_IN);
    private static final WeakHashMap<ImageView, AvatarPaint> AVATARS = new WeakHashMap<>();
    private ReferenceChrome() {}

    /** Direct constructor hook, independent of the tab-bar's ancestor search. */
    public static void homeHeader(View view) {
        if (!(view instanceof FrameLayout) || !ReferenceDesignPolicy.header(view.getClass().getName())) return;
        try {
            WeakReference<Header> reference = HEADERS.get(view);
            Header header = reference == null ? null : reference.get();
            if (header == null) {
                header = new Header((FrameLayout) view);
                HEADERS.put(view, new WeakReference<>(header));
                view.addOnAttachStateChangeListener(header);
                view.addOnLayoutChangeListener(header);
            }
            header.queue();
        } catch (RuntimeException ignored) { /* Original controls and fallback wordmark remain. */ }
    }

    /** Reuses the existing throttled tab observer; no additional window observer or timer. */
    static void refreshHeaders() {
        for (WeakReference<Header> reference : new ArrayList<>(HEADERS.values())) {
            Header header = reference.get();
            if (header != null) header.queue();
        }
    }

    private static final class Header implements View.OnAttachStateChangeListener, View.OnLayoutChangeListener, Runnable {
        final WeakReference<FrameLayout> root;
        final ArrayList<WeakReference<View>> targets = new ArrayList<>();
        final WeakHashMap<View, Integer> accessibility = new WeakHashMap<>();
        WeakReference<FrameLayout> shell = new WeakReference<>(null);
        long last;
        boolean pending;
        Header(FrameLayout root) { this.root = new WeakReference<>(root); }
        void queue() {
            FrameLayout view = root.get();
            if (pending || view == null || !view.isAttachedToWindow() || !view.isShown()) return;
            pending = true;
            view.postDelayed(this, Math.max(0, 250 - (SystemClock.uptimeMillis() - last)));
        }
        @Override public void onViewAttachedToWindow(View view) { queue(); }
        @Override public void onViewDetachedFromWindow(View view) { view.removeCallbacks(this); pending = false; }
        @Override public void onLayoutChange(View v, int l, int t, int r, int b, int ol, int ot, int or, int ob) { queue(); }
        @Override public void run() {
            pending = false;
            FrameLayout view = root.get();
            if (view == null || !view.isAttachedToWindow() || !view.isShown()) return;
            last = SystemClock.uptimeMillis();
            try { update(view); } catch (RuntimeException ignored) { restore(view); }
        }
        void update(FrameLayout host) {
            View wordmark = named(host, "title_logo");
            if (wordmark != null) wordmark.setContentDescription("Friends Only");
            FrameLayout existing = shell.get();
            View title = named(host, "action_bar_title_view");
            ArrayList<View> current = controls(host, title, existing);
            // Unknown variants keep every original control; the resource wordmark is still own branding.
            if (current.isEmpty() || current.size() > 4) { restore(host); return; }
            boolean same = existing != null && current.size() == targets.size();
            for (int i = 0; same && i < current.size(); i++) same = current.get(i) == targets.get(i).get();
            if (!same) {
                restore(host);
                DesignSystem.Colors colors = new DesignSystem.Colors(QuietChrome.isDarkTheme(host));
                FrameLayout panel = new FrameLayout(host.getContext());
                panel.setTag("friends_only_reference_header");
                panel.setClickable(true);
                panel.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
                TextView brand = DesignSystem.label(host.getContext(), "Friends Only", 21, colors.text, false);
                brand.setLetterSpacing(0.10f);
                brand.setGravity(Gravity.CENTER_VERTICAL);
                brand.setMaxLines(1);
                brand.setEllipsize(android.text.TextUtils.TruncateAt.END);
                DesignSystem.heading(brand);
                View titleTarget = firstClick(title);
                if (titleTarget != null) {
                    WeakReference<View> nativeTitle = new WeakReference<>(titleTarget);
                    brand.setClickable(true);
                    brand.setOnClickListener(v -> click(nativeTitle, false));
                    brand.setOnLongClickListener(v -> click(nativeTitle, true));
                }
                FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(-1, -1, Gravity.START);
                bp.setMarginStart(DesignSystem.dp(host.getContext(), 20));
                bp.setMarginEnd(DesignSystem.dp(host.getContext(), 20 + 48 * current.size()));
                panel.addView(brand, bp);
                for (int i = 0; i < current.size(); i++) {
                    View nativeTarget = current.get(i);
                    WeakReference<View> weakTarget = new WeakReference<>(nativeTarget);
                    targets.add(weakTarget);
                    ImageButton button = new ImageButton(host.getContext());
                    ImageView nativeImage = firstImage(nativeTarget);
                    Drawable source = nativeImage == null ? null : nativeImage.getDrawable();
                    if (source != null && source.getConstantState() != null) {
                        button.setImageDrawable(source.getConstantState().newDrawable(host.getResources()).mutate());
                    } else { restore(host); return; }
                    button.setImageTintList(android.content.res.ColorStateList.valueOf(colors.text));
                    button.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                    int inset = DesignSystem.dp(host.getContext(), 12);
                    button.setPadding(inset, inset, inset, inset);
                    button.setBackground(new android.graphics.drawable.RippleDrawable(
                            android.content.res.ColorStateList.valueOf(colors.line),
                            DesignSystem.surface(host.getContext(), colors.canvas, 12), null));
                    CharSequence label = nativeTarget.getContentDescription();
                    if (label == null && nativeImage != null) label = nativeImage.getContentDescription();
                    button.setContentDescription(label);
                    // Never ship a misleading/unlabelled proxy for an unknown control.
                    if (label == null || label.length() == 0) { restore(host); return; }
                    button.setEnabled(nativeTarget.isEnabled());
                    button.setOnClickListener(v -> click(weakTarget, false));
                    button.setOnLongClickListener(v -> click(weakTarget, true));
                    FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(
                            DesignSystem.dp(host.getContext(), 48), -1, Gravity.END);
                    cp.setMarginEnd(DesignSystem.dp(host.getContext(), 12 + (current.size() - 1 - i) * 48));
                    panel.addView(button, cp);
                }
                host.addView(panel, new FrameLayout.LayoutParams(-1, -1));
                shell = new WeakReference<>(panel);
                existing = panel;
            }
            DesignSystem.Colors colors = new DesignSystem.Colors(QuietChrome.isDarkTheme(host));
            existing.setBackgroundColor(colors.canvas);
            for (int i = 0; i < host.getChildCount(); i++) {
                View child = host.getChildAt(i);
                if (child == existing) continue;
                if (!accessibility.containsKey(child)) accessibility.put(child, child.getImportantForAccessibility());
                child.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
            }
            if (host.getChildAt(host.getChildCount() - 1) != existing) existing.bringToFront();
        }
        void restore(FrameLayout host) {
            FrameLayout panel = shell.get();
            if (panel != null && panel.getParent() == host) host.removeView(panel);
            for (Map.Entry<View, Integer> entry : accessibility.entrySet()) entry.getKey().setImportantForAccessibility(entry.getValue());
            accessibility.clear(); targets.clear(); shell.clear();
        }
    }

    private static boolean click(WeakReference<View> reference, boolean longClick) {
        View nativeView = reference.get();
        if (nativeView == null || !nativeView.isEnabled()) return false;
        return longClick ? nativeView.performLongClick() : nativeView.performClick();
    }
    private static View named(View root, String name) {
        if (root == null) return null;
        int id = root.getResources().getIdentifier(name, "id", root.getContext().getPackageName());
        return id == 0 ? null : root.findViewById(id);
    }
    private static ArrayList<View> controls(ViewGroup root, View title, View except) {
        ArrayList<View> result = new ArrayList<>();
        ArrayDeque<View> nodes = new ArrayDeque<>();
        for (int i = 0; i < root.getChildCount(); i++) nodes.add(root.getChildAt(i));
        for (int n = 0; !nodes.isEmpty() && n < 64; n++) {
            View view = nodes.removeFirst();
            if (view == title || view == except || !view.isShown()) continue;
            if (view.isClickable() && view.hasOnClickListeners()) { result.add(view); continue; }
            if (view instanceof ViewGroup) {
                ViewGroup group = (ViewGroup) view;
                for (int i = 0; i < group.getChildCount() && nodes.size() < 64; i++) nodes.add(group.getChildAt(i));
            }
        }
        return result;
    }
    private static View firstClick(View view) {
        if (view == null) return null;
        if (view.isClickable() && view.hasOnClickListeners()) return view;
        if (view instanceof ViewGroup) {
            ArrayList<View> found = controls((ViewGroup) view, null, null);
            if (!found.isEmpty()) return found.get(0);
        }
        return null;
    }
    private static ImageView firstImage(View view) {
        if (view instanceof ImageView) return (ImageView) view;
        if (!(view instanceof ViewGroup)) return null;
        ArrayDeque<View> nodes = new ArrayDeque<>(); nodes.add(view);
        for (int n = 0; !nodes.isEmpty() && n < 24; n++) {
            View child = nodes.removeFirst();
            if (child instanceof ImageView) return (ImageView) child;
            if (child instanceof ViewGroup) {
                ViewGroup group = (ViewGroup) child;
                for (int i = 0; i < group.getChildCount() && nodes.size() < 24; i++) nodes.add(group.getChildAt(i));
            }
        }
        return null;
    }

    public static boolean storyAvatar(View view) {
        for (int i = 0; view != null && i < 8; i++) {
            if (ReferenceDesignPolicy.storyContainer(view.getClass().getName())) return true;
            ViewParent parent = view.getParent();
            view = parent instanceof View ? (View) parent : null;
        }
        return false;
    }
    private static final class AvatarPaint {
        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        final Matrix matrix = new Matrix(); final RectF rect = new RectF();
        WeakReference<Bitmap> source = new WeakReference<>(null);
        BitmapShader shader;
    }
    public static boolean drawStoryAvatar(ImageView view, Canvas canvas, Bitmap bitmap) {
        if (bitmap == null || bitmap.isRecycled() || !storyAvatar(view)) return false;
        try {
            AvatarPaint state = AVATARS.get(view);
            if (state == null) { state = new AvatarPaint(); AVATARS.put(view, state); }
            if (state.source.get() != bitmap) {
                state.source = new WeakReference<>(bitmap);
                state.shader = new BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
                state.paint.setShader(state.shader);
            }
            state.rect.set(view.getPaddingLeft(), view.getPaddingTop(),
                    view.getWidth() - view.getPaddingRight(), view.getHeight() - view.getPaddingBottom());
            if (state.rect.width() <= 0 || state.rect.height() <= 0) return false;
            float scale = Math.max(state.rect.width() / bitmap.getWidth(), state.rect.height() / bitmap.getHeight());
            state.matrix.setScale(scale, scale);
            state.matrix.postTranslate(state.rect.left + (state.rect.width() - bitmap.getWidth() * scale) / 2,
                    state.rect.top + (state.rect.height() - bitmap.getHeight() * scale) / 2);
            state.shader.setLocalMatrix(state.matrix);
            state.paint.setAlpha(view.getImageAlpha());
            state.paint.setColorFilter(view.getColorFilter());
            float radius = DesignSystem.dp(view.getContext(), 14);
            canvas.drawRoundRect(state.rect, radius, radius, state.paint);
            return true;
        } catch (RuntimeException ignored) { return false; }
    }

    /** Neutralize just tray-ring colors; retain native ring progress, animation, alpha and errors. */
    public static void storyRing(View view, Paint first, Paint second, Paint third) {
        boolean story = storyAvatar(view);
        tint(first, story); tint(second, story); tint(third, story);
    }
    private static void tint(Paint paint, boolean story) {
        if (paint == null) return;
        if (story && paint.getShader() != null) {
            if (paint.getColorFilter() != NEUTRAL_RING) ORIGINAL_FILTERS.put(paint, paint.getColorFilter());
            paint.setColorFilter(NEUTRAL_RING);
        } else if (ORIGINAL_FILTERS.containsKey(paint)) {
            if (paint.getColorFilter() == NEUTRAL_RING) paint.setColorFilter(ORIGINAL_FILTERS.get(paint));
            ORIGINAL_FILTERS.remove(paint);
        }
    }
}
