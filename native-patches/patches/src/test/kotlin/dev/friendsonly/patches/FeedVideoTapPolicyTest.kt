/* SPDX-License-Identifier: GPL-3.0-only */
package dev.friendsonly.patches

import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.DexFileFactory
import com.android.tools.smali.dexlib2.Opcodes
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.immutable.ImmutableClassDef
import com.android.tools.smali.dexlib2.immutable.ImmutableField
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodImplementation
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction10x
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction22c
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction11n
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction31c
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableFieldReference
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableStringReference
import java.io.File

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

    private fun modal(direct: Boolean, registers: Int = 14, marker: String? = null): ImmutableMethod {
        val types = if (direct) listOf("Landroidx/fragment/app/FragmentActivity;", CONFIG,
            "Lcom/instagram/common/session/UserSession;") else listOf("Landroidx/fragment/app/Fragment;", CONFIG,
            "Lcom/instagram/common/session/UserSession;", "I", "Z")
        val text = marker ?: if (direct) "android_purge_26_q2_ClipsPluginImpl_launchClipsViewerFromDirect"
            else "android_purge_26_q2_ClipsPluginImpl_launchClipsViewerForResultFromFragment_2"
        val code = mutableListOf<Instruction>()
        if (direct) code += ImmutableInstruction11n(Opcode.CONST_4, 4, 0)
        code += ImmutableInstruction31c(Opcode.CONST_STRING_JUMBO, 0, ImmutableStringReference(text))
        code += ImmutableInstruction31c(Opcode.CONST_STRING_JUMBO, 1, ImmutableStringReference("clips_feed_viewer"))
        if (direct) code += ImmutableInstruction31c(Opcode.CONST_STRING_JUMBO, 0, ImmutableStringReference("launchClipsViewerModal"))
        code += ImmutableInstruction10x(Opcode.RETURN_VOID)
        return ImmutableMethod(OWNER, if (direct) "A0E" else "A0D", types.map { ImmutableMethodParameter(it, emptySet(), null) },
            "V", AccessFlags.PUBLIC.value or AccessFlags.STATIC.value, emptySet(), emptySet(),
            ImmutableMethodImplementation(registers, code, emptyList(), emptyList()))
    }

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
        verify(code.first().opcode == Opcode.RETURN_VOID, "Every source must return before entering the Reels viewer")
        verify(code.drop(1).zip(original).all { (patched, old) -> patched === old } && code.size == original.size + 1,
            "Every original instruction must remain in order")
        verify(mutable.implementation!!.registerCount == originalRegisterCount, "Guard must not shift parameter registers")
        verify(code.take(1).none { it is ReferenceInstruction }, "Permanent guard must not read config, preferences or account data")
        verify(!isFeedViewerLaunchCandidate(mutable, owner()), "Already patched methods cannot match a second time")
        verify(runCatching { installFeedViewerGuard(mutable) }.isFailure, "Repeated injection must fail closed")
        verify(runCatching { installFeedViewerGuard(MutableMethod(method(registers = 21))) }.isFailure, "Unsafe registers must also reject direct injection")
        for (direct in listOf(false, true)) {
            verify(isModalReelsLaunchCandidate(modal(direct), owner()), "Pinned modal route qualifies")
            verify(!isModalReelsLaunchCandidate(modal(direct, registers = 15), owner()), "Changed modal registers fail closed")
            verify(!isModalReelsLaunchCandidate(modal(direct, marker = "other"), owner()), "Modal launch marker is required")
            val guarded = MutableMethod(modal(direct))
            val before = guarded.implementation!!.instructions.toList()
            installModalReelsGuard(guarded)
            verify(guarded.implementation!!.instructions.first().opcode == Opcode.RETURN_VOID &&
                guarded.implementation!!.instructions.drop(1).zip(before).all { (a, b) -> a === b },
                "Modal viewer returns before touching account/config state and keeps original body")
            verify(!isModalReelsLaunchCandidate(guarded, owner()), "Guarded modal cannot match again")
        }
        if (args.isNotEmpty()) {
            val container = DexFileFactory.loadDexContainer(File(args[0]), Opcodes.forApi(35))
            val classes = container.dexEntryNames.flatMap { container.getEntry(it)!!.dexFile.classes }
            verify(classes.sumOf { c -> c.methods.count { isFeedViewerLaunchCandidate(it, c) } } == 1,
                "Actual source has exactly one ordinary Reels launcher")
            verify(classes.sumOf { c -> c.methods.count { isModalReelsLaunchCandidate(it, c) } } == 2,
                "Actual source has exactly two modal Reels launchers")
        }
        println("Feed video tap patch checks: $assertions assertions passed")
    }
}
