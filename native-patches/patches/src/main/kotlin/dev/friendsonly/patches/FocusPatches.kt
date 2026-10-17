/* SPDX-License-Identifier: GPL-3.0-only
 * Fingerprints adapted from FeurStagram; see native-patches/NOTICE.md.
 */
package dev.friendsonly.patches

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.ApkFileType
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.NarrowLiteralInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference

private const val HOOK = "Ldev/friendsonly/patch/runtime/FocusHooks;"
private const val EXTENSION = "extensions/extension.mpe"
// Verified against the resources.arsc table in the supported 451 arm64 build.
// Match both named surfaces so unrelated ViewGroup binders cannot qualify.
private const val TAB_BAR_RESOURCE = 0x7f0b408d
private const val TAB_SHADOW_RESOURCE = 0x7f0b408e
internal val INSTAGRAM = Compatibility(
    name = "Instagram",
    packageName = "com.instagram.android",
    apkFileType = ApkFileType.APK,
    targets = listOf(AppTarget(version = "451.0.0.20.80")),
)

private fun fieldType(instruction: Instruction): String? =
    ((instruction as? ReferenceInstruction)?.reference as? FieldReference)?.type

private object TabBar : Fingerprint(
    name = "<init>",
    parameters = listOf("Landroid/view/View;"),
    custom = { method, owner ->
        owner.type.startsWith("LX/") &&
            method.implementation?.instructions?.let { code ->
                code.any { (it as? NarrowLiteralInstruction)?.narrowLiteral == TAB_BAR_RESOURCE } &&
                    code.any { (it as? NarrowLiteralInstruction)?.narrowLiteral == TAB_SHADOW_RESOURCE } &&
                    code.count { it.opcode == Opcode.IPUT_OBJECT && fieldType(it) == "Landroid/view/ViewGroup;" } == 1 &&
                    code.any { it.opcode == Opcode.IPUT_OBJECT && fieldType(it) == "Landroid/view/View;" }
            } == true
    },
)

@Suppress("unused")
val nativeControls = bytecodePatch(
    name = "Friends Only native controls",
    description = "Reversible native Reels, Explore and creation tab hiding; Home long press opens local settings.",
    default = true,
) {
    compatibleWith(INSTAGRAM)
    extendWith(EXTENSION)
    execute {
        val matches = TabBar.matchAll()
        check(matches.size == 1) { "Unsupported tab-bar layout: expected one method, found ${matches.size}. No APK produced." }
        matches.single().method.apply {
            val store = instructions.single {
                it.opcode == Opcode.IPUT_OBJECT && fieldType(it) == "Landroid/view/ViewGroup;"
            }
            val register = (store as TwoRegisterInstruction).registerA
            addInstructions(store.location.index + 1,
                "invoke-static/range { v$register .. v$register }, $HOOK->install(Landroid/view/ViewGroup;)V")
        }
    }
}

private fun Instruction.isUriLoad() =
    opcode == Opcode.IGET_OBJECT && fieldType(this) == "Ljava/net/URI;"

private object RequestStart : Fingerprint(
    definingClass = "Lcom/instagram/api/tigon/TigonServiceLayer;",
    custom = { method, _ ->
        method.name.startsWith("startRequest") &&
            method.implementation?.instructions?.any { it.isUriLoad() } == true
    },
)

@Suppress("unused")
val discoveryBlocking = bytecodePatch(
    name = "Friends Only discovery blocking",
    description = "Blocks only known Reels/Explore listing requests when enabled; preserves feed, Stories, posts and Direct.",
    default = true,
) {
    compatibleWith(INSTAGRAM)
    extendWith(EXTENSION)
    execute {
        val matches = RequestStart.matchAll()
        check(matches.size == 1) { "Unsupported request entry point: found ${matches.size} methods." }
        matches.single().method.apply {
            val loads = instructions.filter { it.isUriLoad() }
            check(loads.size == 1) { "Ambiguous request URI load; refusing to patch." }
            val load = loads.single()
            // Never introduce an uncaught checked exception into Instagram.
            val nextAddress = instructions.take(load.location.index + 1).sumOf { it.codeUnits }
            check(implementation!!.tryBlocks.any { block ->
                nextAddress >= block.startCodeAddress &&
                    nextAddress < block.startCodeAddress + block.codeUnitCount &&
                    block.exceptionHandlers.any { handler ->
                        handler.exceptionType in setOf(null, "Ljava/io/IOException;", "Ljava/lang/Exception;", "Ljava/lang/Throwable;")
                    }
            }) { "No compatible error handler at URI check; refusing to patch." }
            val register = (load as OneRegisterInstruction).registerA
            addInstructions(load.location.index + 1,
                "invoke-static/range { v$register .. v$register }, $HOOK->checkRequest(Ljava/net/URI;)V")
        }
    }
}
