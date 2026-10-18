/* SPDX-License-Identifier: GPL-3.0-only */
package dev.friendsonly.patches

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.NarrowLiteralInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.security.MessageDigest

internal val DESIGN_LAYOUTS = linkedMapOf(
    "row_feed_media_profile_header" to "0x7f0e149c", "row_feed_media_media_group" to "0x7f0e1498",
    "row_feed_comment" to "0x7f0e148f", "row_feed_view_all_comments" to "0x7f0e14ad",
    "direct_inbox_row_layout" to "0x7f0e0415", "direct_inbox_row_layout_deferred_remaining_cold_stubs" to "0x7f0e0416",
    "direct_text_message_text_linear_layout" to "0x7f0e04c9", "direct_text_message_text_view" to "0x7f0e04ca",
    "direct_composer_bar_with_overflow_for_composer_buttons" to "0x7f0e039f", "direct_composer_bar_with_stickers" to "0x7f0e03a0",
    "profile_header_user_info_row" to "0x7f0e1202", "profile_header_user_info_row_lazy_bio" to "0x7f0e1203",
    "profile_header_user_info_row_no_barrier" to "0x7f0e1204", "profile_header_user_actions_row" to "0x7f0e11ff",
    "reel_item_avatar_content" to "0x7f0e13a0", "reel_item_label_view_layout" to "0x7f0e13a4",
    "reel_item_label_view_layout_subtitle_text" to "0x7f0e13a5", "direct_thread_action_bar_left_aligned" to "0x7f0e04cd",
)
internal const val WELCOME = "dev.friendsonly.patch.runtime.WelcomeActivity"
internal const val NATIVE_ENTRY = "com.instagram.mainactivity.InstagramMainActivity"
private fun Element.elements(): List<Element> = (0 until childNodes.length).mapNotNull { childNodes.item(it) as? Element }

/** Only MAIN/LAUNCHER aliases change target. Deep links and all real auth Activities stay native. */
internal fun installWelcome(document: Document): Int {
    val manifest = document.documentElement
    check(manifest.getAttribute("package") == FRIENDS_ONLY_PACKAGE)
    val application = manifest.elements().single { it.tagName == "application" }
    check(application.elements().none { it.getAttribute("android:name") == WELCOME })
    check(application.elements().count { it.tagName == "activity" && it.getAttribute("android:name") == NATIVE_ENTRY } == 1)
    val aliases = application.elements().filter { it.tagName == "activity-alias" && it.elements().any { filter ->
        filter.tagName == "intent-filter" && filter.elements().any { it.tagName == "action" && it.getAttribute("android:name") == "android.intent.action.MAIN" } &&
            filter.elements().any { it.tagName == "category" && it.getAttribute("android:name") == "android.intent.category.LAUNCHER" }
    } }
    check(aliases.isNotEmpty() && aliases.all { it.getAttribute("android:targetActivity") == NATIVE_ENTRY })
    val welcome = document.createElement("activity").apply {
        setAttribute("android:name", WELCOME)
        setAttribute("android:label", FRIENDS_ONLY_LABEL)
        setAttribute("android:exported", "false")
        setAttribute("android:theme", "@android:style/Theme.Material.Light.NoActionBar")
    }
    // An alias must follow its target declaration in Android's manifest parser.
    application.insertBefore(welcome, aliases.first())
    aliases.forEach { it.setAttribute("android:targetActivity", WELCOME) }
    return aliases.size
}

internal fun isDesignInflater(method: Method, owner: ClassDef): Boolean {
    if (owner.type !in setOf("LX/01Mq;", "LX/0A3y;") || owner.superclass != "Landroid/view/LayoutInflater;" ||
        method.definingClass != owner.type || method.name != "inflate" || method.returnType != "Landroid/view/View;" ||
        method.parameterTypes.map { it.toString() } != listOf("I", "Landroid/view/ViewGroup;", "Z") ||
        !AccessFlags.PUBLIC.isSet(method.accessFlags) || AccessFlags.STATIC.isSet(method.accessFlags)) return false
    val implementation = method.implementation ?: return false
    val code = implementation.instructions.toList()
    val isFast = owner.type == "LX/01Mq;"
    if (implementation.registerCount != 6 || code.size != (if (isFast) 56 else 35) ||
        implementation.tryBlocks.size != (if (isFast) 2 else 1)) return false
    val returns = code.filter { it.opcode == Opcode.RETURN_OBJECT }
    if (returns.size != (if (isFast) 3 else 2) || returns.any { (it as? OneRegisterInstruction)?.registerA != 0 }) return false
    return code.count {
        val ref = (it as? ReferenceInstruction)?.reference as? MethodReference
        ref?.definingClass == "Landroid/view/LayoutInflater;" && ref.name == "inflate" &&
            ref.parameterTypes.map { p -> p.toString() } == listOf("I", "Landroid/view/ViewGroup;", "Z")
    } == 4 && code.any {
        val ref = (it as? ReferenceInstruction)?.reference as? MethodReference
        ref?.definingClass == "Ljava/util/concurrent/locks/ReentrantLock;" && ref.name == "unlock"
    }
}

internal fun decorateInflater(method: MutableMethod, owner: ClassDef) {
    check(isDesignInflater(method, owner)) { "Unsupported design inflater; no APK produced." }
    method.instructions.withIndex().filter { it.value.opcode == Opcode.RETURN_OBJECT }.map { it.index }.reversed().forEach { index ->
        // Replace the return itself so branches targeting that return cannot skip our hook.
        // Original return v0, resource p1=v3, attachToRoot p3=v5; no new scratch register.
        method.replaceInstruction(index, "invoke-static { v0, v3, v5 }, Ldev/friendsonly/patch/runtime/ScreenDesign;->inflated(Landroid/view/View;IZ)V")
        method.addInstruction(index + 1, "return-object v0")
    }
}

/** The native UFI holder owns real like/comment/share/save controls, even in programmatic rows. */
internal fun isFeedActionHolder(method: Method, owner: ClassDef): Boolean {
    if (owner.type != "LX/07bY;" || owner.superclass != "LX/0Gb5;" || method.definingClass != owner.type ||
        method.name != "<init>" || method.parameterTypes.map { it.toString() } != listOf("Landroid/view/View;") ||
        method.returnType != "V" || method.accessFlags != (AccessFlags.PUBLIC.value or AccessFlags.CONSTRUCTOR.value)) return false
    val implementation = method.implementation ?: return false
    val code = implementation.instructions.toList()
    if (implementation.registerCount != 6 || implementation.tryBlocks.isNotEmpty() || code.size != 104 ||
        code.count { it.opcode == Opcode.RETURN_VOID } != 1 || code[101].opcode != Opcode.RETURN_VOID) return false
    val ids = code.mapNotNull { (it as? NarrowLiteralInstruction)?.narrowLiteral }.toSet()
    return ids.containsAll(setOf(0x7f0b3724, 0x7f0b3722, 0x7f0b3729, 0x7f0b3728, 0x7f0b3766, 0x7f0b3740)) &&
        code.any {
            val ref = (it as? ReferenceInstruction)?.reference as? MethodReference
            ref?.definingClass == "LX/0Gb5;" && ref.name == "<init>" &&
                ref.parameterTypes.map { p -> p.toString() } == listOf("Landroid/view/View;")
        }
}

internal fun decorateFeedActions(method: MutableMethod, owner: ClassDef) {
    check(isFeedActionHolder(method, owner)) { "Unsupported feed action holder; no APK produced." }
    method.addInstruction(101,
        "invoke-static { v5 }, Ldev/friendsonly/patch/runtime/ScreenDesign;->feedActions(Landroid/view/View;)V")
}

private object FeedActionHolder : Fingerprint(definingClass = "LX/07bY;", custom = ::isFeedActionHolder)
private class DesignInflater(owner: String) : Fingerprint(definingClass = owner, custom = ::isDesignInflater)
private val designInflation = bytecodePatch {
    compatibleWith(INSTAGRAM)
    extendWith("extensions/extension.mpe")
    execute {
        val holders = FeedActionHolder.matchAll()
        check(holders.size == 1) { "Expected exactly one native feed action holder." }
        decorateFeedActions(holders.single().method, classDefBy("LX/07bY;"))
        for (owner in listOf("LX/01Mq;", "LX/0A3y;")) {
            val matches = DesignInflater(owner).matchAll()
            check(matches.size == 1) { "Expected exactly one supported design inflater: $owner." }
            val match = matches.single()
            decorateInflater(match.method, classDefBy(owner))
        }
    }
}
private object DesignAssets

@Suppress("unused")
val friendsDesign = resourcePatch(
    name = "Friends Only native design",
    description = "Own welcome and full-page settings, Manrope typography and bounded native feed/chat/profile presentation; real authentication stays native.",
    default = true,
) {
    compatibleWith(INSTAGRAM)
    dependsOn(appIdentity, designInflation)
    execute {
        document("res/values/public.xml").use { document ->
            val entries = document.documentElement.elements().filter { it.tagName == "public" && it.getAttribute("type") == "layout" }
            DESIGN_LAYOUTS.forEach { (name, id) ->
                check(entries.singleOrNull { it.getAttribute("name") == name }?.getAttribute("id")?.lowercase() == id) {
                    "Unsupported native design layout: $name."
                }
            }
        }
        document("res/values/public.xml").use { doc ->
            check(doc.documentElement.elements().single { it.tagName == "public" &&
                it.getAttribute("type") == "drawable" && it.getAttribute("name") == "instagram_wordmark_ig_wordmark_r3_finalize_v1" }
                .getAttribute("id").lowercase() == "0x7f082a13")
        }
        for (qualifier in listOf("xhdpi", "night-xhdpi", "xxhdpi", "night-xxhdpi")) {
            val bytes = checkNotNull(DesignAssets.javaClass.getResourceAsStream("/design/wordmark-$qualifier.webp")).use { it.readBytes() }
            get("res/drawable-$qualifier/instagram_wordmark_ig_wordmark_r3_finalize_v1.webp").apply {
                check(isFile) { "Missing expected wordmark density: $qualifier." }
                writeBytes(bytes)
            }
        }
        document("res/values/public.xml").use { doc ->
            check(doc.documentElement.elements().single { it.tagName == "public" &&
                it.getAttribute("type") == "drawable" && it.getAttribute("name") == "ig_logo" }
                .getAttribute("id").lowercase() == "0x7f0820ec")
        }
        for (qualifier in listOf("xhdpi", "xxhdpi")) {
            val bytes = checkNotNull(DesignAssets.javaClass.getResourceAsStream("/design/login-mark-$qualifier.png")).use { it.readBytes() }
            get("res/drawable-$qualifier/ig_logo.png").apply {
                check(isFile) { "Missing pinned native login logo density." }
                writeBytes(bytes)
            }
        }
        document("AndroidManifest.xml").use { installWelcome(it) }
        for ((name, digest) in mapOf(
            "Manrope.ttf" to "3ae11c49db0455a3cc33e37d380f20fdb8c7f8b41dc07625c177e3d87a9d6ae6",
            "OFL-Manrope.txt" to "58172e0c0fac2cda8a37b348164bb55e44b0e69051e557e92b1d3f6910141f7b")) {
            val bytes = checkNotNull(DesignAssets.javaClass.getResourceAsStream("/design/$name")) { "Missing bundled design asset." }.use { it.readBytes() }
            check(MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) } == digest)
            get("assets/friends_only/$name").apply { parentFile.mkdirs(); writeBytes(bytes) }
        }
    }
}
