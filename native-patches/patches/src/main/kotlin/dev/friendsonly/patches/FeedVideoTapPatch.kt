/* SPDX-License-Identifier: GPL-3.0-only */
package dev.friendsonly.patches

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patcher.util.smali.ExternalLabel
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
    val configRegister = implementation.registerCount - LAUNCH_PARAMETERS.size + 2
    val first = method.instructions.first()
    check(AccessFlags.STATIC.isSet(method.accessFlags) && configRegister in 3..15 &&
        first.opcode in setOf(Opcode.CONST_STRING, Opcode.CONST_STRING_JUMBO) &&
        (first as? OneRegisterInstruction)?.registerA == 0) { "Unsafe native viewer scratch registers." }
    // Allowed paths rejoin the original first instruction, which overwrites v0 before using it.
    // Do not cancel touch dispatch, suppress media playback, or intercept other destinations.
    method.addInstructionsWithLabels(0, """
        if-eqz v$configRegister, :friends_only_original_viewer
        iget-object v0, v$configRegister, $CONFIG->A00:$SOURCE
        invoke-static { v0 }, Ldev/friendsonly/patch/runtime/FocusHooks;->shouldBlockFeedReelsOpen(Ljava/lang/Enum;)Z
        move-result v0
        if-eqz v0, :friends_only_original_viewer
        return-void
    """.trimIndent(), ExternalLabel("friends_only_original_viewer", first))
}

private object FeedViewerLaunch : Fingerprint(custom = ::isFeedViewerLaunchCandidate)

@Suppress("unused")
val feedVideoTaps = bytecodePatch(
    name = "Friends Only feed video taps",
    description = "Keeps Home video taps out of the Reels viewer when enabled; leaves inline playback and other entry sources unchanged.",
    default = true,
) {
    compatibleWith(INSTAGRAM)
    extendWith("extensions/extension.mpe")
    execute {
        check(hasFeedViewerSourceContract(classDefBy(CONFIG), classDefBy(SOURCE))) {
            "Unsupported native viewer source field; no APK produced."
        }
        val matches = FeedViewerLaunch.matchAll()
        check(matches.size == 1) { "Unsupported native viewer launcher: expected one, found ${matches.size}. No APK produced." }
        installFeedViewerGuard(matches.single().method)
    }
}
