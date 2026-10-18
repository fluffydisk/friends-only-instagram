/* SPDX-License-Identifier: GPL-3.0-only */
package dev.friendsonly.patches

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.*
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference

private const val FEED_ACTIONS = "LX/06b1;"
private const val SPEED_CONTROLLER = "LX/01zY;"
private val HOLD_PARAMETERS = listOf("Landroid/view/View;", "Lcom/instagram/feed/media/Media;", "LX/0A0J;", "LX/04cG;")
private const val CONFIG_OWNER = "Lcom/facebook/mobileconfig/factory/MobileConfigUnsafeContext;"

internal data class FeedSpeedPlan(val gateResults: List<Int>, val centerRejection: Int)

private fun Instruction.speedMethod() = (this as? ReferenceInstruction)?.reference as? MethodReference
private fun Instruction.speedString() = ((this as? ReferenceInstruction)?.reference as? StringReference)?.string

/**
 * Enable the existing FeedVideo2xSpeed implementation in this version's feed-video
 * long-press handler only. Do not change shared mobileconfig getters, gesture
 * dispatch, players, the Reels viewer, Stories, Direct, or authentication.
 *
 * These three results gate only this method's native 2x attempt. The remaining
 * checks still require a live player and the touched media to match that player.
 * Native release/cancel/recycle callbacks remain responsible for resetting 1x.
 */
internal fun inspectFeedSpeedHandler(method: Method, owner: ClassDef): FeedSpeedPlan? {
    if (owner.type != FEED_ACTIONS || method.definingClass != owner.type || method.name != "A06" ||
        method.returnType != "V" || method.parameterTypes.map { it.toString() } != HOLD_PARAMETERS ||
        AccessFlags.STATIC.isSet(method.accessFlags) || AccessFlags.ABSTRACT.isSet(method.accessFlags)) return null
    val body = method.implementation ?: return null
    val code = body.instructions.toList()
    if (body.registerCount != 33 || body.tryBlocks.size != 1 || code.size != 980) return null
    var address = 0
    val addresses = code.map { instruction -> address.also { address += instruction.codeUnits } }
    fun methodAt(index: Int, ownerType: String, name: String, parameters: List<String>, returns: String): Boolean {
        val reference = code[index].speedMethod() ?: return false
        return reference.definingClass == ownerType && reference.name == name &&
            reference.parameterTypes.map { it.toString() } == parameters && reference.returnType == returns
    }
    fun target(index: Int): Int? = (code[index] as? OffsetInstruction)?.let {
        addresses.indexOf(addresses[index] + it.codeOffset).takeIf { found -> found >= 0 }
    }
    fun register(index: Int) = (code[index] as? OneRegisterInstruction)?.registerA
    fun fieldAt(index: Int, reference: String) =
        (code[index] as? ReferenceInstruction)?.reference?.toString() == reference
    if (code[935].speedString() != "FeedVideo2xSpeed" ||
        code[934].speedString() != "Error setting 2x playback speed" ||
        !methodAt(44, SPEED_CONTROLLER, "A01", listOf(SPEED_CONTROLLER, "Z"), "Z") ||
        !methodAt(72, "LX/07JA;", "A00", listOf("Lcom/instagram/common/session/UserSession;"), "Z") ||
        code[72].opcode != Opcode.INVOKE_STATIC ||
        !methodAt(91, CONFIG_OWNER, "BZb", listOf("J"), "Z") ||
        !methodAt(109, CONFIG_OWNER, "BZb", listOf("J"), "Z") ||
        !methodAt(707, CONFIG_OWNER, "BZb", listOf("J"), "Z")) return null
    for ((index, literal) in listOf(88 to 36326953648935618L, 106 to 36326953648673470L,
            705 to 36326953648739007L)) {
        if (code[index].opcode != Opcode.CONST_WIDE ||
            (code[index] as? WideLiteralInstruction)?.wideLiteral != literal) return null
    }
    val results = listOf(92, 110, 708)
    if ((listOf(73) + results).any { code[it].opcode != Opcode.MOVE_RESULT || register(it) != 0 }) return null
    if (listOf(74, 93, 111).any { code[it].opcode != Opcode.IF_EQZ || register(it) != 0 || target(it) != 142 } ||
        code[709].opcode != Opcode.IF_NEZ || register(709) != 0 || target(709) != 711 ||
        code[710].opcode != Opcode.GOTO_16 || target(710) != 142) return null
    val center = code[727] as? TwoRegisterInstruction ?: return null
    if (code[727].opcode != Opcode.IF_EQ || center.registerA != 1 || center.registerB != 0 || target(727) != 142 ||
        !fieldAt(725, "LX/0009;->A00:Ljava/lang/Integer;") ||
        !fieldAt(726, "LX/0009;->A0C:Ljava/lang/Integer;") ||
        !fieldAt(728, "$SPEED_CONTROLLER->A05:Lkotlin/jvm/functions/Function0;")) return null
    // Keep the actual-playing and matching-media checks, and exact native 2f call.
    if (!methodAt(732, "LX/01zI;", "A0J", emptyList(), "Z") ||
        code[734].opcode != Opcode.IF_EQZ || target(734) != 142 ||
        !methodAt(735, "LX/01zI;", "A06", emptyList(), "Lcom/instagram/feed/media/Media;") ||
        !methodAt(909, "Ljava/util/concurrent/atomic/AtomicBoolean;", "compareAndSet", listOf("Z", "Z"), "Z") ||
        code[926].opcode != Opcode.CONST_HIGH16 ||
        (code[926] as? NarrowLiteralInstruction)?.narrowLiteral != 0x40000000 ||
        !methodAt(931, "LX/09bL;", "A0M", listOf("F"), "V")) return null
    return FeedSpeedPlan(results, 727)
}

internal fun hasFeedSpeedResetContract(method: Method): Boolean {
    if (method.definingClass != SPEED_CONTROLLER || method.name != "A01" || method.returnType != "Z" ||
        method.parameterTypes.map { it.toString() } != listOf(SPEED_CONTROLLER, "Z") ||
        !AccessFlags.STATIC.isSet(method.accessFlags)) return false
    val body = method.implementation ?: return false
    val code = body.instructions.toList()
    return body.registerCount == 9 && body.tryBlocks.size == 1 && code.size == 40 &&
        code[29].speedString() == "FeedVideo2xSpeed" &&
        code[20].opcode == Opcode.CONST_HIGH16 &&
        (code[20] as? NarrowLiteralInstruction)?.narrowLiteral == 0x3f800000 &&
        code[25].speedMethod()?.toString() == "LX/09bL;->A0M(F)V" &&
        code[2].speedMethod()?.toString() == "Ljava/util/concurrent/atomic/AtomicBoolean;->getAndSet(Z)Z" &&
        code[38].speedMethod()?.toString() == "Landroid/view/ViewParent;->requestDisallowInterceptTouchEvent(Z)V"
}

/**
 * This wrapper's single experiment key is dedicated to FeedVideo2xSpeed. Its
 * verified callers also gate native view binding, state reset and lifecycle
 * cleanup. Enable the wrapper itself so those cleanup paths stay consistent;
 * overriding only the start call would leave the background reset disabled.
 * The shared MobileConfigUnsafeContext API and every other key are unchanged.
 */
internal fun hasFeedSpeedMasterContract(method: Method): Boolean {
    if (method.definingClass != "LX/07JA;" || method.name != "A00" || method.returnType != "Z" ||
        method.parameterTypes.map { it.toString() } != listOf("Lcom/instagram/common/session/UserSession;") ||
        !AccessFlags.STATIC.isSet(method.accessFlags)) return false
    val body = method.implementation ?: return false
    val code = body.instructions.toList()
    return body.registerCount == 3 && body.tryBlocks.isEmpty() && code.size == 9 &&
        code[4].opcode == Opcode.CONST_WIDE &&
        (code[4] as? WideLiteralInstruction)?.wideLiteral == 36326953648804544L &&
        code[6].opcode == Opcode.INVOKE_INTERFACE &&
        code[6].speedMethod()?.toString() == "$CONFIG_OWNER->BZb(J)Z" &&
        code[7].opcode == Opcode.MOVE_RESULT && (code[7] as? OneRegisterInstruction)?.registerA == 0 &&
        code[8].opcode == Opcode.RETURN && (code[8] as? OneRegisterInstruction)?.registerA == 0
}

internal fun installFeedSpeedMaster(method: MutableMethod) {
    check(hasFeedSpeedMasterContract(method)) { "Unsupported dedicated feed-speed feature gate; no APK produced." }
    method.replaceInstruction(7, "const/4 v0, 0x1")
}

internal fun installFeedSpeed(method: MutableMethod, owner: ClassDef) {
    val plan = checkNotNull(inspectFeedSpeedHandler(method, owner)) { "Unsupported feed-video 2x handler; no APK produced." }
    // Replacing move-result with const keeps the native calls and their side
    // effects; the unused result is legal DEX. No locals or parameters shift.
    for (index in plan.gateResults) method.replaceInstruction(index, "const/4 v0, 0x1")
    // Native 2x used the left/right edges only. Allow the center as well while
    // leaving long-press timing, scroll slop, pointer/cancel handling untouched.
    method.replaceInstruction(plan.centerRejection, "nop")
}

private object FeedSpeedHandler : Fingerprint(custom = { method, owner -> inspectFeedSpeedHandler(method, owner) != null })
private object FeedSpeedMaster : Fingerprint(custom = { method, _ -> hasFeedSpeedMasterContract(method) })

@Suppress("unused")
val feedHoldSpeed = bytecodePatch(
    name = "Friends Only feed hold speed",
    description = "Enables native in-place 2x playback while holding a playing feed video, with native release/cancel/recycle reset.",
    default = true,
) {
    compatibleWith(INSTAGRAM)
    execute {
        val reset = classDefBy(SPEED_CONTROLLER).methods.singleOrNull { it.name == "A01" }
        check(reset != null && hasFeedSpeedResetContract(reset)) { "Unsupported native feed speed reset; no APK produced." }
        val master = FeedSpeedMaster.matchAll()
        check(master.size == 1) { "Expected one dedicated feed-speed gate; no APK produced." }
        val matches = FeedSpeedHandler.matchAll()
        check(matches.size == 1) { "Expected one native feed hold handler, found ${matches.size}; no APK produced." }
        val method = matches.single().method
        installFeedSpeed(method, classDefBy(FEED_ACTIONS))
        installFeedSpeedMaster(master.single().method)
    }
}
