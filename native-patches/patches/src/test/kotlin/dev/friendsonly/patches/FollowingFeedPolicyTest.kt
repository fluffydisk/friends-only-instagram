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
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.immutable.ImmutableClassDef
import com.android.tools.smali.dexlib2.immutable.ImmutableField
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodImplementation
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction10t
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction11x
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction21t
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction22c
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction31c
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction31i
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction35c
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableFieldReference
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableMethodReference
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableStringReference

/** Exercises actual instruction selection, scoping, relationship semantics and emitted guards. */
object FollowingFeedPolicyTest {
    private const val HOME = "LX/02xz;"
    private const val ITEM = "LX/07at;"
    private const val MEDIA = "Lcom/instagram/feed/media/Media;"
    private const val USER = "Lcom/instagram/user/model/User;"
    private const val RELATIONSHIP = "Lcom/instagram/api/schemas/RelationshipInfoDict;"
    private const val PANDO = "Lcom/instagram/api/schemas/ImmutablePandoRelationshipInfoDict;"
    private const val BOOLEAN = "Ljava/lang/Boolean;"
    private const val HOOK = "Ldev/friendsonly/patch/runtime/FeedHooks;"
    private val tokens = listOf("feed_items", "items", "pagination_source", "user_has_empty_graph",
        "is_initial_split_head_load_request", "cl_cohort_ifr_hp_guardrail")

    private fun invoke(owner: String, name: String, parameters: List<String>, returns: String,
                       registers: List<Int>, opcode: Opcode = Opcode.INVOKE_STATIC): Instruction =
        ImmutableInstruction35c(opcode, registers.size, registers.getOrElse(0) { 0 },
            registers.getOrElse(1) { 0 }, registers.getOrElse(2) { 0 },
            registers.getOrElse(3) { 0 }, registers.getOrElse(4) { 0 },
            ImmutableMethodReference(owner, name, parameters, returns))

    private fun body(missingToken: String? = null, addRegister: Int = 0, skipRegister: Int = 0,
                     loopResultRegister: Int = 1): List<Instruction> {
        val result = tokens.filter { it != missingToken }.map {
            ImmutableInstruction31c(Opcode.CONST_STRING_JUMBO, 2, ImmutableStringReference(it))
        }.toMutableList<Instruction>()
        for ((owner, name, returns) in listOf(Triple("LX/06aP;", "A01", MEDIA), Triple(ITEM, "A02", ITEM))) {
            result += invoke("LX/0Gyx;", "A10", emptyList(), "LX/02a1;", listOf(8), Opcode.INVOKE_VIRTUAL)
            result += ImmutableInstruction11x(Opcode.MOVE_RESULT_OBJECT, loopResultRegister)
            result += invoke(owner, name, listOf("LX/06aK;"), returns, listOf(8))
            result += ImmutableInstruction11x(Opcode.MOVE_RESULT_OBJECT, 0)
            // Loop starts nine code units before the skip and fourteen before its goto.
            result += ImmutableInstruction21t(Opcode.IF_EQZ, skipRegister, -8)
            result += invoke("Ljava/util/AbstractCollection;", "add", listOf("Ljava/lang/Object;"), "Z",
                listOf(4, addRegister), Opcode.INVOKE_VIRTUAL)
            result += ImmutableInstruction10t(Opcode.GOTO, -13)
        }
        result += ImmutableInstruction11x(Opcode.RETURN_OBJECT, 3)
        return result
    }

    private fun method(owner: String = HOME, registers: Int = 9, code: List<Instruction> = body(),
                       name: String = "unsafeParseFromJson", parameters: List<String> = listOf("LX/0Gyx;"),
                       flags: Int = AccessFlags.PUBLIC.value or AccessFlags.FINAL.value,
                       returns: String = "Ljava/lang/Object;") =
        ImmutableMethod(owner, name, parameters.map { ImmutableMethodParameter(it, emptySet(), null) }, returns,
            flags, emptySet(), emptySet(), ImmutableMethodImplementation(registers, code, emptyList(), emptyList()))

    private fun owner(type: String = HOME) = ImmutableClassDef(type, AccessFlags.PUBLIC.value,
        "Ljava/lang/Object;", emptyList(), null, emptyList(), emptyList(), emptyList())

    private fun getter(owner: String, name: String, returns: String, token: String, fieldOwner: String,
                       fieldName: String, flags: Int = AccessFlags.PUBLIC.value): ImmutableMethod =
        ImmutableMethod(owner, name, emptyList(), returns, flags, emptySet(), emptySet(),
            ImmutableMethodImplementation(3, listOf(
                ImmutableInstruction31c(Opcode.CONST_STRING_JUMBO, 0, ImmutableStringReference(token)),
                ImmutableInstruction22c(Opcode.IGET_OBJECT, 0, 1,
                    ImmutableFieldReference(fieldOwner, fieldName, returns)),
                ImmutableInstruction11x(Opcode.RETURN_OBJECT, 0)), emptyList(), emptyList()))

    private fun contractClasses(followingHash: Int = 765915793, token: String = "friendship_status",
                                relationshipFlags: Int = AccessFlags.PUBLIC.value or AccessFlags.INTERFACE.value,
                                itemFieldFlags: Int = AccessFlags.PUBLIC.value,
                                followingName: String = "CEK"): List<ImmutableClassDef> {
        val media = ImmutableClassDef(MEDIA, AccessFlags.PUBLIC.value, "Ljava/lang/Object;", emptyList(), null,
            emptyList(), emptyList(), listOf(
                getter(MEDIA, "A3U", USER, "user", "LX/07kd;", "A3A"),
                getter(MEDIA, "A3F", "LX/07jm;", "injected", "LX/07kd;", "A2v")))
        val user = ImmutableClassDef(USER, AccessFlags.PUBLIC.value, "Ljava/lang/Object;", emptyList(), null,
            emptyList(), emptyList(), listOf(getter(USER, "A1E", RELATIONSHIP, token, "LX/02aM;", "A17")))
        val following = ImmutableMethod(RELATIONSHIP, followingName, emptyList(), BOOLEAN,
            AccessFlags.PUBLIC.value or AccessFlags.ABSTRACT.value, emptySet(), emptySet(), null)
        val relationship = ImmutableClassDef(RELATIONSHIP, relationshipFlags, "Ljava/lang/Object;", emptyList(), null,
            emptyList(), emptyList(), listOf(following))
        val followingGetter = ImmutableMethod(PANDO, "CEK", emptyList(), BOOLEAN, AccessFlags.PUBLIC.value,
            emptySet(), emptySet(), ImmutableMethodImplementation(3, listOf(
                ImmutableInstruction31i(Opcode.CONST, 1, followingHash),
                invoke("LX/06oD;", "A1r", listOf("I", "I"), BOOLEAN, listOf(2, 1, 0), Opcode.INVOKE_VIRTUAL),
                ImmutableInstruction11x(Opcode.MOVE_RESULT_OBJECT, 0),
                ImmutableInstruction11x(Opcode.RETURN_OBJECT, 0)), emptyList(), emptyList()))
        val pando = ImmutableClassDef(PANDO, AccessFlags.PUBLIC.value, "Ljava/lang/Object;", listOf(RELATIONSHIP), null,
            emptyList(), emptyList(), listOf(followingGetter))
        val field = ImmutableField(ITEM, "A0u", MEDIA, itemFieldFlags, null, emptySet(), emptySet())
        val item = ImmutableClassDef(ITEM, AccessFlags.PUBLIC.value, "Ljava/lang/Object;", emptyList(), null,
            emptyList(), listOf(field), emptyList())
        return listOf(media, user, relationship, pando, item)
    }

    @JvmStatic fun main(args: Array<String>) {
        var assertions = 0
        fun verify(value: Boolean, message: String) { check(value) { message }; assertions++ }
        fun plan(candidate: ImmutableMethod) = inspectHomeFeedParser(candidate, owner(candidate.definingClass))
        val supported = checkNotNull(plan(method()))
        verify(supported.resultRegister == 0 && supported.scratchRegister == 1, "Native live/dead register contract")
        verify(supported.mediaResultIndex < supported.itemResultIndex, "Both native Home arrays discovered")
        verify(plan(method(owner = "LX/07au;")) == null, "Shared FeedItem parser must never be patched")
        verify(plan(method(owner = "Lcom/instagram/direct/Parser;")) == null, "Direct parser must never be patched")
        verify(plan(method(owner = "Lcom/instagram/reels/Parser;")) == null, "Stories parser must never be patched")
        verify(plan(method(registers = 10)) == null, "Changed register layout refuses build")
        verify(plan(method(flags = AccessFlags.PUBLIC.value or AccessFlags.STATIC.value)) == null, "Static parser refuses build")
        verify(plan(method(name = "parseSomethingElse")) == null, "Different method refuses build")
        verify(plan(method(parameters = emptyList())) == null, "Different inputs refuse build")
        for (token in tokens) verify(plan(method(code = body(missingToken = token))) == null, "Missing scope token $token")
        verify(plan(method(code = body(addRegister = 1))) == null, "Changed native add operand refuses build")
        verify(plan(method(code = body(skipRegister = 1))) == null, "Changed native skip operand refuses build")
        verify(plan(method(code = body(loopResultRegister = 2))) == null, "Scratch must be overwritten on loop entry")
        val duplicate = body().dropLast(1) + body()
        verify(plan(method(code = duplicate)) == null, "Ambiguous parse calls refuse build")
        fun contract(classes: List<ImmutableClassDef>) = hasHomeRelationshipContract(classes[0], classes[1], classes[2], classes[3], classes[4])
        verify(contract(contractClasses()), "Supported primary-author/following/ad contract")
        verify(!contract(contractClasses(followingHash = 1601672934)), "followed_by is never accepted as following")
        verify(!contract(contractClasses(followingHash = 0)), "Unknown following field refuses build")
        verify(!contract(contractClasses(token = "is_following_current_user")), "Wrong direction of friendship refuses build")
        verify(!contract(contractClasses(followingName = "CE9")), "Wrong relationship getter refuses build")
        verify(!contract(contractClasses(relationshipFlags = AccessFlags.INTERFACE.value)), "Non-public relationship interface refuses build")
        verify(!contract(contractClasses(itemFieldFlags = AccessFlags.PRIVATE.value)), "Private item field refuses build")
        verify(!contract(contractClasses(itemFieldFlags = AccessFlags.PUBLIC.value or AccessFlags.STATIC.value)), "Static item field refuses build")
        val mutable = MutableMethod(method())
        val original = mutable.instructions.toList()
        val originalUnitSkip = original[supported.itemResultIndex + 1]
        val originalMediaSkip = original[supported.mediaResultIndex + 1]
        installFollowingFeedHooks(mutable, supported)
        verify(mutable.implementation!!.registerCount == 9, "Injection does not grow or shift registers")
        val emitted = mutable.instructions.toList()
        val hooks = emitted.mapNotNull { ((it as? ReferenceInstruction)?.reference as? MethodReference) }
            .filter { it.definingClass == HOOK }
        verify(hooks.count { it.name == "filterHomeMedia" } == 2, "Exactly two scoped media decisions")
        verify(hooks.count { it.name == "isFollowingOnlyEnabled" } == 1, "Wrapped units gated on strict toggle")
        verify(hooks.none { it.name == "replaceHomeItemType" }, "Shared parser/type-dispatch left untouched")
        verify(emitted.contains(originalUnitSkip) && emitted.contains(originalMediaSkip), "Original null-skip guards retained")
        val legacyHook = emitted.indexOfFirst { ((it as? ReferenceInstruction)?.reference as? MethodReference)?.let { ref ->
            ref.definingClass == HOOK && ref.name == "filterHomeMedia" } == true }
        verify(emitted[legacyHook + 1].opcode == Opcode.MOVE_RESULT_OBJECT &&
            emitted[legacyHook + 2].opcode == Opcode.CHECK_CAST && emitted[legacyHook + 3] === originalMediaSkip,
            "Legacy media object cast rejoins its original null guard")
        val branches = emitted.filterIsInstance<BuilderOffsetInstruction>()
        verify(branches.count { it.target.location.instruction === originalUnitSkip } == 3,
            "Null input, disabled filter and allowed post all rejoin original unit skip")
        verify(emitted.filter { it.opcode == Opcode.CONST_4 }.any { (it as? OneRegisterInstruction)?.registerA == 0 },
            "Blocked unit is null for the original native list skip")
        println("FollowingFeedPolicyTest: $assertions assertions passed")
    }
}
