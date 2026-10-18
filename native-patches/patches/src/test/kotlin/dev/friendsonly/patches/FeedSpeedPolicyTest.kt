/* SPDX-License-Identifier: GPL-3.0-only */
package dev.friendsonly.patches

import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcodes
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.*
import com.android.tools.smali.dexlib2.immutable.*
import com.android.tools.smali.dexlib2.immutable.instruction.*
import com.android.tools.smali.dexlib2.immutable.reference.*
import java.nio.ByteBuffer
import java.util.zip.ZipFile

/** Tests the real emitted changes and refuses unsupported player/gesture contracts. */
object FeedSpeedPolicyTest {
    private const val ACTIONS = "LX/06b1;"
    private const val CONTROLLER = "LX/01zY;"
    private const val CONFIG = "Lcom/facebook/mobileconfig/factory/MobileConfigUnsafeContext;"
    private val parameters = listOf("Landroid/view/View;", "Lcom/instagram/feed/media/Media;", "LX/0A0J;", "LX/04cG;")
    private val changed = setOf(92, 110, 708, 727)
    private fun call(owner: String, name: String, parameters: List<String>, returns: String,
                     opcode: Opcode = Opcode.INVOKE_VIRTUAL) = ImmutableInstruction35c(opcode, 1, 0, 0, 0, 0, 0,
        ImmutableMethodReference(owner, name, parameters, returns))
    private fun field(owner: String, name: String, type: String, opcode: Opcode = Opcode.SGET_OBJECT) =
        ImmutableInstruction21c(opcode, 0, ImmutableFieldReference(owner, name, type))
    private fun code(): List<Instruction> {
        val result = MutableList<Instruction>(980) { ImmutableInstruction10x(Opcode.NOP) }
        result[44] = call(CONTROLLER, "A01", listOf(CONTROLLER, "Z"), "Z", Opcode.INVOKE_STATIC)
        result[72] = call("LX/07JA;", "A00", listOf("Lcom/instagram/common/session/UserSession;"), "Z", Opcode.INVOKE_STATIC)
        for (index in listOf(73, 92, 110, 708)) result[index] = ImmutableInstruction11x(Opcode.MOVE_RESULT, 0)
        for (index in listOf(74, 93, 111)) result[index] = ImmutableInstruction21t(Opcode.IF_EQZ, 0, 1)
        result[709] = ImmutableInstruction21t(Opcode.IF_NEZ, 0, 1)
        result[710] = ImmutableInstruction20t(Opcode.GOTO_16, 1)
        result[727] = ImmutableInstruction22t(Opcode.IF_EQ, 1, 0, 1)
        result[734] = ImmutableInstruction21t(Opcode.IF_EQZ, 0, 1)
        for ((index, literal) in listOf(88 to 36326953648935618L, 106 to 36326953648673470L,
                705 to 36326953648739007L)) result[index] = ImmutableInstruction51l(Opcode.CONST_WIDE, 0, literal)
        for (index in listOf(91, 109, 707)) result[index] = call(CONFIG, "BZb", listOf("J"), "Z", Opcode.INVOKE_INTERFACE)
        result[725] = field("LX/0009;", "A00", "Ljava/lang/Integer;")
        result[726] = field("LX/0009;", "A0C", "Ljava/lang/Integer;")
        result[728] = ImmutableInstruction22c(Opcode.IGET_OBJECT, 12, 11,
            ImmutableFieldReference(CONTROLLER, "A05", "Lkotlin/jvm/functions/Function0;"))
        result[732] = call("LX/01zI;", "A0J", emptyList(), "Z")
        result[735] = call("LX/01zI;", "A06", emptyList(), "Lcom/instagram/feed/media/Media;")
        result[909] = call("Ljava/util/concurrent/atomic/AtomicBoolean;", "compareAndSet", listOf("Z", "Z"), "Z")
        result[926] = ImmutableInstruction21ih(Opcode.CONST_HIGH16, 7, 0x40000000)
        result[931] = call("LX/09bL;", "A0M", listOf("F"), "V")
        result[934] = ImmutableInstruction21c(Opcode.CONST_STRING, 7, ImmutableStringReference("Error setting 2x playback speed"))
        result[935] = ImmutableInstruction21c(Opcode.CONST_STRING, 0, ImmutableStringReference("FeedVideo2xSpeed"))
        result[979] = ImmutableInstruction10x(Opcode.RETURN_VOID)
        var address = 0
        val addresses = result.map { instruction -> address.also { address += instruction.codeUnits } }
        for ((from, to) in listOf(74 to 142, 93 to 142, 111 to 142, 709 to 711, 734 to 142)) {
            result[from] = ImmutableInstruction21t(result[from].opcode, 0, addresses[to] - addresses[from])
        }
        result[710] = ImmutableInstruction20t(Opcode.GOTO_16, addresses[142] - addresses[710])
        result[727] = ImmutableInstruction22t(Opcode.IF_EQ, 1, 0, addresses[142] - addresses[727])
        return result
    }
    private fun method(owner: String = ACTIONS, name: String = "A06", types: List<String> = parameters,
                       registers: Int = 33, code: List<Instruction> = code(),
                       flags: Int = AccessFlags.PUBLIC.value or AccessFlags.FINAL.value, returns: String = "V",
                       tryBlocks: List<ImmutableTryBlock> = listOf(ImmutableTryBlock(0, 1,
                           listOf(ImmutableExceptionHandler("Ljava/lang/Exception;", 1))))) =
        ImmutableMethod(owner, name, types.map { ImmutableMethodParameter(it, emptySet(), null) }, returns,
            flags, emptySet(), emptySet(), ImmutableMethodImplementation(registers, code, tryBlocks, emptyList()))
    private fun owner(type: String = ACTIONS) = ImmutableClassDef(type, AccessFlags.PUBLIC.value,
        "Ljava/lang/Object;", emptyList(), null, emptyList(), emptyList(), emptyList())
    private fun reset(): ImmutableMethod {
        val body = MutableList<Instruction>(40) { ImmutableInstruction10x(Opcode.NOP) }
        body[2] = call("Ljava/util/concurrent/atomic/AtomicBoolean;", "getAndSet", listOf("Z"), "Z")
        body[20] = ImmutableInstruction21ih(Opcode.CONST_HIGH16, 1, 0x3f800000)
        body[25] = call("LX/09bL;", "A0M", listOf("F"), "V")
        body[29] = ImmutableInstruction21c(Opcode.CONST_STRING, 0, ImmutableStringReference("FeedVideo2xSpeed"))
        body[38] = call("Landroid/view/ViewParent;", "requestDisallowInterceptTouchEvent", listOf("Z"), "V", Opcode.INVOKE_INTERFACE)
        body[39] = ImmutableInstruction11x(Opcode.RETURN, 6)
        return method(owner = CONTROLLER, name = "A01", types = listOf(CONTROLLER, "Z"), registers = 9,
            code = body, returns = "Z", flags = AccessFlags.PUBLIC.value or AccessFlags.STATIC.value)
    }

    private fun master(key: Long = 36326953648804544L, owner: String = "LX/07JA;"): ImmutableMethod {
        val body = MutableList<Instruction>(9) { ImmutableInstruction10x(Opcode.NOP) }
        body[4] = ImmutableInstruction51l(Opcode.CONST_WIDE, 0, key)
        body[6] = call(CONFIG, "BZb", listOf("J"), "Z", Opcode.INVOKE_INTERFACE)
        body[7] = ImmutableInstruction11x(Opcode.MOVE_RESULT, 0)
        body[8] = ImmutableInstruction11x(Opcode.RETURN, 0)
        return method(owner = owner, name = "A00", types = listOf("Lcom/instagram/common/session/UserSession;"),
            registers = 3, code = body, returns = "Z", flags = AccessFlags.PUBLIC.value or AccessFlags.STATIC.value,
            tryBlocks = emptyList())
    }

    @JvmStatic fun main(args: Array<String>) {
        var assertions = 0
        fun verify(value: Boolean, message: String) { check(value) { message }; assertions++ }
        fun rejected(candidate: Method, message: String) = verify(inspectFeedSpeedHandler(candidate, owner()) == null, message)
        verify(inspectFeedSpeedHandler(method(), owner()) == FeedSpeedPlan(listOf(92, 110, 708), 727), "Exact four handler changes")
        rejected(method(owner = "Lcom/instagram/direct/Player;"), "Direct player excluded")
        rejected(method(owner = "Lcom/instagram/reels/Player;"), "Stories player excluded")
        rejected(method(owner = "LX/0R26;"), "Reels player excluded")
        rejected(method(name = "A07"), "Tap handler excluded")
        rejected(method(types = parameters.dropLast(1)), "Different native arguments rejected")
        rejected(method(registers = 34), "Different registers rejected")
        rejected(method(flags = AccessFlags.PUBLIC.value or AccessFlags.STATIC.value), "Static method rejected")
        rejected(method(returns = "Z"), "Different return type rejected")
        rejected(method(code = code().dropLast(1)), "Different instruction layout rejected")
        rejected(method(tryBlocks = emptyList()), "Missing native speed error handling rejected")
        for (index in listOf(44, 72, 73, 74, 88, 91, 92, 93, 106, 109, 110, 111, 705, 707, 708, 709,
                710, 725, 726, 727, 728, 732, 734, 735, 909, 926, 931, 934, 935)) {
            val broken = code().toMutableList()
            broken[index] = ImmutableInstruction10x(Opcode.NOP)
            rejected(method(code = broken), "Unsupported native contract at $index refused")
        }
        val mutable = MutableMethod(method())
        val original = mutable.implementation!!.instructions.toList()
        installFeedSpeed(mutable, owner())
        val patched = mutable.implementation!!.instructions.toList()
        verify(patched.size == original.size, "No touch lifecycle instructions added/deleted")
        verify(patched.indices.filter { patched[it] !== original[it] }.toSet() == changed, "Exactly three feature results and center rejection changed")
        verify(patched.indices.filter { it !in changed }.all { patched[it] === original[it] }, "Every other native instruction retained in order")
        verify(listOf(92, 110, 708).all { patched[it].opcode == Opcode.CONST_4 &&
            (patched[it] as NarrowLiteralInstruction).narrowLiteral == 1 &&
            (patched[it] as OneRegisterInstruction).registerA == 0 }, "Only local feature results enabled")
        verify(patched[727].opcode == Opcode.NOP, "Whole video surface accepts native hold")
        verify(mutable.implementation!!.registerCount == 33, "Registers unchanged")
        verify(mutable.implementation!!.tryBlocks.size == 1, "Native error handler preserved")
        verify(inspectFeedSpeedHandler(mutable, owner()) == null, "Already patched method cannot be patched twice")
        verify(runCatching { installFeedSpeed(mutable, owner()) }.isFailure, "Direct repeated injection refused")
        verify(hasFeedSpeedResetContract(reset()), "Native reset restores 1.0 and clears intercept")
        val wrongSpeed = reset()
        val resetBody = wrongSpeed.implementation!!.instructions.toMutableList()
        resetBody[20] = ImmutableInstruction21ih(Opcode.CONST_HIGH16, 1, 0x40000000)
        verify(!hasFeedSpeedResetContract(method(owner = CONTROLLER, name = "A01", types = listOf(CONTROLLER, "Z"),
            registers = 9, code = resetBody, returns = "Z", flags = AccessFlags.PUBLIC.value or AccessFlags.STATIC.value)), "Non-1x reset refused")
        verify(hasFeedSpeedMasterContract(master()), "Exact dedicated feed-speed gate qualifies")
        verify(!hasFeedSpeedMasterContract(master(key = 36326953648804545L)), "Different experiment key refused")
        verify(!hasFeedSpeedMasterContract(master(owner = CONFIG)), "Shared config getter must never be patched")
        val mutableMaster = MutableMethod(master())
        val beforeMaster = mutableMaster.implementation!!.instructions.toList()
        installFeedSpeedMaster(mutableMaster)
        verify(mutableMaster.implementation!!.instructions.indices.filter {
            mutableMaster.implementation!!.instructions[it] !== beforeMaster[it] } == listOf(7), "Only dedicated feed master result changed")
        verify(!hasFeedSpeedMasterContract(mutableMaster), "Repeated master patch refused")

        // Optional independent audit against the user's pinned source; CI's
        // bundle-only checks require no proprietary APK fixture.
        if (args.isNotEmpty()) ZipFile(args[0]).use { zip ->
            val bytes = zip.getInputStream(zip.getEntry("classes2.dex")).use { it.readAllBytes() }
            val classes = DexBackedDexFile(Opcodes.forApi(35), ByteBuffer.wrap(bytes)).classes
            val actions = classes.single { it.type == ACTIONS }
            val native = actions.methods.single { it.name == "A06" }
            verify(inspectFeedSpeedHandler(native, actions) != null, "Pinned source matches complete feed-speed plan")
            verify(hasFeedSpeedResetContract(classes.single { it.type == CONTROLLER }.methods.single { it.name == "A01" }), "Pinned source has real native 1x reset")
            val actual = MutableMethod(native)
            val before = actual.implementation!!.instructions.toList()
            installFeedSpeed(actual, actions)
            verify(actual.implementation!!.instructions.indices.filter { actual.implementation!!.instructions[it] !== before[it] }.toSet() == changed,
                "Actual source emits exactly the four planned handler changes")
            val nativeMaster = classes.single { it.type == "LX/07JA;" }.methods.single { it.name == "A00" }
            verify(hasFeedSpeedMasterContract(nativeMaster), "Actual source master gate is dedicated feed-speed key")
            val compiledMaster = MutableMethod(nativeMaster)
            val beforeActualMaster = compiledMaster.implementation!!.instructions.toList()
            installFeedSpeedMaster(compiledMaster)
            verify(compiledMaster.implementation!!.instructions.indices.filter {
                compiledMaster.implementation!!.instructions[it] !== beforeActualMaster[it] } == listOf(7), "Actual master emits only single local result change")
        }
        println("Feed hold speed patch checks: $assertions assertions passed")
    }
}
