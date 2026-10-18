package dev.friendsonly.patch.runtime;

import android.content.Context;
import android.content.SharedPreferences;

/** Stores local UI choices only; never stores Instagram data. */
final class FocusConfig implements SharedPreferences.OnSharedPreferenceChangeListener {
    static final String FOLLOWING_ONLY = "following_only";

    private final SharedPreferences preferences;
    private volatile boolean followingOnly;

    FocusConfig(Context applicationContext) {
        preferences = applicationContext.getSharedPreferences("friends_only_focus", Context.MODE_PRIVATE);
        reload();
        preferences.registerOnSharedPreferenceChangeListener(this);
    }

    // Discovery removal and our appearance are permanent, independent of legacy preferences.
    boolean followingOnly() { return followingOnly; }
    boolean quietUi() { return true; }

    void set(String key, boolean enabled) {
        if (FOLLOWING_ONLY.equals(key)) {
            preferences.edit().putBoolean(key, enabled).apply();
        }
    }

    @Override public void onSharedPreferenceChanged(SharedPreferences preferences, String key) {
        reload();
        FocusHooks.refreshAll();
    }

    private void reload() {
        followingOnly = read(FOLLOWING_ONLY, FeedPolicy.DEFAULT_FOLLOWING_ONLY);
        FeedHooks.setFollowingOnly(followingOnly);
    }

    private boolean read(String key, boolean fallback) {
        try {
            return preferences.getBoolean(key, fallback);
        } catch (ClassCastException ignored) {
            return fallback;
        }
    }
}
