/* SPDX-License-Identifier: GPL-3.0-only */
package dev.friendsonly.patches

import app.morphe.patcher.patch.resourcePatch
import app.morphe.patcher.resource.PublicXmlManager
import java.io.File
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node

/** These semantic families and IDs were checked in the pinned 451 resource table. */
internal val QUIET_STYLE_IDS = linkedMapOf(
    "BDSSemanticColorsDark" to "0x7f140054",
    "BDSSemanticColorsLight" to "0x7f140055",
    "IgdsPrismGrayOverridesDark" to "0x7f14029b",
    "IgdsPrismSemanticColorsExperimentDark" to "0x7f1402a7",
    "IgdsPrismSemanticColorsExperimentLight" to "0x7f1402a8",
    "IgdsSemanticColorsDark" to "0x7f1402aa",
    "IgdsSemanticColorsLight" to "0x7f1402ab",
    "GradientPatternStyle" to "0x7f140236",
    "CloseFriendsGradientPatternStyle" to "0x7f140142",
)

internal val QUIET_TAB_IDS = linkedMapOf(
    "tab_home_drawable" to "0x7f083063",
    "tab_prism_home_drawable" to "0x7f083065",
    "tab_direct_drawable" to "0x7f08305c",
    "tab_profile_drawable" to "0x7f083067",
)
internal const val QUIET_LAUNCH_STYLE_ID = "0x7f1400a1"
internal val QUIET_PALETTE_NAMES = listOf("light", "dark").flatMap { mode ->
    listOf("canvas", "surface", "elevated", "text", "secondary_text", "link", "button").map {
        "friends_only_${mode}_$it"
    }
}

/**
 * The pinned encoder does not allocate values IDs for every newly created XML file. Register
 * palette entries explicitly through its own allocator before encoding, without guessing IDs or
 * reusing source entries. File-based drawables are separately discovered by the encoder.
 */
internal fun registerQuietPaletteResources(publicXml: File): Map<String, Int> =
    PublicXmlManager(publicXml).use { manager ->
        val before = manager.getDefinedIds()
        val highest = manager.getHighestResourceIds()["color"] ?: error("Missing source color resource type.")
        check((highest and -0x10000) == 0x7f060000 && (highest and 0xffff) <= 0xffff - QUIET_PALETTE_NAMES.size) {
            "Unsupported or exhausted source color resource namespace."
        }
        QUIET_PALETTE_NAMES.forEach { name ->
            check(!manager.idExists("color", name)) { "Quiet palette entry already exists in source: $name." }
            manager.createPublicId("color", name)
        }
        val after = manager.getDefinedIds()
        check(before.all { (key, value) -> after[key] == value }) { "Existing resource IDs must not change." }
        QUIET_PALETTE_NAMES.associateWith { name -> checkNotNull(after["color" to name]) }
    }

private fun Element.quietChildren(): List<Element> = buildList {
    for (index in 0 until childNodes.length) {
        val child = childNodes.item(index)
        if (child.nodeType == Node.ELEMENT_NODE) add(child as Element)
    }
}

internal fun verifyQuietThemeResourceIds(document: Document) {
    val resources = document.documentElement.quietChildren()
    listOf("style" to (QUIET_STYLE_IDS + mapOf("Base.Theme.Launcher" to QUIET_LAUNCH_STYLE_ID)),
        "drawable" to QUIET_TAB_IDS).forEach { (type, entries) ->
        entries.forEach { (name, id) ->
            val matches = resources.filter {
                it.tagName == "public" && it.getAttribute("type") == type && it.getAttribute("name") == name
            }
            check(matches.size == 1 && matches.single().getAttribute("id").lowercase() == id) {
                "Unsupported quiet-theme resource: $type/$name."
            }
        }
    }
}

/** Palette values are intentionally independent of Instagram's media and notification colors. */
internal fun quietThemeColorsXml(): String = """
    <resources>
        <color name="friends_only_light_canvas">#FFF8F7F4</color>
        <color name="friends_only_light_surface">#FFEDEEE9</color>
        <color name="friends_only_light_elevated">#FFFFFFFF</color>
        <color name="friends_only_light_text">#FF242B27</color>
        <color name="friends_only_light_secondary_text">#FF626B65</color>
        <color name="friends_only_light_link">#FF344D43</color>
        <color name="friends_only_light_button">#FF344D43</color>
        <color name="friends_only_dark_canvas">#FF151A17</color>
        <color name="friends_only_dark_surface">#FF202722</color>
        <color name="friends_only_dark_elevated">#FF29312B</color>
        <color name="friends_only_dark_text">#FFF2F3EE</color>
        <color name="friends_only_dark_secondary_text">#FFB3BDB5</color>
        <color name="friends_only_dark_link">#FFC0CFC4</color>
        <color name="friends_only_dark_button">#FF375A49</color>
    </resources>
""".trimIndent()

/**
 * Replace semantic UI surfaces, never image/video content, error states or legal/login wording.
 * Explicit light/dark families stay explicit so Instagram's in-app theme selection still works.
 */
internal fun applyQuietThemeStyles(document: Document): Int {
    val styles = document.documentElement.quietChildren().filter { it.tagName == "style" }
    val palette = linkedMapOf(
        "igds_color_primary_background" to "canvas",
        "igds_color_secondary_background" to "surface",
        "igds_color_elevated_background" to "elevated",
        "igds_color_primary_text" to "text",
        "igds_color_secondary_text" to "secondary_text",
    )
    var replacements = 0
    QUIET_STYLE_IDS.keys.filter { it.endsWith("Dark") || it.endsWith("Light") }.forEach { name ->
        val style = styles.filter { it.getAttribute("name") == name }.singleOrNull()
            ?: error("Missing or duplicate quiet-theme semantic family: $name.")
        val items = style.quietChildren().filter { it.tagName == "item" }
        val mode = if (name.endsWith("Dark")) "dark" else "light"
        val themed = if (name == "IgdsPrismGrayOverridesDark") palette else palette + mapOf(
            "igds_color_link" to "link", "igds_color_primary_button" to "button",
        )
        themed.forEach { (attribute, value) ->
            val item = items.filter { it.getAttribute("name") == attribute }.singleOrNull()
                ?: error("Unsupported quiet-theme attribute: $name/$attribute.")
            item.textContent = "@color/friends_only_${mode}_$value"
            replacements++
        }
    }
    // GradientSpinner uses GradientPatternStyle for the default unseen Story ring in this build.
    // Keep the seen/unseen distinction and close-friends distinction, but remove rainbow gradients.
    mapOf(
        "GradientPatternStyle" to "?attr/igds_color_secondary_text",
        "CloseFriendsGradientPatternStyle" to "?attr/igds_color_link",
    ).forEach { (name, color) ->
        val style = styles.filter { it.getAttribute("name") == name }.singleOrNull()
            ?: error("Unsupported quiet Story ring style: $name.")
        val items = style.quietChildren().filter { it.tagName == "item" }
        (1..5).forEach { stop ->
            val item = items.filter { it.getAttribute("name") == "gradientColor$stop" }.singleOrNull()
                ?: error("Unsupported quiet Story ring stop: $name/$stop.")
            item.textContent = color
            replacements++
        }
    }
    return replacements
}

/** Launch-only branding: system splash assets must not retain the source app's product mark. */
internal fun applyQuietLaunchTheme(document: Document, android12: Boolean, dark: Boolean = false): Int {
    val launcher = document.documentElement.quietChildren().singleOrNull {
        it.tagName == "style" && it.getAttribute("name") == "Base.Theme.Launcher"
    } ?: error("Missing or duplicate launch-only theme.")
    check(launcher.getAttribute("parent") == "@style/Theme.Instagram") { "Unexpected launch-theme parent." }
    val items = launcher.quietChildren().filter { it.tagName == "item" }
    val canvas = "@color/friends_only_${if (dark) "dark" else "light"}_canvas"
    val changes = if (android12) linkedMapOf(
        "android:windowSplashScreenBackground" to ("@color/ig_splash_screen_background" to canvas),
        "android:windowSplashScreenAnimatedIcon" to ("@drawable/instagram_padded" to "@drawable/friends_only_icon"),
        "android:windowSplashScreenBrandingImage" to ("@drawable/from_meta_gradient_padded" to "@null"),
        "android:windowSplashScreenIconBackgroundColor" to ("@color/ig_splash_screen_background" to canvas),
    ) else linkedMapOf(
        "android:windowBackground" to ("@drawable/instagram_splash_screen" to "@drawable/friends_only_launch_background"),
        "android:statusBarColor" to ("@color/ig_splash_screen_background" to canvas),
        "android:navigationBarColor" to ("@color/ig_splash_screen_background" to canvas),
    )
    changes.forEach { (attribute, values) ->
        val item = items.singleOrNull { it.getAttribute("name") == attribute }
            ?: error("Unsupported launch-only theme attribute: $attribute.")
        check(item.textContent == values.first) { "Unexpected source launch-only theme value: $attribute." }
        item.textContent = values.second
    }
    return changes.size
}

internal fun quietLaunchBackgroundXml(dark: Boolean = false): String = """
    <layer-list xmlns:android="http://schemas.android.com/apk/res/android" android:opacity="opaque">
        <item android:drawable="@color/friends_only_${if (dark) "dark" else "light"}_canvas" />
        <item android:drawable="@drawable/friends_only_icon" android:width="80dp" android:height="80dp" android:gravity="center" />
    </layer-list>
""".trimIndent()

/** Independent tab-only fallback artwork; all original navigation IDs and click targets survive. */
internal fun quietTabSelectorXml(name: String): String {
    check(name in QUIET_TAB_IDS) { "Unsupported quiet navigation drawable." }
    val path = when (name) {
        "tab_home_drawable", "tab_prism_home_drawable" ->
            "M8,5A3,3 0,1 0,8,11A3,3 0,1 0,8,5M16,5A3,3 0,1 0,16,11A3,3 0,1 0,16,5M2,20V18Q2,13 8,13Q11,13 12,15Q13,13 16,13Q22,13 22,18V20"
        "tab_direct_drawable" ->
            "M5,4H19Q22,4 22,7V15Q22,18 19,18H10L5,22V18Q2,18 2,15V7Q2,4 5,4ZM7,9H17M7,13H14"
        else -> "M12,3A4,4 0,1 0,12,11A4,4 0,1 0,12,3M4,21V19Q4,14 12,14Q20,14 20,19V21"
    }
    fun vector(selected: Boolean): String = """
        <vector android:width="24dp" android:height="24dp" android:viewportWidth="24" android:viewportHeight="24">
            <path android:fillColor="#00000000" android:strokeColor="?attr/igds_color_${if (selected) "primary" else "secondary"}_text"
                android:strokeWidth="${if (selected) "2.1" else "1.6"}" android:strokeLineCap="round" android:strokeLineJoin="round"
                android:pathData="$path" />
        </vector>
    """.trimIndent()
    return """
        <selector xmlns:android="http://schemas.android.com/apk/res/android">
            <item android:state_selected="true">${vector(true)}</item>
            <item>${vector(false)}</item>
        </selector>
    """.trimIndent()
}

@Suppress("unused")
val quietVisualTheme = resourcePatch(
    name = "Friends Only quiet visual theme",
    description = "Own warm-neutral light/dark surfaces, restrained Story rings and independent tab artwork; preserves content and authentication.",
    default = true,
) {
    compatibleWith(INSTAGRAM)
    dependsOn(appIdentity)
    execute {
        document("res/values/public.xml").use { verifyQuietThemeResourceIds(it) }
        registerQuietPaletteResources(get("res/values/public.xml"))
        get("res/values/friends_only_colors.xml").apply {
            parentFile.mkdirs()
            writeText(quietThemeColorsXml())
        }
        document("res/values/styles.xml").use {
            check(applyQuietThemeStyles(it) == 57) { "Unsupported quiet-theme surface count." }
            check(applyQuietLaunchTheme(it, false) == 3) { "Unsupported legacy launch-only theme." }
        }
        document("res/values-v31/styles.xml").use {
            check(applyQuietLaunchTheme(it, true) == 4) { "Unsupported Android 12 launch-only theme." }
        }
        document("res/values-night/styles.xml").use {
            check(applyQuietLaunchTheme(it, false, true) == 3) { "Unsupported legacy dark launch-only theme." }
        }
        document("res/values-night-v31/styles.xml").use {
            check(applyQuietLaunchTheme(it, true, true) == 4) { "Unsupported Android 12 dark launch-only theme." }
        }
        get("res/drawable/friends_only_launch_background.xml").apply {
            parentFile.mkdirs()
            writeText(quietLaunchBackgroundXml())
        }
        get("res/drawable-night/friends_only_launch_background.xml").apply {
            parentFile.mkdirs()
            writeText(quietLaunchBackgroundXml(true))
        }
        QUIET_TAB_IDS.keys.forEach { name ->
            get("res/drawable/$name.xml").apply {
                check(isFile) { "Missing quiet navigation selector: $name." }
                writeText(quietTabSelectorXml(name))
            }
        }
    }
}
