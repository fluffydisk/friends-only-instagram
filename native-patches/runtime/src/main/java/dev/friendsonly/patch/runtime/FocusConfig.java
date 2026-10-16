package dev.friendsonly.patch.runtime;

import android.content.Context;
import android.content.SharedPreferences;

/** Stores three local UI choices only; never stores Instagram data. */
final class FocusConfig implements SharedPreferences.OnSharedPreferenceChangeListener {
    static final String REELS = "hide_reels";
    static final String EXPLORE = "hide_explore";
    static final String EXTRAS = "hide_extra_shortcuts";

    private final SharedPreferences preferences;
    private volatile boolean hideReels;
    private volatile boolean hideExplore;
    private volatile boolean hideExtras;

    FocusConfig(Context applicationContext) {
        preferences = applicationContext.getSharedPreferences("friends_only_focus", Context.MODE_PRIVATE);
        reload();
        preferences.registerOnSharedPreferenceChangeListener(this);
    }

    boolean hideReels() { return hideReels; }
    boolean hideExplore() { return hideExplore; }
    boolean hideExtras() { return hideExtras; }

    void set(String key, boolean enabled) {
        if (REELS.equals(key) || EXPLORE.equals(key) || EXTRAS.equals(key)) {
            preferences.edit().putBoolean(key, enabled).apply();
        }
    }

    void restoreAll() {
        preferences.edit().putBoolean(REELS, false).putBoolean(EXPLORE, false)
                .putBoolean(EXTRAS, false).apply();
    }

    @Override public void onSharedPreferenceChanged(SharedPreferences preferences, String key) {
        reload();
        FocusHooks.refreshAll();
    }

    private void reload() {
        hideReels = read(REELS, FocusPolicy.DEFAULT_HIDE_REELS);
        hideExplore = read(EXPLORE, FocusPolicy.DEFAULT_HIDE_EXPLORE);
        hideExtras = read(EXTRAS, FocusPolicy.DEFAULT_HIDE_EXTRAS);
    }

    private boolean read(String key, boolean fallback) {
        try {
            return preferences.getBoolean(key, fallback);
        } catch (ClassCastException ignored) {
            return fallback;
        }
    }
}
