/* SPDX-License-Identifier: GPL-3.0-only */
package dev.friendsonly.patches

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.NarrowLiteralInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

internal data class VisualPoint(val owner: String, val parent: String, val name: String,
    val parameters: List<String>, val returns: String, val registers: Int, val count: Int,
    val returnIndex: Int, val register: Int, val hook: String, val layout: Int? = null)
internal val VISUAL_POINTS = listOf(
    VisualPoint("Linstagram/features/feed/mainfeed/actionbar/MainFeedActionBar;", "Landroid/widget/FrameLayout;", "<init>",
        listOf("Landroid/content/Context;", "Landroid/util/AttributeSet;", "I"), "V", 10, 141, 136, 6,
        "Ldev/friendsonly/patch/runtime/ReferenceChrome;->homeHeader(Landroid/view/View;)V", 0x7f0e0bec),
    VisualPoint("LX/06hD;", "Landroid/widget/FrameLayout;", "<init>", listOf("Landroid/content/Context;"),
        "V", 11, 32, 28, 9, "Ldev/friendsonly/patch/runtime/ScreenDesign;->story(Landroid/view/View;)V", 0x7f0e13a0),
    VisualPoint("LX/07Ah;", "LX/069z;", "A05", listOf("Landroid/content/Context;", "Landroid/view/ViewGroup;"),
        "Landroid/view/View;", 9, 17, 16, 0, "Ldev/friendsonly/patch/runtime/ScreenDesign;->mediaCard(Landroid/view/View;)V", 0x7f0e1498),
    VisualPoint("LX/00gJ;", "Ljava/lang/Object;", "A07",
        listOf("Landroid/view/LayoutInflater;", "Landroid/view/ViewGroup\$LayoutParams;", "Landroid/view/ViewGroup;", "I"),
        "Landroid/view/View;", 13, 11, 10, 0, "Ldev/friendsonly/patch/runtime/ScreenDesign;->managed(Landroid/view/View;I)V"),
)
internal fun isVisualPoint(method: Method, owner: ClassDef, point: VisualPoint): Boolean {
    if (owner.type != point.owner || owner.superclass != point.parent || method.definingClass != owner.type ||
        method.name != point.name || method.returnType != point.returns ||
        method.parameterTypes.map { it.toString() } != point.parameters || !AccessFlags.PUBLIC.isSet(method.accessFlags) ||
        AccessFlags.STATIC.isSet(method.accessFlags)) return false
    val impl = method.implementation ?: return false
    val code = impl.instructions.toList()
    if (impl.registerCount != point.registers || impl.tryBlocks.isNotEmpty() || code.size != point.count ||
        code[point.returnIndex].opcode != (if (point.returns == "V") Opcode.RETURN_VOID else Opcode.RETURN_OBJECT)) return false
    return if (point.layout != null) code.any { (it as? NarrowLiteralInstruction)?.narrowLiteral == point.layout }
        else code.any { val ref = (it as? ReferenceInstruction)?.reference as? MethodReference
            ref?.definingClass == "LX/00gJ;" && ref.name == "A08" && ref.returnType == "Landroid/view/View;" }
}
internal fun installVisualPoint(method: MutableMethod, owner: ClassDef, point: VisualPoint) {
    check(isVisualPoint(method, owner, point)) { "Unsupported reference appearance point: ${point.owner}." }
    val registers = if (point.name == "A07") "v0, v12" else "v${point.register}"
    method.addInstruction(point.returnIndex, "invoke-static { $registers }, ${point.hook}")
}
private const val CIRCULAR = "Lcom/instagram/common/ui/widget/imageview/CircularImageView;"
private const val IMAGE = "Lcom/instagram/common/ui/widget/imageview/IgImageView;"
private const val SPINNER = "Lcom/instagram/ui/widget/gradientspinner/GradientSpinner;"
private const val APPEARANCE = "Ldev/friendsonly/patch/runtime/ReferenceChrome;"
internal fun isAvatarDraw(method: Method, owner: ClassDef): Boolean =
    owner.type == CIRCULAR && owner.superclass == IMAGE && method.definingClass == owner.type && method.name == "onDraw" &&
        method.parameterTypes.map { it.toString() } == listOf("Landroid/graphics/Canvas;") && method.returnType == "V" &&
        method.accessFlags == AccessFlags.PUBLIC.value && method.implementation?.let {
            it.registerCount == 3 && it.tryBlocks.isEmpty() && it.instructions.count() == 7 && it.instructions.any { instruction ->
                val ref = (instruction as? ReferenceInstruction)?.reference as? MethodReference
                instruction.opcode == Opcode.INVOKE_SUPER && ref?.definingClass == IMAGE && ref.name == "onDraw"
            }
        } == true
internal fun installAvatarDraw(method: MutableMethod, owner: ClassDef) {
    check(isAvatarDraw(method, owner))
    // Keep native rendering/image-loaded callbacks. Replacing the RETURN location also
    // makes the native IF_EQZ target enter our post-draw hook, rather than skip it.
    method.replaceInstruction(6, "invoke-static { v1 }, $APPEARANCE->storyAvatar(Landroid/view/View;)Z")
    method.addInstructions(7, """
        move-result v0
        if-eqz v0, :finish_avatar
        invoke-virtual { v1 }, $IMAGE->getBitmap()Landroid/graphics/Bitmap;
        move-result-object v0
        invoke-static { v1, v2, v0 }, $APPEARANCE->drawStoryAvatar(Landroid/widget/ImageView;Landroid/graphics/Canvas;Landroid/graphics/Bitmap;)Z
        :finish_avatar
        return-void
    """.trimIndent())
}
internal fun isRingDraw(method: Method, owner: ClassDef): Boolean {
    if (owner.type != SPINNER || owner.superclass != "Landroid/view/View;" || method.name != "onDraw" ||
        method.definingClass != owner.type || method.returnType != "V" ||
        method.parameterTypes.map { it.toString() } != listOf("Landroid/graphics/Canvas;") ||
        method.accessFlags != (AccessFlags.PUBLIC.value or AccessFlags.FINAL.value)) return false
    val code = method.implementation ?: return false
    return code.registerCount == 37 && code.tryBlocks.isEmpty() && code.instructions.count() == 389 &&
        code.instructions.first().opcode == Opcode.CONST_4 &&
        setOf("A0R", "A0S", "A0T").all { name -> owner.fields.any { it.name == name && it.type == "Landroid/graphics/Paint;" } } &&
        code.instructions.any { ((it as? ReferenceInstruction)?.reference as? MethodReference)?.name == "beginRecording" }
}
internal fun installRingDraw(method: MutableMethod, owner: ClassDef) {
    check(isRingDraw(method, owner))
    method.addInstructions(0, """
        move-object/from16 v0, v35
        iget-object v1, v0, $SPINNER->A0R:Landroid/graphics/Paint;
        iget-object v2, v0, $SPINNER->A0S:Landroid/graphics/Paint;
        iget-object v3, v0, $SPINNER->A0T:Landroid/graphics/Paint;
        invoke-static { v0, v1, v2, v3 }, $APPEARANCE->storyRing(Landroid/view/View;Landroid/graphics/Paint;Landroid/graphics/Paint;Landroid/graphics/Paint;)V
    """.trimIndent())
}

@Suppress("unused")
val referenceAppearance = bytecodePatch(name = "Friends Only reference appearance",
    description = "Direct native presentation hooks for own header, card spacing and square Story avatars; preserves native action/media/authentication handlers.", default = true) {
    compatibleWith(INSTAGRAM)
    extendWith("extensions/extension.mpe")
    execute {
        VISUAL_POINTS.forEach { point ->
            val matches = Fingerprint(definingClass = point.owner, custom = { method, owner -> isVisualPoint(method, owner, point) }).matchAll()
            check(matches.size == 1) { "Ambiguous reference appearance point: ${point.owner}." }
            installVisualPoint(matches.single().method, classDefBy(point.owner), point)
        }
        val avatar = Fingerprint(definingClass = CIRCULAR, custom = ::isAvatarDraw).matchAll()
        check(avatar.size == 1)
        check(classDefBy(IMAGE).methods.any { it.name == "getBitmap" && it.parameterTypes.isEmpty() &&
            it.returnType == "Landroid/graphics/Bitmap;" && AccessFlags.PUBLIC.isSet(it.accessFlags) })
        installAvatarDraw(avatar.single().method, classDefBy(CIRCULAR))
        val ring = Fingerprint(definingClass = SPINNER, custom = ::isRingDraw).matchAll()
        check(ring.size == 1)
        installRingDraw(ring.single().method, classDefBy(SPINNER))
    }
}
