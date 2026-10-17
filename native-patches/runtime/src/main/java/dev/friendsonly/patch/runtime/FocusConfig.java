package dev.friendsonly.patch.runtime;

import android.content.Context;
import android.content.SharedPreferences;

/** Stores local UI choices only; never stores Instagram data. */
final class FocusConfig implements SharedPreferences.OnSharedPreferenceChangeListener {
    static final String REELS = "hide_reels";
    static final String EXPLORE = "hide_explore";
    static final String EXTRAS = "hide_extra_shortcuts";
    static final String BLOCK_FEED_REELS = "block_feed_reels_open";

    private final SharedPreferences preferences;
    private volatile boolean hideReels;
    private volatile boolean hideExplore;
    private volatile boolean hideExtras;
    private volatile boolean blockFeedReelsOpen;

    FocusConfig(Context applicationContext) {
        preferences = applicationContext.getSharedPreferences("friends_only_focus", Context.MODE_PRIVATE);
        reload();
        preferences.registerOnSharedPreferenceChangeListener(this);
    }

    boolean hideReels() { return hideReels; }
    boolean hideExplore() { return hideExplore; }
    boolean hideExtras() { return hideExtras; }
    boolean blockFeedReelsOpen() { return blockFeedReelsOpen; }

    void set(String key, boolean enabled) {
        if (REELS.equals(key) || EXPLORE.equals(key) || EXTRAS.equals(key)
                || BLOCK_FEED_REELS.equals(key)) {
            preferences.edit().putBoolean(key, enabled).apply();
        }
    }

    void restoreAll() {
        preferences.edit().putBoolean(REELS, false).putBoolean(EXPLORE, false)
                .putBoolean(EXTRAS, false).putBoolean(BLOCK_FEED_REELS, false).apply();
    }

    @Override public void onSharedPreferenceChanged(SharedPreferences preferences, String key) {
        reload();
        FocusHooks.refreshAll();
    }

    private void reload() {
        hideReels = read(REELS, FocusPolicy.DEFAULT_HIDE_REELS);
        hideExplore = read(EXPLORE, FocusPolicy.DEFAULT_HIDE_EXPLORE);
        hideExtras = read(EXTRAS, FocusPolicy.DEFAULT_HIDE_EXTRAS);
        blockFeedReelsOpen = read(BLOCK_FEED_REELS, FocusPolicy.DEFAULT_BLOCK_FEED_REELS_OPEN);
    }

    private boolean read(String key, boolean fallback) {
        try {
            return preferences.getBoolean(key, fallback);
        } catch (ClassCastException ignored) {
            return fallback;
        }
    }
}
