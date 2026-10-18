/* SPDX-License-Identifier: GPL-3.0-only */
package dev.friendsonly.patches

import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.BuilderOffsetInstruction
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.immutable.ImmutableClassDef
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodImplementation
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction10x
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction21t
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction22x
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction21c
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction22c
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction35c
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableFieldReference
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableMethodReference
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableStringReference

object SettingsRoutesPolicyTest {
    private const val OWNER = "LX/07Z1;"
    private const val BASE = "LX/057U;"
    private const val CONTROLLER = "LX/0AU0;"
    private const val ACTIVITY = "Landroidx/fragment/app/FragmentActivity;"
    private const val NATIVE = "LX/0HCm;"
    private fun owner(type: String = OWNER, superclass: String = BASE) =
        ImmutableClassDef(type, AccessFlags.PUBLIC.value, superclass, emptyList(), null, emptyList(), emptyList(), emptyList())
    private fun body(): MutableList<Instruction> = MutableList<Instruction>(40) {
        ImmutableInstruction10x(Opcode.NOP)
    }.apply {
        this[0] = ImmutableInstruction22c(Opcode.IGET_OBJECT, 4, 11, ImmutableFieldReference(BASE, "A01", CONTROLLER))
        this[15] = ImmutableInstruction21c(Opcode.CONST_STRING, 8, ImmutableStringReference("IG_FB_RIGHT_BEFORE_LOGOUT_SSO_UPSELL"))
        this[19] = ImmutableInstruction21c(Opcode.CONST_STRING, 1, ImmutableStringReference("settings"))
        this[24] = ImmutableInstruction22c(Opcode.IGET_OBJECT, 0, 4, ImmutableFieldReference(CONTROLLER, "A01", ACTIVITY))
        this[30] = ImmutableInstruction21c(Opcode.SGET_OBJECT, 0, ImmutableFieldReference("LX/01oB;", "A0B", "LX/01oB;"))
        this[31] = ImmutableInstruction35c(Opcode.INVOKE_STATIC, 2, 0, 2, 0, 0, 0,
            ImmutableMethodReference(NATIVE, "A01", listOf("LX/0BAw;", "Ljava/lang/String;"), NATIVE))
        this[39] = ImmutableInstruction10x(Opcode.RETURN_VOID)
    }
    private fun method(type: String = OWNER, name: String = "A02", registers: Int = 12,
        flags: Int = AccessFlags.PUBLIC.value or AccessFlags.FINAL.value, parameters: List<String> = emptyList(),
        returns: String = "V", code: List<Instruction>? = body()) =
        ImmutableMethod(type, name, parameters.map { ImmutableMethodParameter(it, emptySet(), null) }, returns,
            flags, emptySet(), emptySet(), code?.let { ImmutableMethodImplementation(registers, it, emptyList(), emptyList()) })
    private const val MENU = "LX/0AU6;"
    private const val PROFILE = "Lcom/instagram/profile/fragment/UserDetailFragment;"
    private fun menuOwner(type: String = MENU) = owner(type, "Ljava/lang/Object;")
    private fun menuBody(): MutableList<Instruction> = MutableList<Instruction>(1406) {
        ImmutableInstruction10x(Opcode.NOP)
    }.apply {
        this[0] = ImmutableInstruction22x(Opcode.MOVE_OBJECT_FROM16, 0, 45)
        this[1] = ImmutableInstruction22x(Opcode.MOVE_OBJECT_FROM16, 4, 46)
        this[2] = ImmutableInstruction22c(Opcode.IGET_OBJECT, 1, 0, ImmutableFieldReference(MENU, "A07", PROFILE))
        this[3] = ImmutableInstruction35c(Opcode.INVOKE_VIRTUAL, 1, 1, 0, 0, 0, 0,
            ImmutableMethodReference("Landroidx/fragment/app/Fragment;", "getContext", emptyList(), "Landroid/content/Context;"))
        this[4] = com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction11x(Opcode.MOVE_RESULT_OBJECT, 8)
        this[5] = ImmutableInstruction21t(Opcode.IF_EQZ, 8, 2)
        this[6] = ImmutableInstruction22c(Opcode.IGET_BOOLEAN, 2, 1, ImmutableFieldReference(PROFILE, "A2V", "Z"))
        this[23] = ImmutableInstruction22c(Opcode.IGET_BOOLEAN, 7, 1, ImmutableFieldReference(PROFILE, "A2V", "Z"))
        this[26] = ImmutableInstruction21t(Opcode.IF_EQZ, 7, 1)
        this[75] = ImmutableInstruction35c(Opcode.INVOKE_VIRTUAL, 3, 5, 6, 4, 0, 0,
            ImmutableMethodReference("LX/0FMc;", "A01", listOf("Landroid/app/Activity;", "Lcom/instagram/common/session/UserSession;"), "V"))
        this[231] = ImmutableInstruction21c(Opcode.CONST_STRING, 11, ImmutableStringReference("profile_menu"))
        this[322] = ImmutableInstruction21c(Opcode.CONST_STRING, 1, ImmutableStringReference("ig_native_meta_verified_profile_menu_impression"))
        this[349] = ImmutableInstruction22c(Opcode.IGET_OBJECT, 5, 0, ImmutableFieldReference(MENU, "A00", ACTIVITY))
        this[355] = ImmutableInstruction21c(Opcode.CONST_STRING, 3, ImmutableStringReference("profileActionBarController"))
        this[357] = ImmutableInstruction21c(Opcode.CONST_STRING, 0, ImmutableStringReference("main_settings_screen"))
        this[363] = ImmutableInstruction35c(Opcode.INVOKE_STATIC, 2, 1, 0, 0, 0, 0,
            ImmutableMethodReference(NATIVE, "A01", listOf("LX/0BAw;", "Ljava/lang/String;"), NATIVE))
        this[1405] = ImmutableInstruction10x(Opcode.RETURN_VOID)
        this[26] = ImmutableInstruction21t(Opcode.IF_EQZ, 7, take(371).sumOf { it.codeUnits } - take(26).sumOf { it.codeUnits })
    }
    private fun menuMethod(registers: Int = 47, code: List<Instruction>? = menuBody(), parameters: List<String> = listOf("Landroid/view/View;"),
        type: String = MENU, flags: Int = AccessFlags.PUBLIC.value or AccessFlags.FINAL.value) = method(type = type,
            name = "A02", registers = registers, flags = flags, parameters = parameters, code = code)

    @JvmStatic fun main(args: Array<String>) {
        var assertions = 0
        fun verify(value: Boolean, message: String) { check(value) { message }; assertions++ }
        fun rejects(candidate: ImmutableMethod, message: String) = verify(!isSettingsActionCandidate(candidate, owner()), message)
        verify(isSettingsActionCandidate(method(), owner()), "Pinned concrete Settings action must match")
        verify(!isSettingsActionCandidate(method(), owner(superclass = "Lother/Menu;")), "Different hierarchy must reject")
        verify(!isSettingsActionCandidate(method(type = "LX/Other;"), owner(type = "LX/Other;")), "Other action owners must reject")
        rejects(method(name = "A03"), "Other methods must reject")
        rejects(method(parameters = listOf("Ljava/lang/String;")), "Other signatures must reject")
        rejects(method(returns = "Z"), "Nonvoid action must reject")
        rejects(method(registers = 13), "Parameter shifts must reject")
        rejects(method(registers = 11), "Missing scratch/local contract must reject")
        rejects(method(code = null), "Missing implementation must reject")
        rejects(method(code = body().dropLast(1)), "Different body shape must reject")
        for (flag in listOf(AccessFlags.STATIC, AccessFlags.SYNCHRONIZED, AccessFlags.DECLARED_SYNCHRONIZED,
            AccessFlags.NATIVE, AccessFlags.ABSTRACT)) rejects(method(flags = AccessFlags.PUBLIC.value or AccessFlags.FINAL.value or flag.value), "Unsafe $flag must reject")
        rejects(method(flags = AccessFlags.PRIVATE.value or AccessFlags.FINAL.value), "Private methods must reject")
        rejects(method(flags = AccessFlags.PUBLIC.value), "Nonfinal unrelated methods must reject")
        rejects(method(code = body().apply { this[0] = ImmutableInstruction22c(Opcode.IGET_OBJECT, 4, 10,
            ImmutableFieldReference(BASE, "A01", CONTROLLER)) }), "Wrong receiver register must reject")
        rejects(method(code = body().apply { this[0] = ImmutableInstruction22c(Opcode.IGET_OBJECT, 4, 11,
            ImmutableFieldReference(BASE, "A02", CONTROLLER)) }), "Wrong controller field must reject")
        rejects(method(code = body().apply { this[15] = ImmutableInstruction10x(Opcode.NOP) }), "SSO marker is part of version fingerprint")
        rejects(method(code = body().apply { this[19] = ImmutableInstruction10x(Opcode.NOP) }), "Root Settings marker is required")
        rejects(method(code = body().apply { this[24] = ImmutableInstruction22c(Opcode.IGET_OBJECT, 0, 4,
            ImmutableFieldReference(CONTROLLER, "A01", "Landroid/content/Context;")) }), "Only proven Activity upcast qualifies")
        rejects(method(code = body().apply { this[24] = ImmutableInstruction22c(Opcode.IGET_OBJECT, 1, 4,
            ImmutableFieldReference(CONTROLLER, "A01", ACTIVITY)) }), "Wrong Activity local must reject")
        rejects(method(code = body().apply { this[30] = ImmutableInstruction10x(Opcode.NOP) }), "Native root screen id must qualify")
        rejects(method(code = body().apply { this[31] = ImmutableInstruction10x(Opcode.NOP) }), "Actual native Settings launch is required")
        rejects(method(code = body().apply { this[39] = ImmutableInstruction10x(Opcode.NOP) }), "Native terminal return must qualify")
        val mutable = MutableMethod(method())
        val original = mutable.instructions.toList()
        installSettingsActionReplacement(mutable, owner())
        val code = mutable.instructions
        verify(code.take(4).map { it.opcode } == listOf(Opcode.IGET_OBJECT, Opcode.IGET_OBJECT,
            Opcode.INVOKE_STATIC, Opcode.RETURN_VOID), "Local settings must return before native framework launch")
        verify(code.size == original.size + 4 && code.drop(4).zip(original).all { (actual, old) -> actual === old },
            "Original native instructions remain auditable and unchanged")
        verify(mutable.implementation!!.registerCount == 12, "Parameter registers remain unchanged")
        val first = code[0] as TwoRegisterInstruction
        verify(first.registerA == 0 && first.registerB == 11, "Menu controller load uses v0 local and p0")
        val second = code[1] as TwoRegisterInstruction
        verify(second.registerA == 0 && second.registerB == 0, "Activity read reuses safe local")
        val field = (code[1] as ReferenceInstruction).reference as FieldReference
        verify(field.definingClass == CONTROLLER && field.name == "A01" && field.type == ACTIVITY, "Only verified Activity field is read")
        val hook = (code[2] as ReferenceInstruction).reference as MethodReference
        verify(hook.definingClass == "Ldev/friendsonly/patch/runtime/FocusHooks;" && hook.name == "openSettings" &&
            hook.parameterTypes.map { it.toString() } == listOf("Landroid/app/Activity;") && hook.returnType == "V", "Only local settings hook is invoked")
        verify(!isSettingsActionCandidate(mutable, owner()), "Already-patched action must not match")
        verify(runCatching { installSettingsActionReplacement(mutable, owner()) }.isFailure, "Repeated injection must fail closed")
        fun rejectsMenu(candidate: ImmutableMethod, message: String) = verify(!isOwnProfileMenuCandidate(candidate, menuOwner()), message)
        verify(isOwnProfileMenuCandidate(menuMethod(), menuOwner()), "Pinned own-profile menu handler must match")
        rejectsMenu(menuMethod(type = "LX/Other;"), "Other controller must reject")
        rejectsMenu(menuMethod(registers = 48), "Shifted receiver registers must reject")
        rejectsMenu(menuMethod(parameters = emptyList()), "Non-View actions must reject")
        rejectsMenu(menuMethod(code = null), "Missing overflow implementation must reject")
        rejectsMenu(menuMethod(code = menuBody().dropLast(1)), "Changed native body size must reject")
        rejectsMenu(menuMethod(flags = AccessFlags.PUBLIC.value or AccessFlags.FINAL.value or AccessFlags.STATIC.value), "Static controller action must reject")
        rejectsMenu(menuMethod(code = menuBody().apply { this[0] = ImmutableInstruction22x(Opcode.MOVE_OBJECT_FROM16, 0, 44) }), "Receiver must be native p0")
        rejectsMenu(menuMethod(code = menuBody().apply { this[1] = ImmutableInstruction22x(Opcode.MOVE_OBJECT_FROM16, 4, 45) }), "View parameter contract must qualify")
        rejectsMenu(menuMethod(code = menuBody().apply { this[2] = ImmutableInstruction22c(Opcode.IGET_OBJECT, 1, 0,
            ImmutableFieldReference(MENU, "A08", PROFILE)) }), "Profile field contract must qualify")
        rejectsMenu(menuMethod(code = menuBody().apply { this[3] = ImmutableInstruction35c(Opcode.INVOKE_VIRTUAL, 1, 2, 0, 0, 0, 0,
            ImmutableMethodReference("Landroidx/fragment/app/Fragment;", "getContext", emptyList(), "Landroid/content/Context;")) }), "Scratch v2 cannot be read before native definition")
        rejectsMenu(menuMethod(code = menuBody().apply { this[6] = ImmutableInstruction22c(Opcode.IGET_BOOLEAN, 3, 1,
            ImmutableFieldReference(PROFILE, "A2V", "Z")) }), "Native first scratch definition must qualify")
        rejectsMenu(menuMethod(code = menuBody().apply { this[23] = ImmutableInstruction22c(Opcode.IGET_BOOLEAN, 7, 1,
            ImmutableFieldReference(PROFILE, "A2W", "Z")) }), "Self-profile predicate must match actual native branch")
        rejectsMenu(menuMethod(code = menuBody().apply { this[26] = ImmutableInstruction21t(Opcode.IF_NEZ, 7, 1) }), "Inverted own-profile branch must reject")
        rejectsMenu(menuMethod(code = menuBody().apply { this[26] = ImmutableInstruction21t(Opcode.IF_EQZ, 7, 1) }), "Wrong other-profile destination must reject")
        rejectsMenu(menuMethod(code = menuBody().apply { this[75] = ImmutableInstruction10x(Opcode.NOP) }), "Native Compose menu launch is required")
        rejectsMenu(menuMethod(code = menuBody().apply { this[349] = ImmutableInstruction22c(Opcode.IGET_OBJECT, 5, 0,
            ImmutableFieldReference(MENU, "A00", "Landroid/content/Context;")) }), "Native Activity access must qualify")
        rejectsMenu(menuMethod(code = menuBody().apply { this[357] = ImmutableInstruction10x(Opcode.NOP) }), "Native root Settings marker is required")
        rejectsMenu(menuMethod(code = menuBody().apply { this[363] = ImmutableInstruction10x(Opcode.NOP) }), "Native fallback Settings route is required")
        val menuMutable = MutableMethod(menuMethod())
        val menuOriginal = menuMutable.instructions.toList()
        installOwnProfileMenuReplacement(menuMutable, menuOwner())
        val menuCode = menuMutable.instructions
        verify(menuCode.drop(3).take(5).map { it.opcode } == listOf(Opcode.IGET_BOOLEAN, Opcode.IF_EQZ,
            Opcode.IGET_OBJECT, Opcode.INVOKE_STATIC, Opcode.RETURN_VOID), "Self-profile menu must open own settings and return")
        verify(menuCode.take(3).zip(menuOriginal.take(3)).all { (actual, old) -> actual === old } &&
            menuCode.drop(8).zip(menuOriginal.drop(3)).all { (actual, old) -> actual === old } && menuCode.size == 1411,
            "All 1406 original instructions remain in order")
        verify((menuCode[4] as BuilderOffsetInstruction).target.location.instruction === menuOriginal[3], "Other-profile branch must rejoin original getContext")
        verify(menuMutable.implementation!!.registerCount == 47, "Native receiver and View parameters retain registers")
        verify((menuCode[3] as TwoRegisterInstruction).registerA == 2 && (menuCode[3] as TwoRegisterInstruction).registerB == 1,
            "Self-profile check reads only verified profile flag into dead local")
        val menuField = (menuCode[5] as ReferenceInstruction).reference as FieldReference
        verify(menuField.definingClass == MENU && menuField.name == "A00" && menuField.type == ACTIVITY,
            "Own-profile path reads only native Activity field")
        val menuHook = (menuCode[6] as ReferenceInstruction).reference as MethodReference
        verify(menuHook.definingClass == "Ldev/friendsonly/patch/runtime/FocusHooks;" && menuHook.name == "openSettings" &&
            menuHook.parameterTypes.map { it.toString() } == listOf("Landroid/app/Activity;") && menuHook.returnType == "V", "Menu replacement calls local settings only")
        verify(!isOwnProfileMenuCandidate(menuMutable, menuOwner()), "Already-patched profile action cannot match")
        verify(runCatching { installOwnProfileMenuReplacement(menuMutable, menuOwner()) }.isFailure, "Repeated profile guard must fail closed")
        println("Local Settings route patch checks: $assertions assertions passed")
    }
}
