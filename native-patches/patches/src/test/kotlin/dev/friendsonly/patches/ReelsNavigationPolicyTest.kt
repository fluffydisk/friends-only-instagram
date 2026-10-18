/* SPDX-License-Identifier: GPL-3.0-only */
package dev.friendsonly.patches

import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.DexFileFactory
import com.android.tools.smali.dexlib2.Opcodes
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.BuilderOffsetInstruction
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.*
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.immutable.*
import com.android.tools.smali.dexlib2.immutable.instruction.*
import com.android.tools.smali.dexlib2.immutable.reference.*
import java.io.File

object ReelsNavigationPolicyTest {
    private const val CONTROLLER = "LX/00PT;"
    private const val FACTORY = "LX/00PU;"
    private const val NAVIGATION = "LX/00QJ;"
    private const val SESSION = "Lcom/instagram/common/session/UserSession;"
    private const val LIST = "Ljava/util/List;"
    private val controllerParams = listOf("Landroid/content/Intent;", "Landroid/os/Bundle;", "Landroidx/fragment/app/FragmentActivity;",
        SESSION, "LX/0Ixm;", "LX/00GT;", "LX/0Fzu;")
    private val navParams = listOf("Landroid/content/Intent;", "Landroid/os/Bundle;", "Landroidx/fragment/app/FragmentActivity;",
        SESSION, CONTROLLER, "LX/00QI;", LIST)
    private fun field(name: String) = ImmutableFieldReference(MAIN_TAB, name, MAIN_TAB)
    private fun method(owner: String, name: String, params: List<String>, returns: String, registers: Int,
                       code: List<Instruction>, flags: Int = AccessFlags.PUBLIC.value) =
        ImmutableMethod(owner, name, params.map { ImmutableMethodParameter(it, emptySet(), null) }, returns,
            flags, emptySet(), emptySet(), ImmutableMethodImplementation(registers, code, emptyList(), emptyList()))
    private fun owner(type: String, fields: List<ImmutableField> = emptyList(), methods: List<Method> = emptyList(),
                      parent: String = "Ljava/lang/Object;") =
        ImmutableClassDef(type, AccessFlags.PUBLIC.value, parent, emptyList(), null, emptyList(), fields, methods)
    private val returnVoid = ImmutableInstruction10x(Opcode.RETURN_VOID)
    private fun constructor(storeName: String = "A07", registers: Int = 21) = method(CONTROLLER, "<init>", controllerParams,
        "V", registers, listOf(
            ImmutableInstruction35c(Opcode.INVOKE_VIRTUAL, 3, 1, 9, 0, 0, 0,
                ImmutableMethodReference(FACTORY, "A03", listOf(SESSION, "Z"), LIST)),
            ImmutableInstruction11x(Opcode.MOVE_RESULT_OBJECT, 0),
            ImmutableInstruction22c(Opcode.IPUT_OBJECT, 0, 13, ImmutableFieldReference(CONTROLLER, storeName, LIST)),
            ImmutableInstruction11n(Opcode.CONST_4, 2, 0),
            ImmutableInstruction21s(Opcode.CONST_16, 1, 10),
            ImmutableInstruction3rc(Opcode.INVOKE_DIRECT_RANGE, 5, 8,
                ImmutableMethodReference(NAVIGATION, "<init>", navParams, "V")), returnVoid,
        ))
    private fun defaultTab(registers: Int = 3) = method(FACTORY, "A01", listOf(SESSION), MAIN_TAB, registers, listOf(
        ImmutableInstruction35c(Opcode.INVOKE_STATIC, 1, 2, 0, 0, 0, 0,
            ImmutableMethodReference("LX/02qm;", "A05", listOf(SESSION), "Z")),
        ImmutableInstruction11x(Opcode.MOVE_RESULT, 0),
        ImmutableInstruction21c(Opcode.SGET_OBJECT, 0, field("A08")),
        ImmutableInstruction11x(Opcode.RETURN_OBJECT, 0),
    ))
    private fun tabsFactory(registers: Int = 7) = method(FACTORY, "A03", listOf(SESSION, "Z"), LIST, registers, listOf(
        ImmutableInstruction21t(Opcode.IF_EQZ, 6, 2),
        ImmutableInstruction35c(Opcode.INVOKE_STATIC, 1, 5, 0, 0, 0, 0,
            ImmutableMethodReference(FACTORY, "A00", listOf(SESSION), LIST)),
        ImmutableInstruction11x(Opcode.MOVE_RESULT_OBJECT, 3),
        ImmutableInstruction11x(Opcode.RETURN_OBJECT, 3),
        ImmutableInstruction35c(Opcode.INVOKE_VIRTUAL, 3, 0, 5, 2, 0, 0,
            ImmutableMethodReference("LX/08yk;", "A05", listOf(SESSION, "I"), "Ljava/lang/String;")),
    ))
    private fun selection(navigation: Boolean, markerOverride: String? = null): ImmutableMethod {
        val type = if (navigation) NAVIGATION else CONTROLLER
        return method(type, if (navigation) "A0B" else "A05", if (navigation) listOf(MAIN_TAB, MAIN_TAB, "Z", "Z", "Z")
            else listOf(CONTROLLER, MAIN_TAB, "Ljava/lang/String;", "Z", "Z"), "V", if (navigation) 14 else 15, listOf(
            if (navigation) ImmutableInstruction11n(Opcode.CONST_4, 0, 1) else ImmutableInstruction12x(Opcode.MOVE_OBJECT, 2, 10),
            ImmutableInstruction31c(Opcode.CONST_STRING_JUMBO, 0, ImmutableStringReference(markerOverride ?:
                if (navigation) "NavigationController.pushTabFragment" else "MainTabController")),
            ImmutableInstruction21c(Opcode.SGET_OBJECT, 0, field("A0A")), returnVoid,
        ), AccessFlags.PUBLIC.value or if (navigation) 0 else AccessFlags.STATIC.value)
    }
    private fun tabEnum(wrongDirectName: Boolean = false): ImmutableClassDef {
        val fields = listOf("A0C", "A0A", "A0G").map { ImmutableField(MAIN_TAB, it, MAIN_TAB,
            AccessFlags.PUBLIC.value or AccessFlags.STATIC.value or AccessFlags.FINAL.value, null, emptySet(), emptySet()) }
        val code = listOf("A0C" to "FEED", "A0A" to if (wrongDirectName) "CLIPS" else "DIRECT", "A0G" to "PROFILE").flatMap { (name, value) -> listOf(
            ImmutableInstruction31c(Opcode.CONST_STRING_JUMBO, 1, ImmutableStringReference(value)),
            ImmutableInstruction21c(Opcode.SPUT_OBJECT, 0, field(name)),
        ) } + returnVoid
        return owner(MAIN_TAB, fields, listOf(method(MAIN_TAB, "<clinit>", emptyList(), "V", 2, code, AccessFlags.STATIC.value)),
            "Ljava/lang/Enum;")
    }
    @JvmStatic fun main(args: Array<String>) {
        var checks = 0
        fun verify(value: Boolean, message: String) { check(value) { message }; checks++ }
        verify(hasFriendsTabContract(tabEnum()), "Native field/name mappings must qualify")
        verify(!hasFriendsTabContract(tabEnum(true)), "A field mapped to CLIPS cannot stand in for DIRECT")
        verify(!hasFriendsTabContract(owner(MAIN_TAB)), "Non-enum or missing constants must fail closed")
        verify(isFriendsTabsConstructor(constructor(), owner(CONTROLLER)), "Shared list constructor qualifies")
        verify(!isFriendsTabsConstructor(constructor("A06"), owner(CONTROLLER)), "Wrong list store must fail closed")
        verify(!isFriendsTabsConstructor(constructor(registers = 22), owner(CONTROLLER)), "Changed parameter layout must fail closed")
        verify(!isFriendsTabsConstructor(constructor(), owner("LX/Other;")), "Unrelated owner must fail closed")
        verify(isFriendsTabsFactory(tabsFactory(), owner(FACTORY)), "Factory covers construction and live refresh")
        verify(!isFriendsTabsFactory(tabsFactory(8), owner(FACTORY)), "Changed factory locals fail closed")
        val tabs = MutableMethod(tabsFactory())
        val before = tabs.implementation!!.instructions.toList()
        installFriendsTabs(tabs)
        val code = tabs.implementation!!.instructions
        verify(code.size == before.size + 8, "Eight factory instructions replace every tab list before return")
        verify(code.drop(8).zip(before).all { (a, b) -> a === b }, "Original factory body remains intact for auditing")
        verify(code.take(3).map { ((it as ReferenceInstruction).reference as FieldReference).name } ==
            listOf("A0C", "A0A", "A0G"), "Canonical native order is FEED, DIRECT, PROFILE")
        verify(code[3].opcode == Opcode.FILLED_NEW_ARRAY && (code[3] as FiveRegisterInstruction).registerCount == 3,
            "Pager receives exactly three existing native destinations")
        verify(code[7].opcode == Opcode.RETURN_OBJECT && (code[7] as OneRegisterInstruction).registerA == 0,
            "All construction, refresh and experimental paths return the canonical list")
        verify(tabs.implementation!!.registerCount == 7, "Native parameter registers must stay fixed")
        verify(!isFriendsTabsFactory(tabs, owner(FACTORY)), "Already-mutated factory must not match again")
        verify(isFriendsDefaultTab(defaultTab(), owner(FACTORY)), "Pinned native default qualifies")
        verify(!isFriendsDefaultTab(defaultTab(4), owner(FACTORY)), "Unsafe default registers must fail closed")
        val default = MutableMethod(defaultTab())
        installFriendsDefaultTab(default)
        verify(default.implementation!!.instructions.take(2).map { it.opcode } == listOf(Opcode.SGET_OBJECT, Opcode.RETURN_OBJECT),
            "Default returns FEED before experimental Reels defaults")
        verify(((default.implementation!!.instructions[0] as ReferenceInstruction).reference as FieldReference).name == "A0C",
            "Default is the native FEED constant")
        for (navigation in listOf(false, true)) {
            val original = selection(navigation)
            val type = original.definingClass
            verify(isFriendsSelectionEntry(original, owner(type)), "Pinned selection entry qualifies: $type")
            verify(!isFriendsSelectionEntry(selection(navigation, "other"), owner(type)), "Missing selection marker fails closed")
            val guard = MutableMethod(original)
            val old = guard.implementation!!.instructions.toList()
            installFriendsSelectionGuard(guard)
            val guarded = guard.implementation!!.instructions
            verify(guarded.size == old.size + 7 && guarded.drop(7).zip(old).all { (a, b) -> a === b },
                "Native selection body stays intact")
            verify(listOf(1, 3, 5).all { (guarded[it] as BuilderOffsetInstruction).target.location.instruction === old.first() },
                "All allowed destinations rejoin native entry")
            val target = if (navigation) 10 else 11
            verify((guarded[6] as OneRegisterInstruction).registerA == target &&
                ((guarded[6] as ReferenceInstruction).reference as FieldReference).name == "A0C", "Unknown destinations become FEED")
            verify(guarded.take(7).none { it.opcode == Opcode.INVOKE_STATIC || it.opcode == Opcode.INVOKE_VIRTUAL },
                "Selection guard does not inspect views, touch events or account state")
        }
        if (args.isNotEmpty()) {
            val container = DexFileFactory.loadDexContainer(File(args[0]), Opcodes.forApi(35))
            val classes = container.dexEntryNames.flatMap { container.getEntry(it)!!.dexFile.classes }
            verify(hasFriendsTabContract(classes.single { it.type == MAIN_TAB }), "Actual native enum mapping verified")
            val constructors = classes.flatMap { c -> c.methods.filter { isFriendsTabsConstructor(it, c) } }
            val factories = classes.flatMap { c -> c.methods.filter { isFriendsTabsFactory(it, c) } }
            val defaults = classes.flatMap { c -> c.methods.filter { isFriendsDefaultTab(it, c) } }
            val selections = classes.flatMap { c -> c.methods.filter { isFriendsSelectionEntry(it, c) } }
            verify(constructors.size == 1, "Actual APK has exactly one supported constructor")
            verify(factories.size == 1, "Actual APK has exactly one shared construction and refresh factory")
            verify(defaults.size == 1, "Actual APK has exactly one supported default")
            verify(selections.size == 2, "Actual APK has exactly two supported selections")
            installFriendsTabs(MutableMethod(factories.single()))
            installFriendsDefaultTab(MutableMethod(defaults.single()))
            selections.forEach { installFriendsSelectionGuard(MutableMethod(it)) }
        }
        println("Reels navigation patch checks: $checks assertions passed")
    }
}
