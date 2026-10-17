/* SPDX-License-Identifier: GPL-3.0-only
 * Feed-type fingerprint informed by FeurStagram; see native-patches/NOTICE.md.
 */
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
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.NarrowLiteralInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OffsetInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference

private const val FEED_HOOK = "Ldev/friendsonly/patch/runtime/FeedHooks;"
private const val HOME_PARSER = "LX/02xz;"
private const val HOME_ITEM = "LX/07at;"
private const val MEDIA = "Lcom/instagram/feed/media/Media;"
private const val USER = "Lcom/instagram/user/model/User;"
private const val RELATIONSHIP = "Lcom/instagram/api/schemas/RelationshipInfoDict;"
private const val PANDO_RELATIONSHIP = "Lcom/instagram/api/schemas/ImmutablePandoRelationshipInfoDict;"
private const val BOOLEAN = "Ljava/lang/Boolean;"

internal data class HomeFeedPlan(
    val mediaResultIndex: Int,
    val itemResultIndex: Int,
    val resultRegister: Int,
    val scratchRegister: Int,
)

private fun Instruction.methodReference() = (this as? ReferenceInstruction)?.reference as? MethodReference
private fun Instruction.fieldReference() = (this as? ReferenceInstruction)?.reference as? FieldReference
private fun Instruction.stringReference() = ((this as? ReferenceInstruction)?.reference as? StringReference)?.string

/**
 * The main Home response owns both legacy items and wrapped feed_items. Hook its two
 * collection boundaries, never the shared Media, FeedItem, Story or Direct parsers.
 * The native null guard already skips missing items before adding to either list.
 */
internal fun inspectHomeFeedParser(method: Method, owner: ClassDef): HomeFeedPlan? {
    if (owner.type != HOME_PARSER || method.definingClass != owner.type ||
        method.name != "unsafeParseFromJson" || method.returnType != "Ljava/lang/Object;" ||
        method.parameterTypes.map { it.toString() } != listOf("LX/0Gyx;") ||
        AccessFlags.STATIC.isSet(method.accessFlags)) return null
    val implementation = method.implementation ?: return null
    if (implementation.registerCount != 9 || implementation.tryBlocks.isNotEmpty()) return null
    val code = implementation.instructions.toList()
    var address = 0
    val addresses = code.map { instruction -> address.also { address += instruction.codeUnits } }
    val strings = code.mapNotNull { it.stringReference() }.toSet()
    if (!strings.containsAll(setOf("feed_items", "items", "pagination_source", "user_has_empty_graph",
            "is_initial_split_head_load_request", "cl_cohort_ifr_hp_guardrail"))) return null
    fun resultIndex(parseOwner: String, parseName: String, returns: String): Int? {
        val indexes = code.indices.filter { index ->
            val reference = code[index].methodReference()
            code[index].opcode == Opcode.INVOKE_STATIC && reference?.definingClass == parseOwner &&
                reference.name == parseName && reference.returnType == returns &&
                reference.parameterTypes.map { it.toString() } == listOf("LX/06aK;")
        }
        if (indexes.size != 1) return null
        val index = indexes.single()
        val result = code.getOrNull(index + 1) as? OneRegisterInstruction ?: return null
        val skip = code.getOrNull(index + 2) as? OneRegisterInstruction ?: return null
        val add = code.getOrNull(index + 3) as? FiveRegisterInstruction ?: return null
        val addMethod = code[index + 3].methodReference() ?: return null
        if (code[index + 1].opcode != Opcode.MOVE_RESULT_OBJECT || result.registerA != 0 ||
            code[index + 2].opcode != Opcode.IF_EQZ || skip.registerA != 0 ||
            code[index + 3].opcode != Opcode.INVOKE_VIRTUAL || add.registerCount != 2 ||
            add.registerC != 4 || add.registerD != 0 || addMethod.definingClass != "Ljava/util/AbstractCollection;" ||
            addMethod.name != "add" || addMethod.parameterTypes.map { it.toString() } != listOf("Ljava/lang/Object;") ||
            addMethod.returnType != "Z" || code.getOrNull(index + 4)?.opcode != Opcode.GOTO) return null
        val skipOffset = code[index + 2] as? OffsetInstruction ?: return null
        val gotoOffset = code[index + 4] as? OffsetInstruction ?: return null
        val loopAddress = addresses[index + 2] + skipOffset.codeOffset
        if (addresses[index + 4] + gotoOffset.codeOffset != loopAddress) return null
        val loop = addresses.indexOf(loopAddress)
        if (loop < 0 || loop >= index) return null
        val next = code[loop].methodReference() ?: return null
        val parser = code[loop] as? FiveRegisterInstruction ?: return null
        if (code[loop].opcode != Opcode.INVOKE_VIRTUAL || next.definingClass != "LX/0Gyx;" ||
            next.name != "A10" || next.parameterTypes.isNotEmpty() || next.returnType != "LX/02a1;" ||
            parser.registerCount != 1 || parser.registerC != 8 ||
            code.getOrNull(loop + 1)?.opcode != Opcode.MOVE_RESULT_OBJECT ||
            (code[loop + 1] as? OneRegisterInstruction)?.registerA != 1) return null
        // v1 is dead after parse, and is overwritten by the original loop's next-token result.
        // Its value must never be an input to the native add/skip/goto sequence above.
        return index + 1
    }
    val media = resultIndex("LX/06aP;", "A01", MEDIA) ?: return null
    val item = resultIndex(HOME_ITEM, "A02", HOME_ITEM) ?: return null
    if (media >= item) return null
    return HomeFeedPlan(media, item, 0, 1)
}

private fun ClassDef.getter(name: String, returns: String): Method? = methods.singleOrNull { method ->
    method.name == name && method.parameterTypes.isEmpty() && method.returnType == returns &&
        AccessFlags.PUBLIC.isSet(method.accessFlags) && !AccessFlags.STATIC.isSet(method.accessFlags)
}

private fun Method.hasFieldToken(token: String, owner: String, fieldName: String, type: String): Boolean {
    val code = implementation?.instructions?.toList() ?: return false
    return code.any { it.stringReference() == token } && code.any { instruction ->
        val field = instruction.fieldReference()
        instruction.opcode == Opcode.IGET_OBJECT && field?.definingClass == owner &&
            field.name == fieldName && field.type == type
    }
}

/** Public getter/field semantics checked before any code is changed. CE9 is followed_by, not following. */
internal fun hasHomeRelationshipContract(media: ClassDef, user: ClassDef, relationship: ClassDef,
                                         pando: ClassDef, item: ClassDef): Boolean {
    if (media.type != MEDIA || user.type != USER || relationship.type != RELATIONSHIP ||
        pando.type != PANDO_RELATIONSHIP || item.type != HOME_ITEM ||
        listOf(media, user, relationship, pando).any { !AccessFlags.PUBLIC.isSet(it.accessFlags) } ||
        !AccessFlags.INTERFACE.isSet(relationship.accessFlags) || RELATIONSHIP !in pando.interfaces) return false
    val author = media.getter("A3U", USER) ?: return false
    val injected = media.getter("A3F", "LX/07jm;") ?: return false
    val friendship = user.getter("A1E", RELATIONSHIP) ?: return false
    if (!author.hasFieldToken("user", "LX/07kd;", "A3A", USER) ||
        !injected.hasFieldToken("injected", "LX/07kd;", "A2v", "LX/07jm;") ||
        !friendship.hasFieldToken("friendship_status", "LX/02aM;", "A17", RELATIONSHIP) ||
        relationship.getter("CEK", BOOLEAN) == null) return false
    val following = pando.getter("CEK", BOOLEAN)?.implementation?.instructions?.toList() ?: return false
    // Java String.hashCode("following") == 765915793; never use followed_by (1601672934).
    if (following.none { (it as? NarrowLiteralInstruction)?.narrowLiteral == 765915793 } ||
        following.any { (it as? NarrowLiteralInstruction)?.narrowLiteral == 1601672934 } ||
        following.none { instruction ->
            val reference = instruction.methodReference()
            instruction.opcode == Opcode.INVOKE_VIRTUAL && reference?.definingClass == "LX/06oD;" &&
                reference.name == "A1r" && reference.returnType == BOOLEAN &&
                reference.parameterTypes.map { it.toString() } == listOf("I", "I")
        }) return false
    return item.fields.count { field -> field.name == "A0u" && field.type == MEDIA &&
        AccessFlags.PUBLIC.isSet(field.accessFlags) && !AccessFlags.STATIC.isSet(field.accessFlags) } == 1
}

internal fun installFollowingFeedHooks(method: MutableMethod, plan: HomeFeedPlan) {
    // Wrapped feed items: retain only the primary ordinary-post field A0u. All
    // discovery/ad/unknown wrappers lack it and become null at the native skip guard.
    val originalSkip = method.instructions[plan.itemResultIndex + 1]
    method.addInstructionsWithLabels(plan.itemResultIndex + 1, """
        if-eqz v${plan.resultRegister}, :friends_only_original_home_skip
        invoke-static { }, $FEED_HOOK->isFollowingOnlyEnabled()Z
        move-result v${plan.scratchRegister}
        if-eqz v${plan.scratchRegister}, :friends_only_original_home_skip
        iget-object v${plan.scratchRegister}, v${plan.resultRegister}, $HOME_ITEM->A0u:$MEDIA
        invoke-static/range { v${plan.scratchRegister} .. v${plan.scratchRegister} }, $FEED_HOOK->filterHomeMedia(Ljava/lang/Object;)Ljava/lang/Object;
        move-result-object v${plan.scratchRegister}
        if-nez v${plan.scratchRegister}, :friends_only_original_home_skip
        const/4 v${plan.resultRegister}, 0x0
    """.trimIndent(), ExternalLabel("friends_only_original_home_skip", originalSkip))
    // Legacy ordinary-media array: use the same author/ad decision before its own
    // original IF_EQZ skip; no hooks are added inside the shared media deserialiser.
    method.addInstructions(plan.mediaResultIndex + 1, """
        invoke-static/range { v${plan.resultRegister} .. v${plan.resultRegister} }, $FEED_HOOK->filterHomeMedia(Ljava/lang/Object;)Ljava/lang/Object;
        move-result-object v${plan.resultRegister}
        check-cast v${plan.resultRegister}, $MEDIA
    """.trimIndent())
}

private object HomeFeedParser : Fingerprint(custom = { method, owner -> inspectHomeFeedParser(method, owner) != null })

@Suppress("unused")
val followingFeed = bytecodePatch(
    name = "Friends Only following feed",
    description = "Strict native Home feed: ordinary posts require explicit followed-author metadata; skips other Home units without modifying shared Story or Direct parsers.",
    default = true,
) {
    compatibleWith(INSTAGRAM)
    extendWith("extensions/extension.mpe")
    execute {
        check(hasHomeRelationshipContract(classDefBy(MEDIA), classDefBy(USER), classDefBy(RELATIONSHIP),
            classDefBy(PANDO_RELATIONSHIP), classDefBy(HOME_ITEM))) {
            "Unsupported Home author/following metadata contract; no APK produced."
        }
        val matches = HomeFeedParser.matchAll()
        check(matches.size == 1) { "Unsupported Home-item parser: expected one, found ${matches.size}. No APK produced." }
        val match = matches.single()
        val plan = checkNotNull(inspectHomeFeedParser(match.method, match.classDef))
        installFollowingFeedHooks(match.method, plan)
    }
}
