package dev.friendsonly.patch.runtime;

import android.content.Context;

/** Private local, opt-in-copy diagnosis; the original uncaught handler still handles every crash. */
final class CrashSafe {
    private static final String PREFS = "friends_only_diagnostics";
    static void install(Context context) {
        try {
            Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
            if (previous instanceof Handler) return;
            Context application = context.getApplicationContext();
            if (application != null) Thread.setDefaultUncaughtExceptionHandler(new Handler(application, previous));
        } catch (RuntimeException ignored) {}
    }
    static String saved(Context context) {
        String value = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("last_crash", "");
        return value != null && value.length() <= 7000 ? value : "";
    }
    static void clear(Context context) { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove("last_crash").apply(); }
    private static final class Handler implements Thread.UncaughtExceptionHandler {
        final Context context;
        final Thread.UncaughtExceptionHandler previous;
        Handler(Context context, Thread.UncaughtExceptionHandler previous) { this.context = context; this.previous = previous; }
        public void uncaughtException(Thread thread, Throwable failure) {
            try {
                String report = "Friends Only runtime diagnostic v1\nVersion: " + DesignSystem.VERSION
                    + "\nAndroid SDK: " + android.os.Build.VERSION.SDK_INT + "\n" + CrashReportPolicy.format(failure);
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("last_crash", report).commit();
            } catch (Throwable ignored) { /* Report failure must never replace the original crash handler. */ }
            if (previous != null) previous.uncaughtException(thread, failure);
            else { android.os.Process.killProcess(android.os.Process.myPid()); System.exit(10); }
        }
    }
}
