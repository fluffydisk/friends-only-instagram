package dev.friendsonly.android.filter

import android.content.Context
import org.json.JSONObject

class FilterSettings(context: Context) {
    private val preferences = context.getSharedPreferences("filters", Context.MODE_PRIVATE)
    var hideReels: Boolean
        get() = preferences.getBoolean("reels", true)
        set(value) { preferences.edit().putBoolean("reels", value).apply() }
    var hideExplore: Boolean
        get() = preferences.getBoolean("explore", true)
        set(value) { preferences.edit().putBoolean("explore", value).apply() }
    var hideSuggestions: Boolean
        get() = preferences.getBoolean("suggestions", false)
        set(value) { preferences.edit().putBoolean("suggestions", value).apply() }

    fun asJson(): String = JSONObject()
        .put("hideReels", hideReels)
        .put("hideExplore", hideExplore)
        .put("hideSuggestions", hideSuggestions)
        .toString()
}
