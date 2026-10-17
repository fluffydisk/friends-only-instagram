/* SPDX-License-Identifier: GPL-3.0-only */
package dev.friendsonly.patches

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
import com.android.tools.smali.dexlib2.immutable.ImmutableField
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodImplementation
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction10x
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction22c
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction31c
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableFieldReference
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableStringReference

/** Host checks for fail-closed fingerprinting and the actual emitted control-flow guard. */
object FeedVideoTapPolicyTest {
    private const val OWNER = "LX/05b6;"
    private const val CONFIG = "Lcom/instagram/clips/intf/ClipsViewerConfig;"
    private const val SOURCE = "Lcom/instagram/clips/intf/ClipsViewerSource;"
    private const val MARKER = "android_purge_26_q2_ClipsPluginImpl_launchClipsViewer_4"
    private val parameters = listOf(
        "Landroid/view/View;", "Landroidx/fragment/app/FragmentActivity;", CONFIG,
        "Lcom/instagram/common/session/UserSession;", "Z", "Z", "Z",
    )

    private fun body(
        firstRegister: Int = 0,
        marker: String = MARKER,
        secondMarker: String = "launchClipsViewer",
        configRegister: Int = 12,
        fieldName: String = "A00",
        fieldType: String = SOURCE,
        fieldOwner: String = CONFIG,
    ): List<Instruction> = listOf(
        ImmutableInstruction31c(Opcode.CONST_STRING_JUMBO, firstRegister, ImmutableStringReference(marker)),
        ImmutableInstruction31c(Opcode.CONST_STRING_JUMBO, 0, ImmutableStringReference(secondMarker)),
        ImmutableInstruction22c(Opcode.IGET_OBJECT, 2, configRegister,
            ImmutableFieldReference(fieldOwner, fieldName, fieldType)),
        ImmutableInstruction10x(Opcode.RETURN_VOID),
    )

    private fun method(
        owner: String = OWNER,
        types: List<String> = parameters,
        returns: String = "V",
        flags: Int = AccessFlags.PUBLIC.value or AccessFlags.STATIC.value or AccessFlags.FINAL.value,
        registers: Int = 17,
        code: List<Instruction>? = body(),
    ) = ImmutableMethod(owner, "A0C", types.map { ImmutableMethodParameter(it, emptySet(), null) }, returns,
        flags, emptySet(), emptySet(), code?.let { ImmutableMethodImplementation(registers, it, emptyList(), emptyList()) })

    private fun owner(type: String = OWNER) = ImmutableClassDef(type, AccessFlags.PUBLIC.value,
        "Ljava/lang/Object;", emptyList(), null, emptyList(), emptyList(), emptyList())

    private fun config(
        type: String = CONFIG,
        fieldFlags: Int = AccessFlags.PUBLIC.value,
        fieldType: String = SOURCE,
    ): ImmutableClassDef {
        val field = ImmutableField(type, "A00", fieldType, fieldFlags, null, emptySet(), emptySet())
        return ImmutableClassDef(type, AccessFlags.PUBLIC.value, "Ljava/lang/Object;",
            emptyList(), null, emptyList(), listOf(field), emptyList())
    }

    private fun source(type: String = SOURCE, superclass: String = "Ljava/lang/Enum;") =
        ImmutableClassDef(type, AccessFlags.PUBLIC.value or AccessFlags.ENUM.value, superclass,
            emptyList(), null, emptyList(), emptyList(), emptyList())

    @JvmStatic
    fun main(args: Array<String>) {
        var assertions = 0
        fun verify(value: Boolean, message: String) {
            check(value) { message }
            assertions++
        }
        fun rejects(candidate: ImmutableMethod, message: String) =
            verify(!isFeedViewerLaunchCandidate(candidate, owner()), message)
        verify(isFeedViewerLaunchCandidate(method(), owner()), "Supported viewer launcher must qualify")
        verify(!isFeedViewerLaunchCandidate(method(owner = "Lother/Launcher;"), owner("Lother/Launcher;")), "Unrelated owners must fail closed")
        rejects(method(owner = "LX/Other;"), "Mismatched method owner must fail closed")
        rejects(method(types = parameters.dropLast(1)), "Different parameter count must fail closed")
        rejects(method(types = parameters.map { if (it == CONFIG) "Ljava/lang/Object;" else it }), "Different config type must fail closed")
        rejects(method(returns = "Z"), "Non-void launcher must fail closed")
        rejects(method(flags = AccessFlags.PUBLIC.value), "Instance method must fail closed")
        for (flag in listOf(AccessFlags.SYNCHRONIZED, AccessFlags.DECLARED_SYNCHRONIZED, AccessFlags.NATIVE, AccessFlags.ABSTRACT)) {
            rejects(method(flags = AccessFlags.STATIC.value or flag.value), "Unsafe access flag $flag must fail closed")
        }
        rejects(method(code = null), "Absent implementation must fail closed")
        rejects(method(code = emptyList()), "Empty implementation must fail closed")
        rejects(method(registers = 7), "v0 must be a local, never a parameter")
        rejects(method(registers = 21), "Config register outside iget encoding must fail closed")
        rejects(method(code = body(firstRegister = 1)), "Original entry must overwrite scratch v0")
        rejects(method(code = body(marker = "unrelated_marker")), "First pinned marker is required")
        rejects(method(code = body(secondMarker = "other_launch")), "Second launch marker is required")
        rejects(method(code = body(configRegister = 11)), "Field read must use the config parameter")
        rejects(method(code = body(fieldName = "A01")), "Different field name must fail closed")
        rejects(method(code = body(fieldType = "Ljava/lang/String;")), "Different source type must fail closed")
        rejects(method(code = body(fieldOwner = "Lother/Config;")), "Different field owner must fail closed")
        verify(hasFeedViewerSourceContract(config(), source()), "Pinned public source field and Enum upcast must qualify")
        verify(!hasFeedViewerSourceContract(config(type = "Lother/Config;"), source()), "Wrong config class must fail closed")
        verify(!hasFeedViewerSourceContract(config(), source(type = "Lother/Source;")), "Wrong source class must fail closed")
        verify(!hasFeedViewerSourceContract(config(), source(superclass = "Ljava/lang/Object;")), "Hook argument must be Enum-compatible")
        verify(!hasFeedViewerSourceContract(config(fieldFlags = AccessFlags.PRIVATE.value), source()), "Private field cannot be injected from launcher")
        verify(!hasFeedViewerSourceContract(config(fieldFlags = AccessFlags.PUBLIC.value or AccessFlags.STATIC.value), source()), "Source field must be an instance field")
        verify(!hasFeedViewerSourceContract(config(fieldType = "Ljava/lang/String;"), source()), "Source field type must match")

        val mutable = MutableMethod(method())
        val original = mutable.implementation!!.instructions.toList()
        val originalRegisterCount = mutable.implementation!!.registerCount
        installFeedViewerGuard(mutable)
        val code = mutable.implementation!!.instructions
        verify(code.take(6).map { it.opcode } == listOf(Opcode.IF_EQZ, Opcode.IGET_OBJECT, Opcode.INVOKE_STATIC,
            Opcode.MOVE_RESULT, Opcode.IF_EQZ, Opcode.RETURN_VOID), "Guard must branch before touching unrelated navigation")
        verify(code.drop(6).zip(original).all { (patched, old) -> patched === old } && code.size == original.size + 6,
            "Every original instruction must remain in order")
        verify(mutable.implementation!!.registerCount == originalRegisterCount, "Guard must not shift parameter registers")
        verify((code[0] as OneRegisterInstruction).registerA == 12, "Null check must use config p2")
        verify((code[0] as BuilderOffsetInstruction).target.location.instruction === original.first(), "Null config must rejoin original entry")
        verify((code[4] as BuilderOffsetInstruction).target.location.instruction === original.first(), "Allowed source must rejoin original entry")
        verify((code[1] as TwoRegisterInstruction).registerA == 0 && (code[1] as TwoRegisterInstruction).registerB == 12,
            "Source read must use scratch v0 and config p2")
        val field = (code[1] as ReferenceInstruction).reference as FieldReference
        verify(field.definingClass == CONFIG && field.name == "A00" && field.type == SOURCE, "Guard must read only pinned source field")
        val hook = (code[2] as ReferenceInstruction).reference as MethodReference
        verify(hook.definingClass == "Ldev/friendsonly/patch/runtime/FocusHooks;" && hook.name == "shouldBlockFeedReelsOpen" &&
            hook.parameterTypes.map { it.toString() } == listOf("Ljava/lang/Enum;") && hook.returnType == "Z",
            "Guard must call only the narrowly scoped source policy")
        verify((code[3] as OneRegisterInstruction).registerA == 0 && (code[4] as OneRegisterInstruction).registerA == 0,
            "Policy result must control the early return")
        verify(!isFeedViewerLaunchCandidate(mutable, owner()), "Already patched methods cannot match a second time")
        verify(runCatching { installFeedViewerGuard(mutable) }.isFailure, "Repeated injection must fail closed")
        verify(runCatching { installFeedViewerGuard(MutableMethod(method(registers = 21))) }.isFailure, "Unsafe registers must also reject direct injection")
        println("Feed video tap patch checks: $assertions assertions passed")
    }
}
