package dev.friendsonly.patch.runtime;

import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import java.lang.ref.WeakReference;
import java.util.ArrayDeque;
import java.util.WeakHashMap;

/** Scoped native row decoration. No text is read/exported, and no native click/gesture is replaced. */
public final class ScreenDesign {
    private static final WeakHashMap<View, WeakReference<Binding>> BOUND = new WeakHashMap<>();
    private static final int MAX_NODES = 96;
    private static final int MAX_DEPTH = 8;
    private ScreenDesign() {}

    /** Native UFI-holder hook covers rows built without the resource inflater. */
    public static void feedActions(View root) { inflated(root, 0x7f0e1498, false); }

    /** Injected at the normal return of the two pinned resource-ID inflaters only. */
    public static void inflated(View root, int layout, boolean attachedToParent) {
        int surface = ScreenDesignPolicy.surface(layout);
        // With attachToRoot=true, Android returns the parent, not the inflated child. Never style it.
        if (root == null || surface == ScreenDesignPolicy.NONE || attachedToParent) return;
        if (Looper.myLooper() != Looper.getMainLooper()) {
            final WeakReference<View> weak = new WeakReference<>(root);
            root.post(() -> inflated(weak.get(), layout, false));
            return;
        }
        try {
            WeakReference<Binding> existing = BOUND.get(root);
            if (existing != null && existing.get() != null) return;
            Binding binding = new Binding(root, surface);
            BOUND.put(root, new WeakReference<>(binding));
            root.addOnAttachStateChangeListener(binding);
            root.addOnLayoutChangeListener(binding);
            if (root.isAttachedToWindow()) binding.queue();
        } catch (RuntimeException ignored) {
            // Optional styling must never prevent a genuine native screen from opening.
        }
    }

    private static final class Binding implements View.OnAttachStateChangeListener,
            View.OnLayoutChangeListener, Runnable {
        private final WeakReference<View> root;
        private final int surface;
        private boolean pending;
        private long lastUpdate;
        Binding(View view, int surface) { root = new WeakReference<>(view); this.surface = surface; }
        void queue() {
            View view = root.get();
            if (!pending && view != null && view.isAttachedToWindow()) {
                pending = true;
                long delay = Math.max(0, 250 - (android.os.SystemClock.uptimeMillis() - lastUpdate));
                view.postDelayed(this, delay); // Complete native binding first; at most four updates per second.
            }
        }
        @Override public void onViewAttachedToWindow(View view) { queue(); }
        @Override public void onViewDetachedFromWindow(View view) { view.removeCallbacks(this); pending = false; }
        @Override public void onLayoutChange(View v, int l, int t, int r, int b, int ol, int ot, int or, int ob) { queue(); }
        @Override public void run() {
            pending = false;
            View view = root.get();
            if (view == null || !view.isAttachedToWindow()) return;
            lastUpdate = android.os.SystemClock.uptimeMillis();
            try { decorate(view, surface); }
            catch (RuntimeException ignored) { /* Unsupported local layout retains its native content. */ }
        }
    }

    private static void decorate(View root, int surface) {
        DesignSystem.Colors colors = new DesignSystem.Colors(QuietChrome.isDarkTheme(root));
        if (surface == ScreenDesignPolicy.FEED_HEADER) {
            padding(root, 20, 12, 20, 12);
            minimum(root, 68);
        } else if (surface == ScreenDesignPolicy.INBOX) {
            padding(root, 20, 12, 20, 12);
            minimum(root, 84);
        } else if (surface == ScreenDesignPolicy.PROFILE) {
            padding(root, 20, 16, 20, 16);
        }
        ArrayDeque<Node> nodes = new ArrayDeque<>();
        nodes.add(new Node(root, 0));
        int visited = 0;
        while (!nodes.isEmpty() && visited++ < MAX_NODES) {
            Node node = nodes.removeFirst();
            View view = node.view;
            String name = name(view);
            if (ScreenDesignPolicy.hide(surface, name)) {
                if (view.getVisibility() != View.GONE) view.setVisibility(View.GONE);
                continue;
            }
            if (surface == ScreenDesignPolicy.FEED_BODY) FeedActionLabel.apply(view, name, colors);
            if (view instanceof TextView) {
                TextView text = (TextView) view;
                boolean title = ScreenDesignPolicy.title(name);
                android.graphics.Typeface desired = DesignSystem.font(view.getContext(), title);
                if (!desired.equals(text.getTypeface())) text.setTypeface(desired);
                if (title) text.setTextSize(surface == ScreenDesignPolicy.PROFILE ? 22 : 16);
                else if (surface == ScreenDesignPolicy.MESSAGE || view instanceof EditText) text.setTextSize(16);
                // Preserve native span/link/error colors and themed incoming/outgoing bubble colors.
                // The independent neutral semantic theme supplies the canvas; never recolor media.
            }
            if (node.depth >= MAX_DEPTH || !(view instanceof ViewGroup) || isList(view)) continue;
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount() && visited + nodes.size() < MAX_NODES; i++) {
                nodes.addLast(new Node(group.getChildAt(i), node.depth + 1));
            }
        }
    }

    private static void minimum(View view, int height) {
        int pixels = DesignSystem.dp(view.getContext(), height);
        if (view.getMinimumHeight() != pixels) view.setMinimumHeight(pixels);
        ViewGroup.LayoutParams params = view.getLayoutParams();
        if (params != null && params.height > 0 && params.height < pixels) {
            params.height = pixels;
            view.setLayoutParams(params);
        }
    }

    private static void padding(View view, int start, int top, int end, int bottom) {
        int s = DesignSystem.dp(view.getContext(), start), t = DesignSystem.dp(view.getContext(), top);
        int e = DesignSystem.dp(view.getContext(), end), b = DesignSystem.dp(view.getContext(), bottom);
        if (view.getPaddingStart() != s || view.getPaddingTop() != t || view.getPaddingEnd() != e || view.getPaddingBottom() != b) {
            view.setPaddingRelative(s, t, e, b);
        }
    }

    private static boolean isList(View view) {
        if (view instanceof android.widget.AbsListView || view instanceof android.widget.ScrollView
                || view instanceof android.widget.HorizontalScrollView) return true;
        Class<?> type = view.getClass();
        for (int i = 0; type != null && i < 8; i++, type = type.getSuperclass()) {
            if ("androidx.recyclerview.widget.RecyclerView".equals(type.getName())) return true;
        }
        return false;
    }

    private static String name(View view) {
        if (view.getId() == View.NO_ID) return "";
        try { return view.getResources().getResourceEntryName(view.getId()); }
        catch (RuntimeException ignored) { return ""; }
    }

    private static final class Node {
        final View view; final int depth;
        Node(View view, int depth) { this.view = view; this.depth = depth; }
    }
}
