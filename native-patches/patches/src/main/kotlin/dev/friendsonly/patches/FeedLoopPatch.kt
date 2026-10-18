/* SPDX-License-Identifier: GPL-3.0-only */
package dev.friendsonly.patches

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.*
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference

private const val CONTROLLER = "LX/02Ag;"
private const val PLAYER = "LX/01zI;"
private const val MEDIA = "Lcom/instagram/feed/media/Media;"
private const val STATE = "LX/04cG;"
private const val END_STATE = "LX/04cV;"
private const val PLAYBACK_CONFIG = "LX/09XA;"
private val END_PARAMETERS = listOf(MEDIA, STATE, "Ljava/lang/Integer;")
private val CONFIG_PARAMETERS = listOf("LX/04ZR;", "Lcom/facebook/video/heroplayer/ipc/AudioFocusLossSettings;",
    "Ljava/lang/Integer;", "I", "I", "Z", "Z", "Z", "Z", "Z", "Z", "Z", "Z", "Z")
private fun Instruction.loopField() = (this as? ReferenceInstruction)?.reference as? FieldReference
private fun Instruction.loopMethod() = (this as? ReferenceInstruction)?.reference as? MethodReference
private fun Instruction.loopString() = ((this as? ReferenceInstruction)?.reference as? StringReference)?.string
private fun Instruction.loopRegisters(): List<Int> = when (this) {
    is FiveRegisterInstruction -> listOf(registerC, registerD, registerE, registerF, registerG).take(registerCount)
    is TwoRegisterInstruction -> listOf(registerA, registerB)
    is OneRegisterInstruction -> listOf(registerA)
    else -> emptyList()
}

/** The private feed-only transition both pauses preview playback and activates its end-card. */
internal fun isFeedLoopCandidate(method: Method, owner: ClassDef): Boolean {
    if (owner.type != CONTROLLER || method.definingClass != owner.type || method.name != "A0C" ||
        method.parameterTypes.map { it.toString() } != END_PARAMETERS || method.returnType != "Z" ||
        method.accessFlags != (AccessFlags.PRIVATE.value or AccessFlags.FINAL.value)) return false
    val body = method.implementation ?: return false
    val code = body.instructions.toList()
    if (body.registerCount != 12 || body.tryBlocks.isNotEmpty() || code.size != 92) return false
    fun field(index: Int, opcode: Opcode, registers: List<Int>, reference: String) =
        code[index].opcode == opcode && code[index].loopRegisters() == registers &&
            code[index].loopField()?.toString() == reference
    fun call(index: Int, opcode: Opcode, registers: List<Int>, reference: String) =
        code[index].opcode == opcode && code[index].loopRegisters() == registers &&
            code[index].loopMethod()?.toString() == reference
    if (code[0].opcode != Opcode.CONST_4 || code[0].loopRegisters() != listOf(3) ||
        (code[0] as? NarrowLiteralInstruction)?.narrowLiteral != 0) return false
    for ((load, label, pause) in listOf(Triple(21, 22, 23), Triple(83, 84, 85))) {
        if (!field(load, Opcode.IGET_OBJECT, listOf(1, 8), "$CONTROLLER->A0K:$PLAYER") ||
            code[label].loopString() != "preview_end" || code[label].loopRegisters() != listOf(0) ||
            !call(pause, Opcode.INVOKE_VIRTUAL, listOf(1, 0), "$PLAYER->A0F(Ljava/lang/String;)V")) return false
    }
    return field(50, Opcode.IGET_OBJECT, listOf(1, 10), "$STATE->A1C:$END_STATE") &&
        field(54, Opcode.SGET_OBJECT, listOf(0), "$END_STATE->A02:$END_STATE") &&
        call(55, Opcode.INVOKE_VIRTUAL, listOf(10, 0), "$STATE->A0N($END_STATE)V") &&
        field(87, Opcode.IGET_OBJECT, listOf(0, 8), "$CONTROLLER->A0U:Lkotlin/jvm/functions/Function1;") &&
        call(88, Opcode.INVOKE_INTERFACE, listOf(0, 10),
            "Lkotlin/jvm/functions/Function1;->invoke(Ljava/lang/Object;)Ljava/lang/Object;") &&
        code[89].opcode == Opcode.RETURN && code[89].loopRegisters() == listOf(5)
}

/** Existing native loop state must already be enabled and forwarded to the real player. */
internal fun hasNativeFeedLoopContract(type: ClassDef): Boolean {
    if (type.type != PLAYER) return false
    val constructor = type.methods.singleOrNull { it.name == "<init>" }?.implementation ?: return false
    val init = constructor.instructions.toList()
    if (constructor.registerCount != 35 || init.size < 8 ||
        init[0].opcode != Opcode.CONST_4 || init[0].loopRegisters() != listOf(2) ||
        (init[0] as? NarrowLiteralInstruction)?.narrowLiteral != 1 ||
        init[7].opcode != Opcode.IPUT_BOOLEAN || init[7].loopRegisters() != listOf(2, 7) ||
        init[7].loopField()?.toString() != "$PLAYER->A0N:Z") return false
    val binding = type.methods.singleOrNull { it.name == "A0B" } ?: return false
    if (binding.returnType != "V" || binding.parameterTypes.map { it.toString() } !=
        listOf(MEDIA, "LX/0AVy;", "LX/0JaO;", "LX/09XA;", "I", "I", "I", "Z", "Z", "Z")) return false
    val body = binding.implementation ?: return false
    val code = body.instructions.toList()
    return body.registerCount == 43 && code.size > 96 &&
        code[95].opcode == Opcode.IGET_BOOLEAN && code[95].loopRegisters() == listOf(0, 11) &&
        code[95].loopField()?.toString() == "$PLAYER->A0N:Z" &&
        code[96].opcode == Opcode.INVOKE_VIRTUAL && code[96].loopRegisters() == listOf(4, 0) &&
        code[96].loopMethod()?.toString() == "LX/09ZA;->A0l(Z)V"
}

/** Only the feed preview factory opts out of native looping. Do not alter shared players. */
internal fun isFeedLoopConfigCandidate(method: Method, owner: ClassDef): Boolean {
    if (owner.type != CONTROLLER || method.definingClass != owner.type || method.name != "A01" ||
        method.parameterTypes.map { it.toString() } != listOf(MEDIA, STATE, CONTROLLER) ||
        method.returnType != PLAYBACK_CONFIG || method.accessFlags !=
        (AccessFlags.PUBLIC.value or AccessFlags.STATIC.value or AccessFlags.FINAL.value)) return false
    val body = method.implementation ?: return false
    val code = body.instructions.toList()
    if (body.registerCount != 20 || body.tryBlocks.isNotEmpty() || code.size != 120) return false
    if (code[5].opcode != Opcode.CONST_4 || code[5].loopRegisters() != listOf(10) ||
        (code[5] as? NarrowLiteralInstruction)?.narrowLiteral != 0 ||
        code[86].opcode != Opcode.CONST_4 || code[86].loopRegisters() != listOf(13) ||
        (code[86] as? NarrowLiteralInstruction)?.narrowLiteral != 1) return false
    // v12=false is the loop value; v13=true enables that false override.
    // The fallback branch already inherits native looping with v13=false.
    return listOf(93 to 12, 110 to 12, 111 to 13).all { (index, target) ->
        code[index].opcode == Opcode.MOVE && code[index].loopRegisters() == listOf(target, 10)
    } && listOf(100, 118).all { index ->
        val call = code[index] as? RegisterRangeInstruction
        val reference = code[index].loopMethod()
        code[index].opcode == Opcode.INVOKE_DIRECT_RANGE && call?.startRegister == 5 && call.registerCount == 15 &&
            reference?.definingClass == PLAYBACK_CONFIG && reference.name == "<init>" &&
            reference.parameterTypes.map { it.toString() } == CONFIG_PARAMETERS && reference.returnType == "V"
    }
}

internal fun hasLoopOverrideFieldContract(type: ClassDef): Boolean {
    if (type.type != PLAYBACK_CONFIG) return false
    val method = type.methods.singleOrNull {
        it.name == "<init>" && it.parameterTypes.map { value -> value.toString() } == CONFIG_PARAMETERS
    } ?: return false
    val code = method.implementation?.instructions?.toList() ?: return false
    return method.implementation!!.registerCount == 15 && code.size == 16 &&
        listOf(Triple(2, 7, "A09"), Triple(3, 8, "A0A")).all { (index, parameter, name) ->
            code[index].opcode == Opcode.IPUT_BOOLEAN && code[index].loopRegisters() == listOf(parameter, 0) &&
                code[index].loopField()?.toString() == "$PLAYBACK_CONFIG->$name:Z"
        }
}

internal fun installFeedLoopConfig(method: MutableMethod, owner: ClassDef) {
    check(isFeedLoopConfigCandidate(method, owner)) { "Unsupported feed preview loop override; no APK produced." }
    // Inherit the feed player's existing true loop setting. No new play/seek calls.
    method.replaceInstruction(86, "const/4 v13, 0x0")
}

internal fun installFeedLoop(method: MutableMethod, owner: ClassDef) {
    check(isFeedLoopCandidate(method, owner)) { "Unsupported feed preview-end transition; no APK produced." }
    // The real feed player already loops. Decline the transition before either native pause,
    // the end-card state update, or its renderer. Never seek/play a paused/offscreen player.
    // Keep the entire original body and its callers intact; v0 is an existing local.
    method.addInstructions(0, "const/4 v0, 0x0\nreturn v0")
}

private object FeedLoopTransition : Fingerprint(custom = ::isFeedLoopCandidate)
private object FeedLoopConfig : Fingerprint(custom = ::isFeedLoopConfigCandidate)

@Suppress("unused")
val feedVideoLoop = bytecodePatch(
    name = "Friends Only feed video loop",
    description = "Keeps native inline feed video looping instead of pausing for Watch in Reels and Watch again end cards.",
    default = true,
) {
    compatibleWith(INSTAGRAM)
    execute {
        check(hasNativeFeedLoopContract(classDefBy(PLAYER))) { "Unsupported native feed-loop contract; no APK produced." }
        check(hasLoopOverrideFieldContract(classDefBy(PLAYBACK_CONFIG))) { "Unsupported playback loop fields; no APK produced." }
        val matches = FeedLoopTransition.matchAll()
        val configs = FeedLoopConfig.matchAll()
        check(matches.size == 1) { "Expected one feed preview-end transition, found ${matches.size}; no APK produced." }
        check(configs.size == 1) { "Expected one feed preview loop override, found ${configs.size}; no APK produced." }
        installFeedLoopConfig(configs.single().method, classDefBy(CONTROLLER))
        installFeedLoop(matches.single().method, classDefBy(CONTROLLER))
    }
}
