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
        get() = preferences.getBoolean("suggestions", true)
        set(value) { preferences.edit().putBoolean("suggestions", value).apply() }
    var hideDistractions: Boolean
        get() = preferences.getBoolean("distractions", true)
        set(value) { preferences.edit().putBoolean("distractions", value).apply() }
    var minimalAppearance: Boolean
        get() = preferences.getBoolean("appearance", true)
        set(value) { preferences.edit().putBoolean("appearance", value).apply() }

    fun asJson(darkMode: Boolean, styleSheet: String): String = JSONObject()
        .put("hideReels", hideReels)
        .put("hideExplore", hideExplore)
        .put("hideSuggestions", hideSuggestions)
        .put("hideDistractions", hideDistractions)
        .put("minimalAppearance", minimalAppearance)
        .put("darkMode", darkMode)
        .put("styleSheet", styleSheet)
        .toString()
}
