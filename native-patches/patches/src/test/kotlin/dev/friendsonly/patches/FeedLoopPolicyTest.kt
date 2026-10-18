/* SPDX-License-Identifier: GPL-3.0-only */
package dev.friendsonly.patches

import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.Opcodes
import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.*
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.immutable.*
import com.android.tools.smali.dexlib2.immutable.instruction.*
import com.android.tools.smali.dexlib2.immutable.reference.*
import java.nio.ByteBuffer
import java.util.zip.ZipFile

/** Host-side contract and emitted-bytecode tests; never simulate a successful phone test. */
object FeedLoopPolicyTest {
    private const val OWNER = "LX/02Ag;"
    private const val PLAYER = "LX/01zI;"
    private const val MEDIA = "Lcom/instagram/feed/media/Media;"
    private const val STATE = "LX/04cG;"
    private const val END = "LX/04cV;"
    private const val CONFIG = "LX/09XA;"
    private val configParameters = listOf("LX/04ZR;", "Lcom/facebook/video/heroplayer/ipc/AudioFocusLossSettings;",
        "Ljava/lang/Integer;", "I", "I", "Z", "Z", "Z", "Z", "Z", "Z", "Z", "Z", "Z")
    private fun field(op: Opcode, a: Int, b: Int, owner: String, name: String, type: String): Instruction =
        ImmutableInstruction22c(op, a, b, ImmutableFieldReference(owner, name, type))
    private fun call(op: Opcode, registers: List<Int>, owner: String, name: String,
        parameters: List<String>, returns: String): Instruction {
        val r = (registers + List(5) { 0 }).take(5)
        return ImmutableInstruction35c(op, registers.size, r[0], r[1], r[2], r[3], r[4],
            ImmutableMethodReference(owner, name, parameters, returns))
    }
    private fun code() = MutableList<Instruction>(92) { ImmutableInstruction10x(Opcode.NOP) }.apply {
        this[0] = ImmutableInstruction11n(Opcode.CONST_4, 3, 0)
        for ((load, label, pause) in listOf(Triple(21, 22, 23), Triple(83, 84, 85))) {
            this[load] = field(Opcode.IGET_OBJECT, 1, 8, OWNER, "A0K", PLAYER)
            this[label] = ImmutableInstruction31c(Opcode.CONST_STRING_JUMBO, 0, ImmutableStringReference("preview_end"))
            this[pause] = call(Opcode.INVOKE_VIRTUAL, listOf(1, 0), PLAYER, "A0F", listOf("Ljava/lang/String;"), "V")
        }
        this[50] = field(Opcode.IGET_OBJECT, 1, 10, STATE, "A1C", END)
        this[54] = ImmutableInstruction21c(Opcode.SGET_OBJECT, 0, ImmutableFieldReference(END, "A02", END))
        this[55] = call(Opcode.INVOKE_VIRTUAL, listOf(10, 0), STATE, "A0N", listOf(END), "V")
        this[87] = field(Opcode.IGET_OBJECT, 0, 8, OWNER, "A0U", "Lkotlin/jvm/functions/Function1;")
        this[88] = call(Opcode.INVOKE_INTERFACE, listOf(0, 10), "Lkotlin/jvm/functions/Function1;", "invoke",
            listOf("Ljava/lang/Object;"), "Ljava/lang/Object;")
        this[89] = ImmutableInstruction11x(Opcode.RETURN, 5)
    }
    private fun method(owner: String = OWNER, name: String = "A0C", returns: String = "Z",
        parameters: List<String> = listOf(MEDIA, STATE, "Ljava/lang/Integer;"),
        flags: Int = AccessFlags.PRIVATE.value or AccessFlags.FINAL.value, registers: Int = 12,
        body: List<Instruction>? = code()) = ImmutableMethod(owner, name,
            parameters.map { ImmutableMethodParameter(it, emptySet(), null) }, returns, flags, emptySet(), emptySet(),
            body?.let { ImmutableMethodImplementation(registers, it, emptyList(), emptyList()) })
    private fun owner(type: String = OWNER, methods: List<Method> = emptyList()): ClassDef =
        ImmutableClassDef(type, AccessFlags.PUBLIC.value, "Ljava/lang/Object;", emptyList(), null,
            emptyList(), emptyList(), methods)
    private fun player(looping: Int = 1, forward: Boolean = true): ClassDef {
        val init = MutableList<Instruction>(9) { ImmutableInstruction10x(Opcode.NOP) }.apply {
            this[0] = ImmutableInstruction11n(Opcode.CONST_4, 2, looping)
            this[7] = field(Opcode.IPUT_BOOLEAN, 2, 7, PLAYER, "A0N", "Z")
            this[8] = ImmutableInstruction10x(Opcode.RETURN_VOID)
        }
        val binding = MutableList<Instruction>(98) { ImmutableInstruction10x(Opcode.NOP) }.apply {
            this[95] = field(Opcode.IGET_BOOLEAN, 0, 11, PLAYER, "A0N", "Z")
            if (forward) this[96] = call(Opcode.INVOKE_VIRTUAL, listOf(4, 0), "LX/09ZA;", "A0l", listOf("Z"), "V")
            this[97] = ImmutableInstruction10x(Opcode.RETURN_VOID)
        }
        return owner(PLAYER, listOf(
            method(PLAYER, "<init>", "V", emptyList(), AccessFlags.PUBLIC.value or AccessFlags.CONSTRUCTOR.value, 35, init),
            method(PLAYER, "A0B", "V", listOf(MEDIA, "LX/0AVy;", "LX/0JaO;", "LX/09XA;",
                "I", "I", "I", "Z", "Z", "Z"), AccessFlags.PUBLIC.value, 43, binding)))
    }
    private fun configBody() = MutableList<Instruction>(120) { ImmutableInstruction10x(Opcode.NOP) }.apply {
        this[5] = ImmutableInstruction11n(Opcode.CONST_4, 10, 0)
        this[86] = ImmutableInstruction11n(Opcode.CONST_4, 13, 1)
        for ((index, register) in listOf(93 to 12, 110 to 12, 111 to 13)) {
            this[index] = ImmutableInstruction12x(Opcode.MOVE, register, 10)
        }
        for (index in listOf(100, 118)) this[index] = ImmutableInstruction3rc(Opcode.INVOKE_DIRECT_RANGE, 5, 15,
            ImmutableMethodReference(CONFIG, "<init>", configParameters, "V"))
        this[119] = ImmutableInstruction11x(Opcode.RETURN_OBJECT, 5)
    }
    private fun configMethod(registers: Int = 20, code: List<Instruction> = configBody(),
        parameters: List<String> = listOf(MEDIA, STATE, OWNER), flags: Int = 25) =
        method(name = "A01", returns = CONFIG, parameters = parameters, flags = flags, registers = registers, body = code)
    private fun configType(correctFields: Boolean = true): ClassDef {
        val body = MutableList<Instruction>(16) { ImmutableInstruction10x(Opcode.NOP) }.apply {
            this[2] = field(Opcode.IPUT_BOOLEAN, 7, 0, CONFIG, "A09", "Z")
            this[3] = field(Opcode.IPUT_BOOLEAN, 8, 0, CONFIG, if (correctFields) "A0A" else "A09", "Z")
        }
        return owner(CONFIG, listOf(method(CONFIG, "<init>", "V", configParameters,
            AccessFlags.PUBLIC.value or AccessFlags.CONSTRUCTOR.value, 15, body)))
    }
    @JvmStatic fun main(args: Array<String>) {
        var assertions = 0
        fun verify(value: Boolean, message: String) { check(value) { message }; assertions++ }
        verify(isFeedLoopCandidate(method(), owner()), "Supported feed end transition matches")
        verify(!isFeedLoopCandidate(method(), owner("LX/other;")), "Unrelated class rejected")
        verify(!isFeedLoopCandidate(method(owner = "LX/other;"), owner()), "Mismatched method owner rejected")
        verify(!isFeedLoopCandidate(method(name = "A0I"), owner()), "Other feed callbacks preserved")
        verify(!isFeedLoopCandidate(method(returns = "V"), owner()), "Different return contract rejected")
        verify(!isFeedLoopCandidate(method(parameters = listOf(MEDIA)), owner()), "Different parameters rejected")
        for (flag in listOf(AccessFlags.PUBLIC, AccessFlags.STATIC, AccessFlags.NATIVE,
            AccessFlags.ABSTRACT, AccessFlags.SYNCHRONIZED, AccessFlags.DECLARED_SYNCHRONIZED)) {
            verify(!isFeedLoopCandidate(method(flags = 18 or flag.value), owner()), "Unsafe access $flag rejected")
        }
        verify(!isFeedLoopCandidate(method(registers = 11), owner()), "Changed register layout rejected")
        verify(!isFeedLoopCandidate(method(body = null), owner()), "Missing body rejected")
        verify(!isFeedLoopCandidate(method(body = code().dropLast(1)), owner()), "Changed instruction count rejected")
        for (index in listOf(0, 21, 22, 23, 50, 54, 55, 83, 84, 85, 87, 88, 89)) {
            val changed = code().apply { this[index] = ImmutableInstruction10x(Opcode.NOP) }
            verify(!isFeedLoopCandidate(method(body = changed), owner()), "Changed native contract at $index rejected")
        }
        verify(hasNativeFeedLoopContract(player()), "Existing player enables and forwards native loop")
        verify(!hasNativeFeedLoopContract(player(looping = 0)), "Disabled native loop rejected")
        verify(!hasNativeFeedLoopContract(player(forward = false)), "Missing native loop forwarding rejected")
        verify(!hasNativeFeedLoopContract(owner()), "Unrelated player rejected")
        fun verifyEmission(native: Method, type: ClassDef) {
            val mutable = MutableMethod(native)
            val original = mutable.instructions.toList()
            installFeedLoop(mutable, type)
            val emitted = mutable.instructions.toList()
            verify(emitted.size == original.size + 2, "Only two guard instructions inserted")
            verify(emitted[0].opcode == Opcode.CONST_4 && (emitted[0] as OneRegisterInstruction).registerA == 0 &&
                (emitted[0] as NarrowLiteralInstruction).narrowLiteral == 0, "Native transition returns false")
            verify(emitted[1].opcode == Opcode.RETURN && (emitted[1] as OneRegisterInstruction).registerA == 0,
                "Return precedes pause, state update and end-card renderer")
            verify(original.indices.all { emitted[it + 2] === original[it] }, "Original body retained in order")
            verify(mutable.implementation!!.registerCount == native.implementation!!.registerCount,
                "No parameter/local register shift")
            verify(!isFeedLoopCandidate(mutable, type), "Repeated patch rejected")
        }
        verifyEmission(method(), owner())
        verify(isFeedLoopConfigCandidate(configMethod(), owner()), "Dedicated feed preview loop override matches")
        verify(!isFeedLoopConfigCandidate(configMethod(), owner("LX/other;")), "Other config factory rejected")
        verify(!isFeedLoopConfigCandidate(configMethod(registers = 19), owner()), "Config register shift rejected")
        verify(!isFeedLoopConfigCandidate(configMethod(parameters = listOf(MEDIA)), owner()), "Other config parameters rejected")
        verify(!isFeedLoopConfigCandidate(configMethod(flags = 17), owner()), "Instance config factory rejected")
        verify(!isFeedLoopConfigCandidate(configMethod(code = configBody().dropLast(1)), owner()), "Changed config size rejected")
        for (index in listOf(5, 86, 93, 100, 110, 111, 118)) {
            val changed = configBody().apply { this[index] = ImmutableInstruction10x(Opcode.NOP) }
            verify(!isFeedLoopConfigCandidate(configMethod(code = changed), owner()), "Changed config contract $index rejected")
        }
        verify(hasLoopOverrideFieldContract(configType()), "Loop override and value map to native ctor fields")
        verify(!hasLoopOverrideFieldContract(configType(false)), "Swapped native loop fields rejected")
        verify(!hasLoopOverrideFieldContract(owner()), "Unrelated config type rejected")
        fun verifyConfigEmission(native: Method, type: ClassDef) {
            val mutable = MutableMethod(native)
            val original = mutable.instructions.toList()
            installFeedLoopConfig(mutable, type)
            val emitted = mutable.instructions.toList()
            verify(emitted.size == original.size && original.indices.filter { emitted[it] !== original[it] } == listOf(86),
                "Only the preview-specific override instruction changes")
            verify(emitted[86].opcode == Opcode.CONST_4 && (emitted[86] as OneRegisterInstruction).registerA == 13 &&
                (emitted[86] as NarrowLiteralInstruction).narrowLiteral == 0, "Preview inherits true native feed looping")
            verify(!isFeedLoopConfigCandidate(mutable, type), "Repeated config patch rejected")
        }
        verifyConfigEmission(configMethod(), owner())
        if (args.isNotEmpty()) ZipFile(args[0]).use { zip ->
            val dex = zip.getInputStream(zip.getEntry("classes2.dex")).use { it.readAllBytes() }
            val classes = DexBackedDexFile(Opcodes.forApi(35), ByteBuffer.wrap(dex)).classes
            val nativeOwner = classes.single { it.type == OWNER }
            val candidates = nativeOwner.methods.filter { isFeedLoopCandidate(it, nativeOwner) }
            verify(candidates.size == 1, "Exactly one actual source transition matches")
            verify(hasNativeFeedLoopContract(classes.single { it.type == PLAYER }), "Actual source native player loops")
            verifyEmission(candidates.single(), nativeOwner)
            val configs = nativeOwner.methods.filter { isFeedLoopConfigCandidate(it, nativeOwner) }
            verify(configs.size == 1, "Exactly one actual preview config factory matches")
            verify(hasLoopOverrideFieldContract(classes.single { it.type == CONFIG }), "Actual native loop override field mapping")
            verifyConfigEmission(configs.single(), nativeOwner)
        }
        println("Feed video loop patch checks: $assertions assertions passed")
    }
}
