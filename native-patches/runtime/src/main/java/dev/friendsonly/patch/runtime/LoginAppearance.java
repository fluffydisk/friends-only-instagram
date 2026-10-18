package dev.friendsonly.patch.runtime;

import android.app.*;
import android.content.res.ColorStateList;
import android.graphics.drawable.*;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import java.lang.ref.WeakReference;
import java.util.*;

/** Decorates real native account widgets. Never reads input, hints, labels or account/session state. */
final class LoginAppearance {
    private static boolean registered;
    private static final WeakHashMap<Activity, Binding> BINDINGS = new WeakHashMap<>();
    static void register(Application application) {
        if (registered) return;
        application.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            public void onActivityResumed(Activity activity) {
                CrashSafe.install(activity);
                if (!LoginDesignPolicy.host(activity.getClass().getName())) return;
                Binding binding = BINDINGS.get(activity);
                if (binding == null) { binding = new Binding(activity); BINDINGS.put(activity, binding); }
                binding.attach();
            }
            public void onActivityPaused(Activity activity) { detach(activity); }
            public void onActivityDestroyed(Activity activity) { detach(activity); BINDINGS.remove(activity); }
            public void onActivityCreated(Activity a, Bundle b) {}
            public void onActivityStarted(Activity a) {}
            public void onActivityStopped(Activity a) {}
            public void onActivitySaveInstanceState(Activity a, Bundle b) {}
        });
        registered = true;
    }
    private static void detach(Activity activity) { Binding binding = BINDINGS.get(activity); if (binding != null) binding.detach(); }
    private static final class Binding implements ViewTreeObserver.OnGlobalLayoutListener, Runnable {
        final WeakReference<Activity> activity;
        final WeakReference<View> decor;
        boolean attached, pending;
        long last;
        final WeakHashMap<View, Integer> backgrounds = new WeakHashMap<>();
        Binding(Activity a) { activity = new WeakReference<>(a); decor = new WeakReference<>(a.getWindow().getDecorView()); }
        void attach() {
            View view = decor.get();
            if (view == null || attached) return;
            view.getViewTreeObserver().addOnGlobalLayoutListener(this);
            attached = true;
            onGlobalLayout();
        }
        void detach() {
            View view = decor.get();
            if (view != null) {
                if (view.getViewTreeObserver().isAlive()) view.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                view.removeCallbacks(this);
            }
            attached = pending = false;
        }
        public void onGlobalLayout() {
            View view = decor.get();
            if (!attached || pending || view == null) return;
            pending = true;
            view.postDelayed(this, Math.max(0, 500 - (android.os.SystemClock.uptimeMillis() - last)));
        }
        public void run() {
            pending = false;
            Activity host = activity.get(); View root = decor.get();
            if (!attached || host == null || root == null || host.isFinishing()) return;
            last = android.os.SystemClock.uptimeMillis();
            try {
                DesignSystem.Colors colors = new DesignSystem.Colors(QuietChrome.isDarkTheme(root));
                // Preserve the native keyboard/window insets, focus and navigation behavior.
                host.getWindow().setStatusBarColor(colors.canvas);
                host.getWindow().setNavigationBarColor(colors.canvas);
                Integer previous = backgrounds.get(root);
                if (previous == null || previous != colors.canvas) { root.setBackgroundColor(colors.canvas); backgrounds.put(root, colors.canvas); }
                ArrayDeque<View> queue = new ArrayDeque<>(); queue.add(root);
                for (int visited = 0; !queue.isEmpty() && visited < 256; visited++) {
                    View view = queue.removeFirst();
                    Drawable background = view.getBackground();
                    if (background instanceof ColorDrawable && LoginDesignPolicy.canvas(((ColorDrawable) background).getColor()))
                        view.setBackgroundColor(colors.canvas);
                    if (view instanceof TextView) {
                        TextView text = (TextView) view;
                        boolean bold = text.getTypeface() != null && text.getTypeface().isBold();
                        android.graphics.Typeface font = DesignSystem.font(host, bold);
                        if (!font.equals(text.getTypeface())) text.setTypeface(font);
                        if (view instanceof EditText) {
                            // Input type, transformation, autofill, errors and listeners stay native.
                            if (text.getCurrentTextColor() != colors.text) text.setTextColor(colors.text);
                            text.setHintTextColor(colors.secondary);
                            Integer old = backgrounds.get(view);
                            if (old == null || old != colors.line) {
                                GradientDrawable outline = DesignSystem.surface(host, colors.canvas, 12);
                                outline.setStroke(DesignSystem.dp(host, 1), colors.line);
                                text.setBackground(outline); backgrounds.put(view, colors.line);
                            }
                        } else if (text.isClickable()) {
                            if (LoginDesignPolicy.blue(text.getCurrentTextColor())) text.setTextColor(colors.accent);
                            int fill = color(background);
                            if (LoginDesignPolicy.blue(fill)) {
                                GradientDrawable shape = DesignSystem.surface(host, colors.text, 12);
                                text.setBackground(new RippleDrawable(ColorStateList.valueOf(colors.line), shape, null));
                                text.setTextColor(new ColorStateList(new int[][]{new int[]{-android.R.attr.state_enabled},new int[]{}},
                                    new int[]{colors.secondary, colors.canvas}));
                            }
                        }
                    } else if (view.isClickable() && LoginDesignPolicy.blue(color(background))) {
                        // Native container buttons keep their white child labels readable.
                        view.setBackgroundTintList(ColorStateList.valueOf(0xff3e4045));
                    }
                    if (view instanceof ViewGroup) {
                        ViewGroup group = (ViewGroup) view;
                        for (int i = 0; i < group.getChildCount() && queue.size() < 256; i++) queue.addLast(group.getChildAt(i));
                    }
                }
            } catch (RuntimeException ignored) { /* Native authentication remains usable on unknown variants. */ }
        }
        @SuppressWarnings("deprecation")
        int color(Drawable drawable) {
            if (drawable instanceof ColorDrawable) return ((ColorDrawable) drawable).getColor();
            if (drawable instanceof GradientDrawable && android.os.Build.VERSION.SDK_INT >= 24) {
                ColorStateList value = ((GradientDrawable) drawable).getColor();
                return value == null ? 0 : value.getDefaultColor();
            }
            if (drawable instanceof InsetDrawable) return color(((InsetDrawable) drawable).getDrawable());
            if (drawable instanceof LayerDrawable) {
                LayerDrawable layers = (LayerDrawable) drawable;
                for (int i = 0; i < layers.getNumberOfLayers(); i++) { int value = color(layers.getDrawable(i)); if (LoginDesignPolicy.blue(value)) return value; }
            }
            return 0;
        }
    }
}
