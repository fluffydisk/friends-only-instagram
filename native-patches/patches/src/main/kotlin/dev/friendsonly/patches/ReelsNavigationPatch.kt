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
import com.android.tools.smali.dexlib2.iface.instruction.*
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference

internal const val MAIN_TAB = "LX/02yu;"
private const val CONTROLLER = "LX/00PT;"
private const val TAB_FACTORY = "LX/00PU;"
private const val NAVIGATION = "LX/00QJ;"
private const val SESSION = "Lcom/instagram/common/session/UserSession;"
private const val LIST = "Ljava/util/List;"
private val CONTROLLER_PARAMETERS = listOf(
    "Landroid/content/Intent;", "Landroid/os/Bundle;", "Landroidx/fragment/app/FragmentActivity;",
    SESSION, "LX/0Ixm;", "LX/00GT;", "LX/0Fzu;",
)
private val NAVIGATION_PARAMETERS = listOf(
    "Landroid/content/Intent;", "Landroid/os/Bundle;", "Landroidx/fragment/app/FragmentActivity;",
    SESSION, CONTROLLER, "LX/00QI;", LIST,
)
private val SAFE_TABS = listOf("A0C" to "FEED", "A0A" to "DIRECT", "A0G" to "PROFILE")
private fun Instruction.field() = (this as? ReferenceInstruction)?.reference as? FieldReference
private fun Instruction.methodRef() = (this as? ReferenceInstruction)?.reference as? MethodReference
private fun Method.params() = parameterTypes.map { it.toString() }
private fun Instruction.registers(): List<Int> = when (this) {
    is FiveRegisterInstruction -> listOf(registerC, registerD, registerE, registerF, registerG).take(registerCount)
    is RegisterRangeInstruction -> (startRegister until startRegister + registerCount).toList()
    is ThreeRegisterInstruction -> listOf(registerA, registerB, registerC)
    is TwoRegisterInstruction -> listOf(registerA, registerB)
    is OneRegisterInstruction -> if (opcode in setOf(Opcode.CONST_WIDE, Opcode.CONST_WIDE_16,
        Opcode.CONST_WIDE_32, Opcode.CONST_WIDE_HIGH16)) listOf(registerA, registerA + 1) else listOf(registerA)
    else -> emptyList()
}
private fun Method.isConcrete() = implementation != null &&
    listOf(AccessFlags.NATIVE, AccessFlags.ABSTRACT, AccessFlags.SYNCHRONIZED, AccessFlags.DECLARED_SYNCHRONIZED)
        .none { it.isSet(accessFlags) }
private fun Instruction.isListStore() = opcode == Opcode.IPUT_OBJECT &&
    field()?.let { it.definingClass == CONTROLLER && it.name == "A07" && it.type == LIST } == true

/** Three native destinations. The field/name mapping is proved from enum initialization. */
internal fun hasFriendsTabContract(type: ClassDef): Boolean {
    if (type.type != MAIN_TAB || type.superclass != "Ljava/lang/Enum;") return false
    val code = type.methods.singleOrNull { it.name == "<clinit>" }?.implementation?.instructions?.toList() ?: return false
    return SAFE_TABS.all { (fieldName, enumName) ->
        type.fields.count { it.name == fieldName && it.type == MAIN_TAB &&
            AccessFlags.PUBLIC.isSet(it.accessFlags) && AccessFlags.STATIC.isSet(it.accessFlags) } == 1 &&
            code.withIndex().any { (index, instruction) ->
                instruction.opcode == Opcode.SPUT_OBJECT && instruction.field()?.let {
                    it.definingClass == MAIN_TAB && it.name == fieldName && it.type == MAIN_TAB
                } == true && code.subList(maxOf(0, index - 6), index).any { before ->
                    ((before as? ReferenceInstruction)?.reference as? StringReference)?.string == enumName
                }
            }
    }
}

/** A07 is the shared list consumed by both bottom-tab creation and the pager/selection binder. */
internal fun isFriendsTabsConstructor(method: Method, owner: ClassDef): Boolean {
    if (owner.type != CONTROLLER || method.definingClass != CONTROLLER || method.name != "<init>" ||
        method.params() != CONTROLLER_PARAMETERS || method.returnType != "V" ||
        AccessFlags.STATIC.isSet(method.accessFlags) || !method.isConcrete()) return false
    val impl = method.implementation ?: return false
    if (impl.registerCount != 21 || impl.tryBlocks.isNotEmpty()) return false
    val code = impl.instructions.toList()
    val stores = code.withIndex().filter { it.value.isListStore() }
    if (stores.size != 1) return false
    val (index, store) = stores.single()
    if (index < 2 || (store as? TwoRegisterInstruction)?.let { it.registerA == 0 && it.registerB == 13 } != true) return false
    val producer = code[index - 2].methodRef()
    if (code[index - 2].opcode != Opcode.INVOKE_VIRTUAL || producer?.definingClass != TAB_FACTORY ||
        producer.name != "A03" || producer.parameterTypes.map { it.toString() } != listOf(SESSION, "Z") ||
        producer.returnType != LIST || code[index - 1].opcode != Opcode.MOVE_RESULT_OBJECT ||
        (code[index - 1] as? OneRegisterInstruction)?.registerA != 0) return false
    // v1/v2 are dead between this store and their next explicit constants; p0 is v13.
    for (register in listOf(1, 2)) {
        val next = code.drop(index + 1).firstOrNull { register in it.registers() } ?: return false
        val constants = setOf(Opcode.CONST_4, Opcode.CONST_16, Opcode.CONST_WIDE, Opcode.CONST_WIDE_16,
            Opcode.CONST_WIDE_32, Opcode.CONST_WIDE_HIGH16)
        if (next.opcode !in constants || next !is OneRegisterInstruction || register !in next.registers()) return false
    }
    // Same list is passed to the native NavigationController, not a separate page index table.
    return code.any { instruction -> instruction.methodRef()?.let { ref ->
        instruction.opcode == Opcode.INVOKE_DIRECT_RANGE && ref.definingClass == NAVIGATION &&
            ref.name == "<init>" && ref.parameterTypes.map { it.toString() } == NAVIGATION_PARAMETERS
    } == true }
}

/** This factory also runs during A0U's live tab refresh, not just initial construction. */
internal fun isFriendsTabsFactory(method: Method, owner: ClassDef): Boolean {
    if (owner.type != TAB_FACTORY || method.definingClass != TAB_FACTORY || method.name != "A03" ||
        method.params() != listOf(SESSION, "Z") || method.returnType != LIST ||
        AccessFlags.STATIC.isSet(method.accessFlags) || !method.isConcrete()) return false
    val impl = method.implementation ?: return false
    val code = impl.instructions.toList()
    return impl.registerCount == 7 && impl.tryBlocks.isEmpty() &&
        code.firstOrNull()?.opcode == Opcode.IF_EQZ && (code.firstOrNull() as? OneRegisterInstruction)?.registerA == 6 &&
        code.count { it.opcode == Opcode.RETURN_OBJECT } == 1 &&
        code.any { it.methodRef()?.let { ref -> ref.definingClass == TAB_FACTORY && ref.name == "A00" &&
            ref.parameterTypes.map { it.toString() } == listOf(SESSION) && ref.returnType == LIST } == true } &&
        code.any { it.methodRef()?.let { ref -> ref.definingClass == "LX/08yk;" && ref.name == "A05" &&
            ref.parameterTypes.map { it.toString() } == listOf(SESSION, "I") && ref.returnType == "Ljava/lang/String;" } == true }
}

internal fun installFriendsTabs(method: MutableMethod) {
    // All scratch registers are locals (p0 is v4). This runs before defaults,
    // experiments and refresh paths can create a divergent pager/tab order.
    method.addInstructions(0, """
        sget-object v0, $MAIN_TAB->A0C:$MAIN_TAB
        sget-object v1, $MAIN_TAB->A0A:$MAIN_TAB
        sget-object v2, $MAIN_TAB->A0G:$MAIN_TAB
        filled-new-array { v0, v1, v2 }, [$MAIN_TAB
        move-result-object v0
        invoke-static { v0 }, Ljava/util/Arrays;->asList([Ljava/lang/Object;)Ljava/util/List;
        move-result-object v0
        return-object v0
    """.trimIndent())
}

internal fun isFriendsDefaultTab(method: Method, owner: ClassDef): Boolean =
    owner.type == TAB_FACTORY && method.definingClass == TAB_FACTORY && method.name == "A01" &&
        method.params() == listOf(SESSION) && method.returnType == MAIN_TAB && !AccessFlags.STATIC.isSet(method.accessFlags) &&
        method.isConcrete() && method.implementation?.let { impl ->
            impl.registerCount == 3 && impl.tryBlocks.isEmpty() && impl.instructions.any {
                it.methodRef()?.let { ref -> ref.definingClass == "LX/02qm;" && ref.name == "A05" &&
                    ref.parameterTypes.map { value -> value.toString() } == listOf(SESSION) && ref.returnType == "Z" } == true
            } && impl.instructions.any { it.field()?.let { field -> field.definingClass == MAIN_TAB && field.name == "A08" } == true }
        } == true

internal fun installFriendsDefaultTab(method: MutableMethod) {
    method.addInstructions(0, "sget-object v0, $MAIN_TAB->A0C:$MAIN_TAB\nreturn-object v0")
}

/** Target is normalized before the native navigation or pager can resolve it. */
internal fun isFriendsSelectionEntry(method: Method, owner: ClassDef): Boolean {
    if (!method.isConcrete() || method.returnType != "V" || method.definingClass != owner.type) return false
    val impl = method.implementation ?: return false
    val code = impl.instructions.toList()
    val isController = owner.type == CONTROLLER && method.name == "A05" && AccessFlags.STATIC.isSet(method.accessFlags) &&
        method.params() == listOf(CONTROLLER, MAIN_TAB, "Ljava/lang/String;", "Z", "Z") &&
        impl.registerCount == 15 && impl.tryBlocks.isEmpty() && code.firstOrNull()?.opcode == Opcode.MOVE_OBJECT &&
        (code.firstOrNull() as? TwoRegisterInstruction)?.let { it.registerA == 2 && it.registerB == 10 } == true &&
        code.any { ((it as? ReferenceInstruction)?.reference as? StringReference)?.string == "MainTabController" }
    val isNavigation = owner.type == NAVIGATION && method.name == "A0B" && !AccessFlags.STATIC.isSet(method.accessFlags) &&
        method.params() == listOf(MAIN_TAB, MAIN_TAB, "Z", "Z", "Z") && impl.registerCount == 14 &&
        code.firstOrNull()?.opcode == Opcode.CONST_4 && (code.firstOrNull() as? OneRegisterInstruction)?.registerA == 0 &&
        code.any { ((it as? ReferenceInstruction)?.reference as? StringReference)?.string == "NavigationController.pushTabFragment" }
    return (isController || isNavigation) && code.any { instruction ->
        instruction.field()?.let { it.definingClass == MAIN_TAB && it.name == "A0A" && it.type == MAIN_TAB } == true
    }
}

internal fun installFriendsSelectionGuard(method: MutableMethod) {
    val target = if (AccessFlags.STATIC.isSet(method.accessFlags)) 11 else 10
    val first = method.instructions.first()
    method.addInstructionsWithLabels(0, """
        sget-object v0, $MAIN_TAB->A0C:$MAIN_TAB
        if-eq v$target, v0, :friends_only_allowed_tab
        sget-object v0, $MAIN_TAB->A0A:$MAIN_TAB
        if-eq v$target, v0, :friends_only_allowed_tab
        sget-object v0, $MAIN_TAB->A0G:$MAIN_TAB
        if-eq v$target, v0, :friends_only_allowed_tab
        sget-object v$target, $MAIN_TAB->A0C:$MAIN_TAB
    """.trimIndent(), ExternalLabel("friends_only_allowed_tab", first))
}

private object FriendsTabsConstructor : Fingerprint(custom = ::isFriendsTabsConstructor)
private object FriendsTabsFactory : Fingerprint(custom = ::isFriendsTabsFactory)
private object FriendsDefaultTab : Fingerprint(custom = ::isFriendsDefaultTab)
private object FriendsSelection : Fingerprint(custom = ::isFriendsSelectionEntry)

@Suppress("unused")
val reelsNavigation = bytecodePatch(
    name = "Friends Only Reels navigation",
    description = "Removes Reels, Explore and creation pages from the shared native tab/pager list; permits only Friends, Chats and Me.",
    default = true,
) {
    compatibleWith(INSTAGRAM)
    execute {
        check(hasFriendsTabContract(classDefBy(MAIN_TAB))) { "Unsupported native tab enum; no APK produced." }
        val constructors = FriendsTabsConstructor.matchAll()
        val factories = FriendsTabsFactory.matchAll()
        val defaults = FriendsDefaultTab.matchAll()
        val selections = FriendsSelection.matchAll()
        check(constructors.size == 1 && factories.size == 1 && defaults.size == 1 && selections.size == 2) {
            "Unsupported main navigation contract: constructors=${constructors.size}, factories=${factories.size}, defaults=${defaults.size}, selections=${selections.size}. No APK produced."
        }
        installFriendsTabs(factories.single().method)
        installFriendsDefaultTab(defaults.single().method)
        selections.forEach { installFriendsSelectionGuard(it.method) }
    }
}
