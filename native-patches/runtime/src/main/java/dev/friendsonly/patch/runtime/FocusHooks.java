package dev.friendsonly.patch.runtime;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Application;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.net.URI;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;

/** Native, scoped hooks injected into Instagram. No web scraping or account-data access. */
public final class FocusHooks {
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    // Values must not retain their weak keys (or an Activity context).
    private static final WeakHashMap<ViewGroup, TabController> ROOTS = new WeakHashMap<>();
    private static final WeakHashMap<Activity, WeakReference<AlertDialog>> DIALOGS = new WeakHashMap<>();
    private static volatile FocusConfig config;
    private static boolean lifecycleRegistered;

    private FocusHooks() {}

    /** Called immediately after the supported Instagram tab-bar binder obtains its ViewGroup. */
    public static void install(final ViewGroup tabBar) {
        if (tabBar == null) return;
        if (Looper.myLooper() != Looper.getMainLooper()) {
            final WeakReference<ViewGroup> weakRoot = new WeakReference<>(tabBar);
            MAIN.post(new Runnable() {
                @Override public void run() { install(weakRoot.get()); }
            });
            return;
        }
        try {
            if (config == null) {
                Context application = tabBar.getContext().getApplicationContext();
                if (!(application instanceof Application)) return;
                registerLifecycle((Application) application);
                config = new FocusConfig(application);
            }
            TabController existing = ROOTS.get(tabBar);
            if (existing != null) {
                existing.schedule();
                return;
            }
            TabController controller = new TabController(tabBar);
            ROOTS.put(tabBar, controller);
            tabBar.addOnAttachStateChangeListener(controller);
            if (tabBar.isAttachedToWindow()) controller.attach();
        } catch (RuntimeException ignored) {
            // A changed Instagram layout must keep working without these optional controls.
        }
    }

    /**
     * Optional network hook: fail open until the native settings context exists. IOException uses
     * Instagram's existing request error handling; no request or response body is read or altered.
     */
    public static void checkRequest(URI request) throws IOException {
        FocusConfig current = config;
        if (current != null && FocusPolicy.shouldBlock(request, current.hideReels(), current.hideExplore())) {
            throw new IOException("Friends Only focus filter");
        }
    }

    /** Called at the supported Reels-viewer entry; no inline playback or click listener changes. */
    public static boolean shouldBlockFeedReelsOpen(Enum<?> source) {
        FocusConfig current = config;
        return current != null && source != null
                && FocusPolicy.shouldBlockFeedReelsOpen(source.name(), current.blockFeedReelsOpen());
    }

    static void refreshAll() {
        MAIN.post(new Runnable() {
            @Override public void run() {
                for (TabController controller : new ArrayList<>(ROOTS.values())) controller.schedule();
            }
        });
    }

    private static void registerLifecycle(Application application) {
        if (lifecycleRegistered) return;
        application.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            @Override public void onActivityDestroyed(Activity activity) { dismissDialog(activity); }
            @Override public void onActivityCreated(Activity activity, Bundle savedInstanceState) {}
            @Override public void onActivityStarted(Activity activity) {}
            @Override public void onActivityResumed(Activity activity) {}
            @Override public void onActivityPaused(Activity activity) {}
            @Override public void onActivityStopped(Activity activity) {}
            @Override public void onActivitySaveInstanceState(Activity activity, Bundle state) {}
        });
        lifecycleRegistered = true;
    }

    private static void dismissDialog(Activity activity) {
        WeakReference<AlertDialog> reference = DIALOGS.remove(activity);
        AlertDialog dialog = reference == null ? null : reference.get();
        dismissSafely(dialog);
    }

    private static void dismissSafely(AlertDialog dialog) {
        if (dialog == null) return;
        try {
            dialog.dismiss();
        } catch (RuntimeException ignored) {
            // A window removed by Instagram or the framework must not break Activity teardown.
        }
    }

    private static Activity activityFor(View view) {
        Context context = view.getContext();
        for (int i = 0; i < 12; i++) {
            if (context instanceof Activity) return (Activity) context;
            if (!(context instanceof ContextWrapper)) return null;
            Context next = ((ContextWrapper) context).getBaseContext();
            if (next == context) return null;
            context = next;
        }
        return null;
    }

    private static void showSettings(TabController controller) {
        ViewGroup root = controller.root.get();
        final FocusConfig current = config;
        if (root == null || current == null) return;
        final Activity activity = activityFor(root);
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;
        WeakReference<AlertDialog> previous = DIALOGS.get(activity);
        if (previous != null && previous.get() != null && previous.get().isShowing()) return;
        final boolean tr = "tr".equals(Locale.getDefault().getLanguage());
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        int padding = Math.round(20 * activity.getResources().getDisplayMetrics().density);
        content.setPadding(padding, padding / 2, padding, 0);
        TextView intro = new TextView(activity);
        intro.setText(tr
                ? "Native yama prototipi · Ana Sayfa'ya uzun basarak açılır. Hikâyeler ve sohbetler korunur."
                : "Native patch prototype · Long-press Home to open. Stories and conversations are preserved.");
        content.addView(intro);
        addSwitch(content, current, FocusConfig.REELS, tr ? "Reels sekmesini gizle" : "Hide Reels tab", current.hideReels());
        addSwitch(content, current, FocusConfig.EXPLORE, tr ? "Keşfet / Arama sekmesini gizle" : "Hide Explore / Search tab", current.hideExplore());
        addSwitch(content, current, FocusConfig.EXTRAS, tr ? "Oluştur kısayolunu gizle" : "Hide Create shortcut", current.hideExtras());
        addSwitch(content, current, FocusConfig.BLOCK_FEED_REELS,
                tr ? "Akış ve profilden Reels açılmasını engelle" : "Block Reels from feed and profile posts",
                current.blockFeedReelsOpen());
        TextView note = new TextView(activity);
        note.setText(tr
                ? "Videolar akışın içinde oynatılmaya devam eder. Bu ayar açıkken profildeki videolara dokunmak da Reels ekranını açmaz. Keşfet filtresi aynı sekmedeki aramayı da gizler. Bu filtre akışın yalnızca takip ettiklerini içerdiğini garanti etmez. Değişiklikler için ekranı yeniden açman gerekebilir."
                : "Videos keep playing within the feed. With this setting on, tapping profile videos also leaves the Reels viewer closed. Explore filtering also hides search in that tab. These filters do not guarantee a following-only feed. You may need to reopen a screen after changing a filter.");
        note.setPadding(0, padding / 2, 0, padding / 2);
        content.addView(note);
        final AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("Friends Only")
                .setView(content)
                .setPositiveButton(tr ? "Tamam" : "Done", null)
                .setNeutralButton(tr ? "Tümünü geri göster" : "Show all", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface ignored, int which) { current.restoreAll(); }
                }).create();
        final WeakReference<Activity> owner = new WeakReference<>(activity);
        dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override public void onDismiss(DialogInterface ignored) {
                Activity activity = owner.get();
                WeakReference<AlertDialog> registered = activity == null ? null : DIALOGS.get(activity);
                if (registered != null && registered.get() == dialog) DIALOGS.remove(activity);
            }
        });
        try {
            dialog.show();
            DIALOGS.put(activity, new WeakReference<>(dialog));
            controller.dialog = new WeakReference<>(dialog);
        } catch (RuntimeException ignored) {
            // The Activity can finish between validation and show().
        }
    }

    private static void addSwitch(LinearLayout parent, final FocusConfig current, final String key,
            String label, boolean checked) {
        Switch toggle = new Switch(parent.getContext());
        toggle.setText(label);
        toggle.setChecked(checked);
        toggle.setPadding(0, 16, 0, 16);
        toggle.setOnCheckedChangeListener((button, enabled) -> current.set(key, enabled));
        parent.addView(toggle, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private static final class TabController implements View.OnAttachStateChangeListener,
            ViewTreeObserver.OnGlobalLayoutListener, Runnable {
        private static final int MAX_VIEWS = 96;
        private static final int MAX_DEPTH = 5;
        private static final long MIN_INTERVAL_MS = 250;
        private final WeakReference<ViewGroup> root;
        private final WeakHashMap<View, Integer> hidden = new WeakHashMap<>();
        private final WeakHashMap<View, Boolean> settingsTargets = new WeakHashMap<>();
        private WeakReference<ViewTreeObserver> observer = new WeakReference<>(null);
        private WeakReference<AlertDialog> dialog = new WeakReference<>(null);
        private long lastRun;
        private boolean attached;
        private boolean queued;

        TabController(ViewGroup root) { this.root = new WeakReference<>(root); }

        void attach() {
            if (attached) return;
            ViewGroup view = root.get();
            if (view == null) return;
            attached = true;
            ViewTreeObserver tree = view.getViewTreeObserver();
            if (tree.isAlive()) {
                tree.addOnGlobalLayoutListener(this);
                observer = new WeakReference<>(tree);
            }
            schedule();
        }

        @Override public void onViewAttachedToWindow(View view) { attach(); }

        @Override public void onViewDetachedFromWindow(View view) {
            attached = false;
            MAIN.removeCallbacks(this);
            queued = false;
            ViewTreeObserver tree = observer.get();
            if (tree != null && tree.isAlive()) tree.removeOnGlobalLayoutListener(this);
            observer.clear();
            for (Map.Entry<View, Integer> entry : hidden.entrySet()) restore(entry.getKey(), entry.getValue());
            hidden.clear();
            AlertDialog current = dialog.get();
            dismissSafely(current);
            dialog.clear();
        }

        @Override public void onGlobalLayout() { schedule(); }

        void schedule() {
            if (!attached || queued) return;
            queued = true;
            long delay = Math.max(0, MIN_INTERVAL_MS - (SystemClock.uptimeMillis() - lastRun));
            MAIN.postDelayed(this, delay);
        }

        @Override public void run() {
            queued = false;
            if (!attached) return;
            lastRun = SystemClock.uptimeMillis();
            ViewGroup view = root.get();
            FocusConfig current = config;
            if (view == null || current == null) return;
            try {
                apply(view, current);
            } catch (RuntimeException ignored) {
                // Never crash Instagram because a resource/view implementation changed.
            }
        }

        private void apply(ViewGroup tabBar, FocusConfig current) {
            Iterator<Map.Entry<View, Integer>> previous = hidden.entrySet().iterator();
            while (previous.hasNext()) {
                Map.Entry<View, Integer> entry = previous.next();
                View view = entry.getKey();
                if (!FocusPolicy.shouldHideTab(nameOf(view), current.hideReels(),
                        current.hideExplore(), current.hideExtras()) || !within(view, tabBar)) {
                    restore(view, entry.getValue());
                    previous.remove();
                }
            }
            ArrayDeque<Node> pending = new ArrayDeque<>();
            pending.add(new Node(tabBar, 0));
            int visited = 0;
            while (!pending.isEmpty() && visited++ < MAX_VIEWS) {
                Node node = pending.removeFirst();
                View view = node.view;
                String name = nameOf(view);
                if (FocusPolicy.shouldHideTab(name, current.hideReels(), current.hideExplore(), current.hideExtras())) {
                    if (!hidden.containsKey(view) && view.getVisibility() != View.GONE) {
                        hidden.put(view, view.getVisibility());
                    }
                    if (hidden.containsKey(view) && view.getVisibility() != View.GONE) view.setVisibility(View.GONE);
                } else if ("feed_tab".equals(name)) {
                    installSettingsGesture(view);
                }
                if (node.depth < MAX_DEPTH && view instanceof ViewGroup) {
                    ViewGroup group = (ViewGroup) view;
                    for (int i = 0; i < group.getChildCount() && pending.size() + visited < MAX_VIEWS; i++) {
                        pending.addLast(new Node(group.getChildAt(i), node.depth + 1));
                    }
                }
            }
        }

        private void installSettingsGesture(View home) {
            if (settingsTargets.containsKey(home)) return;
            // The supported patch owns Home's long-press gesture so focus settings always remain
            // reachable. Its ordinary click listener is untouched. Any original long-press action
            // is replaced until the user returns to an unpatched app; there is no public API to
            // retrieve and restore an existing long-click listener, and we do not use reflection.
            final WeakReference<TabController> controller = new WeakReference<>(this);
            home.setOnLongClickListener(new View.OnLongClickListener() {
                @Override public boolean onLongClick(View view) {
                    TabController target = controller.get();
                    if (target == null || !target.attached) return false;
                    try {
                        showSettings(target);
                        return true;
                    } catch (RuntimeException ignored) {
                        return false;
                    }
                }
            });
            settingsTargets.put(home, Boolean.TRUE);
        }

        private static String nameOf(View view) {
            if (view == null || view.getId() == View.NO_ID) return "";
            try {
                return view.getResources().getResourceEntryName(view.getId());
            } catch (RuntimeException ignored) {
                return "";
            }
        }

        private static boolean within(View view, ViewGroup root) {
            View current = view;
            for (int i = 0; i <= MAX_DEPTH; i++) {
                if (current == root) return true;
                if (!(current.getParent() instanceof View)) return false;
                current = (View) current.getParent();
            }
            return false;
        }

        private static void restore(View view, int visibility) {
            if (view != null && view.getVisibility() == View.GONE) view.setVisibility(visibility);
        }
    }

    private static final class Node {
        final View view;
        final int depth;
        Node(View view, int depth) { this.view = view; this.depth = depth; }
    }
}
