/* SPDX-License-Identifier: GPL-3.0-only */
package dev.friendsonly.patches

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patcher.util.smali.ExternalLabel
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OffsetInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference

private const val SETTINGS_ACTION = "LX/07Z1;"
private const val MENU_ACTION = "LX/057U;"
private const val MENU_CONTROLLER = "LX/0AU0;"
private const val ACTIVITY = "Landroidx/fragment/app/FragmentActivity;"
private const val NATIVE_SETTINGS = "LX/0HCm;"

private fun settingsField(instruction: Any): FieldReference? =
    ((instruction as? ReferenceInstruction)?.reference as? FieldReference)

/** Match only the supported profile menu's concrete Settings action, never a generic router. */
internal fun isSettingsActionCandidate(method: Method, owner: ClassDef): Boolean {
    if (owner.type != SETTINGS_ACTION || owner.superclass != MENU_ACTION ||
        method.definingClass != owner.type || method.name != "A02" ||
        method.returnType != "V" || method.parameterTypes.isNotEmpty() ||
        !AccessFlags.PUBLIC.isSet(method.accessFlags) || !AccessFlags.FINAL.isSet(method.accessFlags) ||
        listOf(AccessFlags.STATIC, AccessFlags.SYNCHRONIZED, AccessFlags.DECLARED_SYNCHRONIZED,
            AccessFlags.NATIVE, AccessFlags.ABSTRACT).any { it.isSet(method.accessFlags) }) return false
    val implementation = method.implementation ?: return false
    if (implementation.registerCount != 12 || implementation.tryBlocks.isNotEmpty()) return false
    val code = implementation.instructions.toList()
    if (code.size != 40) return false
    val first = code.first()
    val firstField = settingsField(first)
    if (first.opcode != Opcode.IGET_OBJECT || (first as? TwoRegisterInstruction)?.registerA != 4 ||
        (first as? TwoRegisterInstruction)?.registerB != 11 || firstField?.definingClass != MENU_ACTION ||
        firstField.name != "A01" || firstField.type != MENU_CONTROLLER) return false
    val strings = code.mapNotNull { ((it as? ReferenceInstruction)?.reference as? StringReference)?.string }.toSet()
    if (!strings.containsAll(setOf("settings", "IG_FB_RIGHT_BEFORE_LOGOUT_SSO_UPSELL"))) return false
    // This exact field read already occurs in the original action, so visibility and the
    // Activity upcast are native contracts rather than guessed private reflection.
    val activityLoad = code[24]
    val field = settingsField(activityLoad)
    if (activityLoad.opcode != Opcode.IGET_OBJECT || (activityLoad as? TwoRegisterInstruction)?.registerA != 0 ||
        (activityLoad as? TwoRegisterInstruction)?.registerB != 4 || field?.definingClass != MENU_CONTROLLER ||
        field.name != "A01" || field.type != ACTIVITY) return false
    return code.any { instruction ->
        val reference = (instruction as? ReferenceInstruction)?.reference as? MethodReference
        instruction.opcode == Opcode.INVOKE_STATIC && reference?.definingClass == NATIVE_SETTINGS &&
            reference.name == "A01" && reference.parameterTypes.map { it.toString() } ==
            listOf("LX/0BAw;", "Ljava/lang/String;") && reference.returnType == NATIVE_SETTINGS
    } && code.any { instruction ->
        val reference = settingsField(instruction)
        instruction.opcode == Opcode.SGET_OBJECT && reference?.definingClass == "LX/01oB;" &&
            reference.name == "A0B" && reference.type == "LX/01oB;"
    } && code.last().opcode == Opcode.RETURN_VOID
}

internal fun installSettingsActionReplacement(method: MutableMethod, owner: ClassDef) {
    check(isSettingsActionCandidate(method, owner)) { "Unsupported native Settings action; no APK produced." }
    // v0 is an existing local; p0 remains v11. The original action is retained for an
    // exact source audit, but cannot launch the platform settings or its upsell.
    method.addInstructions(0, """
        iget-object v0, v11, $MENU_ACTION->A01:$MENU_CONTROLLER
        iget-object v0, v0, $MENU_CONTROLLER->A01:$ACTIVITY
        invoke-static { v0 }, Ldev/friendsonly/patch/runtime/FocusHooks;->openSettings(Landroid/app/Activity;)V
        return-void
    """.trimIndent())
}

private const val PROFILE_MENU_ACTION = "LX/0AU6;"
private const val PROFILE = "Lcom/instagram/profile/fragment/UserDetailFragment;"

/** The shared profile overflow handler has a native self-profile branch. Preserve its other-profile path. */
internal fun isOwnProfileMenuCandidate(method: Method, owner: ClassDef): Boolean {
    if (owner.type != PROFILE_MENU_ACTION || method.definingClass != owner.type || method.name != "A02" ||
        method.parameterTypes.map { it.toString() } != listOf("Landroid/view/View;") || method.returnType != "V" ||
        !AccessFlags.PUBLIC.isSet(method.accessFlags) || !AccessFlags.FINAL.isSet(method.accessFlags) ||
        listOf(AccessFlags.STATIC, AccessFlags.SYNCHRONIZED, AccessFlags.DECLARED_SYNCHRONIZED,
            AccessFlags.NATIVE, AccessFlags.ABSTRACT).any { it.isSet(method.accessFlags) }) return false
    val implementation = method.implementation ?: return false
    if (implementation.registerCount != 47 || implementation.tryBlocks.isNotEmpty()) return false
    val code = implementation.instructions.toList()
    if (code.size != 1406) return false
    fun registers(index: Int, opcode: Opcode, a: Int, b: Int): Boolean =
        code[index].opcode == opcode && (code[index] as? TwoRegisterInstruction)?.let { it.registerA == a && it.registerB == b } == true
    fun field(index: Int, definingClass: String, name: String, type: String): Boolean =
        settingsField(code[index])?.let { it.definingClass == definingClass && it.name == name && it.type == type } == true
    if (!registers(0, Opcode.MOVE_OBJECT_FROM16, 0, 45) || !registers(1, Opcode.MOVE_OBJECT_FROM16, 4, 46) ||
        !registers(2, Opcode.IGET_OBJECT, 1, 0) || !field(2, PROFILE_MENU_ACTION, "A07", PROFILE) ||
        !registers(6, Opcode.IGET_BOOLEAN, 2, 1) || !field(6, PROFILE, "A2V", "Z") ||
        !registers(23, Opcode.IGET_BOOLEAN, 7, 1) || !field(23, PROFILE, "A2V", "Z") ||
        !registers(349, Opcode.IGET_OBJECT, 5, 0) || !field(349, PROFILE_MENU_ACTION, "A00", ACTIVITY)) return false
    // This IF_EQZ is the native proof that false means the other person's profile.
    // Both native menu implementations are on its true branch.
    val ownBranch = code[26]
    val branchOffset = (ownBranch as? OffsetInstruction)?.codeOffset ?: return false
    if (ownBranch.opcode != Opcode.IF_EQZ || (ownBranch as? OneRegisterInstruction)?.registerA != 7 ||
        code.take(26).sumOf { it.codeUnits } + branchOffset != code.take(371).sumOf { it.codeUnits }) return false
    val contextRead = (code[3] as? ReferenceInstruction)?.reference as? MethodReference
    if (code[3].opcode != Opcode.INVOKE_VIRTUAL || contextRead?.definingClass != "Landroidx/fragment/app/Fragment;" ||
        contextRead.name != "getContext" || contextRead.returnType != "Landroid/content/Context;" || contextRead.parameterTypes.isNotEmpty() ||
        (code[3] as? FiveRegisterInstruction)?.registerCount != 1 || (code[3] as? FiveRegisterInstruction)?.registerC != 1 ||
        code[4].opcode != Opcode.MOVE_RESULT_OBJECT || (code[4] as? OneRegisterInstruction)?.registerA != 8 ||
        code[5].opcode != Opcode.IF_EQZ || (code[5] as? OneRegisterInstruction)?.registerA != 8) return false
    fun launch(index: Int, definingClass: String, name: String, parameters: List<String>, returns: String): Boolean {
        val reference = (code[index] as? ReferenceInstruction)?.reference as? MethodReference
        return reference?.definingClass == definingClass && reference.name == name &&
            reference.parameterTypes.map { it.toString() } == parameters && reference.returnType == returns
    }
    if (code[75].opcode != Opcode.INVOKE_VIRTUAL || !launch(75, "LX/0FMc;", "A01",
        listOf("Landroid/app/Activity;", "Lcom/instagram/common/session/UserSession;"), "V") ||
        code[363].opcode != Opcode.INVOKE_STATIC || !launch(363, NATIVE_SETTINGS, "A01",
            listOf("LX/0BAw;", "Ljava/lang/String;"), NATIVE_SETTINGS)) return false
    val strings = code.mapNotNull { ((it as? ReferenceInstruction)?.reference as? StringReference)?.string }.toSet()
    return strings.containsAll(setOf("main_settings_screen", "profileActionBarController", "profile_menu",
        "ig_native_meta_verified_profile_menu_impression"))
}

internal fun installOwnProfileMenuReplacement(method: MutableMethod, owner: ClassDef) {
    check(isOwnProfileMenuCandidate(method, owner)) { "Unsupported own-profile menu action; no APK produced." }
    val original = method.instructions[3]
    // v0/v1 already hold this/the native profile; v2 is an existing dead local.
    // The other-profile path rejoins the original getContext. Its first use of
    // v2 is the native IGET_BOOLEAN at index 6, which overwrites our scratch.
    method.addInstructionsWithLabels(3, """
        iget-boolean v2, v1, $PROFILE->A2V:Z
        if-eqz v2, :friends_only_other_profile_options
        iget-object v2, v0, $PROFILE_MENU_ACTION->A00:$ACTIVITY
        invoke-static { v2 }, Ldev/friendsonly/patch/runtime/FocusHooks;->openSettings(Landroid/app/Activity;)V
        return-void
    """.trimIndent(), ExternalLabel("friends_only_other_profile_options", original))
}

private object OwnProfileMenuAction : Fingerprint(custom = ::isOwnProfileMenuCandidate)

private object ProfileSettingsAction : Fingerprint(custom = ::isSettingsActionCandidate)

@Suppress("unused")
val settingsRoutes = bytecodePatch(
    name = "Friends Only local settings",
    description = "Opens local Friends Only settings from the own-profile menu and Settings action while preserving other-profile options and authentication routes.",
    default = true,
) {
    compatibleWith(INSTAGRAM)
    extendWith("extensions/extension.mpe")
    execute {
        val matches = ProfileSettingsAction.matchAll()
        check(matches.size == 1) { "Unsupported native Settings action: expected one, found ${matches.size}. No APK produced." }
        val menuMatches = OwnProfileMenuAction.matchAll()
        check(menuMatches.size == 1) { "Unsupported own-profile menu action: expected one, found ${menuMatches.size}. No APK produced." }
        installSettingsActionReplacement(matches.single().method, classDefBy(SETTINGS_ACTION))
        installOwnProfileMenuReplacement(menuMatches.single().method, classDefBy(PROFILE_MENU_ACTION))
    }
}
