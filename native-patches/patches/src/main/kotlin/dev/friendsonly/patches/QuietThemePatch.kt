/* SPDX-License-Identifier: GPL-3.0-only */
package dev.friendsonly.patches

import app.morphe.patcher.patch.resourcePatch
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
/** Independent values; semantic attributes must point at color resources, never inline literals.
 * Native LX/00RQ.A0b resolves attributes via TypedValue.resourceId; literal colors resolve to 0.
 */
internal val QUIET_PALETTE = linkedMapOf(
    "light_canvas" to "#FFF8F7F4", "light_surface" to "#FFEDEEE9", "light_elevated" to "#FFFFFFFF",
    "light_text" to "#FF28292C", "light_secondary_text" to "#FF68696D",
    "light_link" to "#FF424449", "light_button" to "#FF424449",
    "dark_canvas" to "#FF0D0E10", "dark_surface" to "#FF151618", "dark_elevated" to "#FF1D1E21",
    "dark_text" to "#FFEDEDEF", "dark_secondary_text" to "#FFA5A6AA",
    "dark_link" to "#FFD4D5D8", "dark_button" to "#FF3E4045",
)

/** Append to the tracked source values file so Morphe allocates real, nonzero color IDs. */
internal fun appendQuietPaletteColors(document: Document): Int {
    val resources = document.documentElement
    check(resources.tagName == "resources") { "Unsupported quiet palette values root." }
    val existingNames = resources.quietChildren().map { it.getAttribute("name") }.toSet()
    QUIET_PALETTE.forEach { (suffix, value) ->
        val name = "friends_only_$suffix"
        check(name !in existingNames) { "Quiet palette color already exists: $name." }
        resources.appendChild(document.createElement("color").apply {
            setAttribute("name", name)
            textContent = value
        })
    }
    return QUIET_PALETTE.size
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
            // Existing native consumers call getColor(resolveAttribute(...).resourceId).
            // A literal #ARGB would leave resourceId=0 and crash otherwise valid screens.
            check(item.textContent.startsWith("@color/")) {
                "Native semantic color must remain a resource reference: $name/$attribute."
            }
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
    val canvas = QUIET_PALETTE.getValue("${if (dark) "dark" else "light"}_canvas")
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
        <item><color android:color="${QUIET_PALETTE.getValue("${if (dark) "dark" else "light"}_canvas")}" /></item>
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
        // A separate newly created values file was not reliably registered by this encoder.
        // Appending through document() tracks the existing file and lets the encoder allocate IDs.
        document("res/values/colors.xml").use {
            check(appendQuietPaletteColors(it) == 14) { "Unsupported quiet palette size." }
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
