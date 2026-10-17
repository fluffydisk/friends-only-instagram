/* SPDX-License-Identifier: GPL-3.0-only */
package dev.friendsonly.patches

import java.io.ByteArrayInputStream
import java.nio.file.Path
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Document
import org.w3c.dom.Element
import kotlin.math.pow

/** Verify appearance changes preserve media/error semantics and retain readable light/dark UI. */
object QuietThemePolicyTest {
    @JvmStatic
    fun main(args: Array<String>) {
        var assertions = 0
        fun verify(value: Boolean, message: String) {
            check(value) { message }
            assertions++
        }
        fun parse(xml: String): Document {
            val factory = DocumentBuilderFactory.newInstance()
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            return factory.newDocumentBuilder().parse(ByteArrayInputStream(xml.toByteArray()))
        }
        fun Document.elements(tag: String): List<Element> {
            val nodes = getElementsByTagName(tag)
            return (0 until nodes.length).map { nodes.item(it) as Element }
        }
        fun publicFixture(): String = "<resources package=\"com.instagram.android\" id=\"0x7f\">" +
            QUIET_STYLE_IDS.map { (name, id) -> "<public type=\"style\" name=\"$name\" id=\"$id\" />" }.joinToString("") +
            "<public type=\"style\" name=\"Base.Theme.Launcher\" id=\"$QUIET_LAUNCH_STYLE_ID\" />" +
            QUIET_TAB_IDS.map { (name, id) -> "<public type=\"drawable\" name=\"$name\" id=\"$id\" />" }.joinToString("") + "</resources>"
        verifyQuietThemeResourceIds(parse(publicFixture()))
        verify(runCatching {
            verifyQuietThemeResourceIds(parse(publicFixture().replace("0x7f140236", "0x7f140999")))
        }.isFailure, "Unexpected Story ring resource IDs must fail closed")
        verify(runCatching {
            verifyQuietThemeResourceIds(parse(publicFixture().replace("name=\"tab_direct_drawable\"", "name=\"other_drawable\"")))
        }.isFailure, "Missing scoped navigation resource must fail closed")
        val unchangedPublic = parse(publicFixture())
        verifyQuietThemeResourceIds(unchangedPublic)
        verify(unchangedPublic.documentElement.getElementsByTagName("public").length == QUIET_STYLE_IDS.size + QUIET_TAB_IDS.size + 1,
            "Theme validation must not add or reassign source resource IDs")

        fun fixture(): String = "<resources><string name=\"login_provider\">Instagram</string>" +
            QUIET_STYLE_IDS.keys.map { name ->
                val attrs = if (name.endsWith("Dark") || name.endsWith("Light")) listOf(
                    "igds_color_primary_background", "igds_color_secondary_background", "igds_color_elevated_background",
                    "igds_color_primary_text", "igds_color_secondary_text", "igds_color_link", "igds_color_primary_button",
                    "igds_color_primary_text_on_media", "igds_color_error_or_destructive",
                ) else (1..5).map { "gradientColor$it" }
                "<style name=\"$name\">" + attrs.joinToString("") {
                    "<item name=\"$it\">@color/original_$it</item>"
                } + "</style>"
            }.joinToString("") + """
                <style name="EffectDarkMode"><item name="igds_color_primary_background">?attr/igds_color_media_background</item></style>
                <style name="GradientSpinnerStyle.Default"><item name="inactiveColour">?attr/igds_color_stroke</item><item name="errorColour">?attr/igds_color_error_or_destructive</item></style>
                </resources>
            """.trimIndent()
        val styles = parse(fixture())
        verify(applyQuietThemeStyles(styles) == 57, "Expected exact semantic styles and default/close-friend ring stops")
        styles.elements("style").filter { it.getAttribute("name") in QUIET_STYLE_IDS }.forEach { style ->
            val name = style.getAttribute("name")
            val items = style.getElementsByTagName("item")
            val values = (0 until items.length).map { items.item(it) as Element }.associate {
                it.getAttribute("name") to it.textContent
            }
            if (name.endsWith("Dark") || name.endsWith("Light")) {
                val mode = if (name.endsWith("Dark")) "dark" else "light"
                verify(values["igds_color_primary_background"] == "@color/friends_only_${mode}_canvas", "Explicit $mode theme must retain its mode")
                verify(values["igds_color_primary_text_on_media"] == "@color/original_igds_color_primary_text_on_media", "Do not recolor media overlays")
                verify(values["igds_color_error_or_destructive"] == "@color/original_igds_color_error_or_destructive", "Do not obscure error/destructive states")
            } else {
                verify(values.values.toSet().size == 1, "Story ring must be a single quiet color")
            }
        }
        verify(styles.elements("string").single().textContent == "Instagram", "Authentication/legal provider identity must stay accurate")
        val effect = styles.elements("style").single { it.getAttribute("name") == "EffectDarkMode" }
        verify(effect.textContent == "?attr/igds_color_media_background", "Camera/effects palettes must survive")
        val spinner = styles.elements("style").single { it.getAttribute("name") == "GradientSpinnerStyle.Default" }
        verify(spinner.textContent == "?attr/igds_color_stroke?attr/igds_color_error_or_destructive", "Seen/error ring semantics must survive")
        verify(runCatching {
            applyQuietThemeStyles(parse(fixture().replace("name=\"IgdsSemanticColorsLight\"", "name=\"unexpected\"")))
        }.isFailure, "Missing semantic family must fail closed")

        fun launchFixture(android12: Boolean): String = """
            <resources><style name="Base.Theme.Launcher" parent="@style/Theme.Instagram">
                ${if (android12) """
                    <item name="android:windowSplashScreenBackground">@color/ig_splash_screen_background</item>
                    <item name="android:windowSplashScreenAnimatedIcon">@drawable/instagram_padded</item>
                    <item name="android:windowSplashScreenBrandingImage">@drawable/from_meta_gradient_padded</item>
                    <item name="android:windowSplashScreenIconBackgroundColor">@color/ig_splash_screen_background</item>
                """ else """
                    <item name="android:windowBackground">@drawable/instagram_splash_screen</item>
                    <item name="android:statusBarColor">@color/ig_splash_screen_background</item>
                    <item name="android:navigationBarColor">@color/ig_splash_screen_background</item>
                """}
                <item name="android:windowLightStatusBar">true</item>
            </style><style name="UnrelatedLoginScreen"><item name="android:windowBackground">@drawable/instagram_splash_screen</item></style></resources>
        """.trimIndent()
        listOf(false to false, true to false, false to true, true to true).forEach { (android12, dark) ->
            val launch = parse(launchFixture(android12))
            verify(applyQuietLaunchTheme(launch, android12, dark) == if (android12) 4 else 3, "Only launch-only attributes should change")
            val launcher = launch.elements("style").first()
            val values = (0 until launcher.getElementsByTagName("item").length).map {
                launcher.getElementsByTagName("item").item(it) as Element
            }.associate { it.getAttribute("name") to it.textContent }
            val expectedCanvas = QUIET_PALETTE.getValue("${if (dark) "dark" else "light"}_canvas")
            verify(values[if (android12) "android:windowSplashScreenBackground" else "android:statusBarColor"] == expectedCanvas,
                "Splash canvas must retain the specific light/dark configuration")
            verify(values["android:windowLightStatusBar"] == "true", "Preserve system status-bar icon legibility")
            verify(launch.elements("style").last().textContent == "@drawable/instagram_splash_screen", "Do not rename unrelated login branding")
            if (android12) {
                verify(values["android:windowSplashScreenAnimatedIcon"] == "@drawable/friends_only_icon", "Android 12+ needs own icon despite explicit source override")
                verify(values["android:windowSplashScreenBrandingImage"] == "@null", "Own app splash must not claim Meta publishes it")
            } else verify(values["android:windowBackground"] == "@drawable/friends_only_launch_background", "Legacy splash needs own launch-only artwork")
            verify(runCatching {
                applyQuietLaunchTheme(parse(launchFixture(android12).replace("@style/Theme.Instagram", "@style/UnrelatedTheme")), android12)
            }.isFailure, "Unexpected launch parent must fail closed")
        }
        verify(runCatching {
            applyQuietLaunchTheme(parse(launchFixture(true).replace("@drawable/instagram_padded", "@drawable/unexpected")), true)
        }.isFailure, "Unexpected explicit source splash icon must fail closed")
        val ownSplash = parse(quietLaunchBackgroundXml())
        verify(ownSplash.elements("item").size == 2 && !quietLaunchBackgroundXml().contains("instagram_"), "Own legacy splash has only neutral canvas and independent icon")
        verify(quietLaunchBackgroundXml(true).contains(QUIET_PALETTE.getValue("dark_canvas")), "Night splash drawable must inline own dark canvas")

        val colors = QUIET_PALETTE
        verify(colors.size == 14 && colors.values.all { it.matches(Regex("#[A-F0-9]{8}")) }, "Palette values must be literal ARGB colors")
        val paletteDocument = parse("""<resources><color name="original_color">#FF010203</color></resources>""")
        verify(appendQuietPaletteColors(paletteDocument) == 14, "Append every independent palette color to the tracked values file")
        val paletteValues = paletteDocument.elements("color").associate { it.getAttribute("name") to it.textContent }
        verify(paletteValues["original_color"] == "#FF010203", "Existing color values must stay unchanged")
        verify(paletteValues.size == 15 && colors.all { (suffix, value) -> paletteValues["friends_only_$suffix"] == value },
            "Every new semantic target must have an independent defined color")
        verify(runCatching { appendQuietPaletteColors(paletteDocument) }.isFailure, "Duplicate palette definitions must fail closed")
        val semanticReferences = styles.elements("item").filter { it.textContent.startsWith("@color/friends_only_") }
        verify(semanticReferences.size == 47, "All 47 native semantic attributes must remain color resource references")
        semanticReferences.forEach { item ->
            verify(paletteValues.containsKey(item.textContent.removePrefix("@color/")),
                "Every native semantic reference must resolve to an allocated palette color")
        }
        verify(styles.elements("style").filter { it.getAttribute("name") in QUIET_STYLE_IDS &&
            (it.getAttribute("name").endsWith("Dark") || it.getAttribute("name").endsWith("Light")) }
            .flatMap { style -> (0 until style.getElementsByTagName("item").length).map { style.getElementsByTagName("item").item(it) as Element } }
            .none { it.textContent.startsWith("#") }, "Literal semantic colors would produce native resourceId=0")
        verify(runCatching {
            applyQuietThemeStyles(parse(fixture().replace("@color/original_igds_color_primary_background", "#FF151A17")))
        }.isFailure, "Reject literal semantic source values rather than producing getColor(0) startup failures")
        verify(!quietLaunchBackgroundXml().contains("@color/friends_only_") && !quietLaunchBackgroundXml(true).contains("@color/friends_only_"),
            "Launch drawable colors must not depend on new values resources")
        fun luminance(hex: String): Double {
            val color = hex.removePrefix("#").takeLast(6).toInt(16)
            fun channel(shift: Int): Double {
                val value = ((color shr shift) and 255) / 255.0
                return if (value <= 0.04045) value / 12.92 else ((value + 0.055) / 1.055).pow(2.4)
            }
            return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
        }
        fun contrast(first: String, second: String): Double {
            val light = luminance(first)
            val dark = luminance(second)
            return (maxOf(light, dark) + 0.05) / (minOf(light, dark) + 0.05)
        }
        listOf("light", "dark").forEach { mode ->
            listOf("canvas", "surface", "elevated").forEach { surface ->
                listOf("text", "secondary_text", "link").forEach { text ->
                    verify(contrast(colors.getValue("${mode}_$surface"), colors.getValue("${mode}_$text")) >= 4.5,
                        "$mode $text on $surface needs readable contrast")
                }
            }
            verify(contrast(colors.getValue("${mode}_button"), "#FFFFFF") >= 4.5, "$mode primary button must retain white-label contrast")
        }
        QUIET_TAB_IDS.keys.forEach { name ->
            val xml = quietTabSelectorXml(name)
            val drawable = parse(xml)
            verify(drawable.elements("item").size == 2 && drawable.elements("vector").size == 2, "Tab needs selected and default vectors")
            verify(!xml.contains("instagram_") && !xml.contains("pathData=\"\""), "Tab artwork must be independent of Instagram assets")
        }
        verify(runCatching { quietTabSelectorXml("instagram_direct_outline_24") }.isFailure, "Never replace shared message/composer artwork")
        // Optional independent check against a locally decoded *real* source resource tree.
        if (args.isNotEmpty()) {
            val res = Path.of(args[0])
            verifyQuietThemeResourceIds(parse(res.resolve("values/public.xml").toFile().readText()))
            verify(applyQuietThemeStyles(parse(res.resolve("values/styles.xml").toFile().readText())) == 57, "Pinned source semantic surface audit")
            verify(applyQuietLaunchTheme(parse(res.resolve("values/styles.xml").toFile().readText()), false) == 3, "Pinned source legacy splash audit")
            verify(applyQuietLaunchTheme(parse(res.resolve("values-v31/styles.xml").toFile().readText()), true) == 4, "Pinned source Android 12 splash audit")
            verify(applyQuietLaunchTheme(parse(res.resolve("values-night/styles.xml").toFile().readText()), false, true) == 3, "Pinned source legacy night splash audit")
            verify(applyQuietLaunchTheme(parse(res.resolve("values-night-v31/styles.xml").toFile().readText()), true, true) == 4, "Pinned source Android 12 night splash audit")
        }
        println("Quiet visual theme checks: $assertions assertions passed")
    }
}
