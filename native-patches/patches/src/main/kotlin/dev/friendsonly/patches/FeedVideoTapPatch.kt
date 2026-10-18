/* SPDX-License-Identifier: GPL-3.0-only */
package dev.friendsonly.patches

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference

private const val CONFIG = "Lcom/instagram/clips/intf/ClipsViewerConfig;"
private const val SOURCE = "Lcom/instagram/clips/intf/ClipsViewerSource;"
private const val LAUNCH_MARKER = "android_purge_26_q2_ClipsPluginImpl_launchClipsViewer_4"
private val LAUNCH_PARAMETERS = listOf(
    "Landroid/view/View;", "Landroidx/fragment/app/FragmentActivity;", CONFIG,
    "Lcom/instagram/common/session/UserSession;", "Z", "Z", "Z",
)

/** Version-pinned launcher identity, with register assumptions checked before any mutation. */
internal fun isFeedViewerLaunchCandidate(method: Method, owner: ClassDef): Boolean {
    if (!owner.type.startsWith("LX/") || method.definingClass != owner.type ||
        method.returnType != "V" || method.parameterTypes.map { it.toString() } != LAUNCH_PARAMETERS ||
        !AccessFlags.STATIC.isSet(method.accessFlags) ||
        listOf(AccessFlags.SYNCHRONIZED, AccessFlags.DECLARED_SYNCHRONIZED, AccessFlags.NATIVE, AccessFlags.ABSTRACT)
            .any { it.isSet(method.accessFlags) }) return false
    val implementation = method.implementation ?: return false
    // All seven arguments are one register wide. Keep p2 and scratch v0 in the 4-bit iget range.
    val configRegister = implementation.registerCount - LAUNCH_PARAMETERS.size + 2
    if (configRegister !in 3..15) return false
    val code = implementation.instructions.toList()
    val first = code.firstOrNull() ?: return false
    if (first.opcode !in setOf(Opcode.CONST_STRING, Opcode.CONST_STRING_JUMBO) ||
        (first as? OneRegisterInstruction)?.registerA != 0 ||
        ((first as? ReferenceInstruction)?.reference as? StringReference)?.string != LAUNCH_MARKER) return false
    val strings = code.mapNotNull { ((it as? ReferenceInstruction)?.reference as? StringReference)?.string }.toSet()
    return "launchClipsViewer" in strings && code.any { instruction ->
        val field = (instruction as? ReferenceInstruction)?.reference as? FieldReference
        instruction.opcode == Opcode.IGET_OBJECT &&
            (instruction as? TwoRegisterInstruction)?.registerB == configRegister &&
            field?.definingClass == CONFIG && field.name == "A00" && field.type == SOURCE
    }
}

/** Verify the only Instagram field that the injected code reads, and its safe Enum upcast. */
internal fun hasFeedViewerSourceContract(config: ClassDef, source: ClassDef): Boolean =
    config.type == CONFIG && source.type == SOURCE && source.superclass == "Ljava/lang/Enum;" &&
        config.fields.count { field ->
            field.name == "A00" && field.type == SOURCE &&
                AccessFlags.PUBLIC.isSet(field.accessFlags) && !AccessFlags.STATIC.isSet(field.accessFlags)
        } == 1

internal fun installFeedViewerGuard(method: MutableMethod) {
    val implementation = checkNotNull(method.implementation) { "Missing native viewer implementation." }
    val first = method.instructions.first()
    val configRegister = implementation.registerCount - LAUNCH_PARAMETERS.size + 2
    check(AccessFlags.STATIC.isSet(method.accessFlags) && configRegister in 3..15 &&
        first.opcode in setOf(Opcode.CONST_STRING, Opcode.CONST_STRING_JUMBO) &&
        (first as? OneRegisterInstruction)?.registerA == 0) { "Unsafe native viewer scratch registers." }
    // This product has no Reels destination. Block the shared full-screen Clips entry
    // before it reads a config, regardless of source, account initialization or prefs.
    // The inline feed, Stories and Direct players do not enter this launcher.
    // Keep the original body intact for reproducible auditing; no touch interception.
    method.addInstructions(0, "return-void")
}

/** These two modal routes do not delegate to the ordinary Clips launcher. */
internal fun isModalReelsLaunchCandidate(method: Method, owner: ClassDef): Boolean {
    if (owner.type != "LX/05b6;" || method.definingClass != owner.type || method.returnType != "V" ||
        !AccessFlags.STATIC.isSet(method.accessFlags) ||
        listOf(AccessFlags.SYNCHRONIZED, AccessFlags.DECLARED_SYNCHRONIZED, AccessFlags.NATIVE, AccessFlags.ABSTRACT)
            .any { it.isSet(method.accessFlags) }) return false
    val impl = method.implementation ?: return false
    if (impl.registerCount != 14 || impl.tryBlocks.isNotEmpty()) return false
    val params = method.parameterTypes.map { it.toString() }
    val strings = impl.instructions.mapNotNull { ((it as? ReferenceInstruction)?.reference as? StringReference)?.string }.toSet()
    if ("clips_feed_viewer" !in strings) return false
    return when (method.name) {
        "A0D" -> params == listOf("Landroidx/fragment/app/Fragment;", CONFIG,
            "Lcom/instagram/common/session/UserSession;", "I", "Z") &&
            "android_purge_26_q2_ClipsPluginImpl_launchClipsViewerForResultFromFragment_2" in strings &&
            impl.instructions.firstOrNull()?.opcode == Opcode.CONST_STRING_JUMBO
        "A0E" -> params == listOf("Landroidx/fragment/app/FragmentActivity;", CONFIG,
            "Lcom/instagram/common/session/UserSession;") &&
            "android_purge_26_q2_ClipsPluginImpl_launchClipsViewerFromDirect" in strings &&
            "launchClipsViewerModal" in strings && impl.instructions.firstOrNull()?.opcode == Opcode.CONST_4
        else -> false
    }
}

internal fun installModalReelsGuard(method: MutableMethod) {
    check(method.instructions.first().opcode != Opcode.RETURN_VOID) { "Reels modal is already guarded." }
    method.addInstructions(0, "return-void")
}

private object FeedViewerLaunch : Fingerprint(custom = ::isFeedViewerLaunchCandidate)
private object ModalReelsLaunch : Fingerprint(custom = ::isModalReelsLaunchCandidate)

@Suppress("unused")
val feedVideoTaps = bytecodePatch(
    name = "Friends Only feed video taps",
    description = "Permanently blocks the shared full-screen Reels launcher from every source; preserves inline feed, Stories and Direct playback.",
    default = true,
) {
    compatibleWith(INSTAGRAM)
    extendWith("extensions/extension.mpe")
    execute {
        check(hasFeedViewerSourceContract(classDefBy(CONFIG), classDefBy(SOURCE))) {
            "Unsupported native viewer source field; no APK produced."
        }
        val matches = FeedViewerLaunch.matchAll()
        val modals = ModalReelsLaunch.matchAll()
        check(matches.size == 1) { "Unsupported native viewer launcher: expected one, found ${matches.size}. No APK produced." }
        check(modals.size == 2) { "Unsupported native modal Reels launchers: expected two, found ${modals.size}. No APK produced." }
        installFeedViewerGuard(matches.single().method)
        modals.forEach { installModalReelsGuard(it.method) }
    }
}
