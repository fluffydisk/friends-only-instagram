/* SPDX-License-Identifier: GPL-3.0-only */
package dev.friendsonly.patches

import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import com.android.tools.smali.dexlib2.*
import com.android.tools.smali.dexlib2.iface.*
import com.android.tools.smali.dexlib2.iface.instruction.*
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.immutable.*
import com.android.tools.smali.dexlib2.immutable.instruction.*
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableMethodReference
import com.android.tools.smali.dexlib2.builder.BuilderOffsetInstruction
import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile
import java.nio.ByteBuffer
import java.util.zip.ZipFile

object ReferenceVisualPolicyTest {
    private var checks = 0
    private fun verify(ok: Boolean, label: String) { check(ok) { label }; checks++ }
    private fun owner(point: VisualPoint) = ImmutableClassDef(point.owner, 1, point.parent, emptyList(), null, emptySet(), emptyList(), emptyList())
    private fun method(point: VisualPoint, registers: Int = point.registers, count: Int = point.count, name: String = point.name): ImmutableMethod {
        val code = MutableList<Instruction>(count) { ImmutableInstruction10x(Opcode.NOP) }
        if (point.layout != null) code[0] = ImmutableInstruction31i(Opcode.CONST, 0, point.layout)
        else code[0] = ImmutableInstruction3rc(Opcode.INVOKE_VIRTUAL_RANGE, 0, 8,
            ImmutableMethodReference("LX/00gJ;", "A08", emptyList(), "Landroid/view/View;"))
        if (point.returnIndex < count) code[point.returnIndex] = if (point.returns == "V") ImmutableInstruction10x(Opcode.RETURN_VOID) else ImmutableInstruction11x(Opcode.RETURN_OBJECT, 0)
        return ImmutableMethod(point.owner, name, point.parameters.map { ImmutableMethodParameter(it, emptySet(), null) },
            point.returns, 1, emptySet(), emptySet(), ImmutableMethodImplementation(registers, code, emptyList(), emptyList()))
    }
    private fun actual(apk: String): Map<String, ClassDef> {
        val needed = VISUAL_POINTS.map { it.owner }.toSet() + setOf("Lcom/instagram/common/ui/widget/imageview/CircularImageView;", "Lcom/instagram/ui/widget/gradientspinner/GradientSpinner;")
        val result = linkedMapOf<String, ClassDef>()
        ZipFile(apk).use { zip ->
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                if (!entry.name.matches(Regex("classes(?:[0-9]+)?\\.dex"))) continue
                val dex = DexBackedDexFile(Opcodes.forApi(35), ByteBuffer.wrap(zip.getInputStream(entry).use { it.readBytes() }))
                dex.classes.filter { it.type in needed }.forEach { result[it.type] = it }
            }
        }
        return result
    }
    @JvmStatic fun main(args: Array<String>) {
        for (point in VISUAL_POINTS) {
            val type = owner(point)
            val original = method(point)
            verify(isVisualPoint(original, type, point), "Expected visual contract ${point.owner}")
            verify(!isVisualPoint(method(point, registers = point.registers + 1), type, point), "Reject register shift")
            verify(!isVisualPoint(method(point, name = "wrong"), type, point), "Reject other method")
            verify(!isVisualPoint(method(point, count = point.count + 1), type, point), "Reject code drift")
            val mutable = MutableMethod(original)
            val before = mutable.instructions.toList()
            installVisualPoint(mutable, type, point)
            verify(mutable.instructions.size == before.size + 1, "One call only")
            verify(before.indices.all { mutable.instructions[if (it < point.returnIndex) it else it + 1] === before[it] }, "Every native instruction retained")
            verify(mutable.implementation!!.registerCount == original.implementation!!.registerCount, "No locals/parameters shift")
            verify(runCatching { installVisualPoint(mutable, type, point) }.isFailure, "Refuse duplicate hook")
        }
        // Native draw and image-loaded callbacks must run before our Story-only overlay.
        val avatarName = "Lcom/instagram/common/ui/widget/imageview/CircularImageView;"
        val imageName = "Lcom/instagram/common/ui/widget/imageview/IgImageView;"
        val avatarOwner = ImmutableClassDef(avatarName, 1, imageName, emptyList(), null, emptySet(), emptyList(), emptyList())
        val avatarCode = listOf<Instruction>(
            ImmutableInstruction11n(Opcode.CONST_4, 0, 0),
            ImmutableInstruction35c(Opcode.INVOKE_STATIC, 1, 2, 0, 0, 0, 0,
                ImmutableMethodReference("Lfixture/Check;", "canvas", listOf("Landroid/graphics/Canvas;"), "V")),
            ImmutableInstruction35c(Opcode.INVOKE_SUPER, 2, 1, 2, 0, 0, 0,
                ImmutableMethodReference(imageName, "onDraw", listOf("Landroid/graphics/Canvas;"), "V")),
            ImmutableInstruction22c(Opcode.IGET_OBJECT, 0, 1,
                com.android.tools.smali.dexlib2.immutable.reference.ImmutableFieldReference(avatarName, "A04", "Landroid/graphics/drawable/Drawable;")),
            ImmutableInstruction21t(Opcode.IF_EQZ, 0, 5),
            ImmutableInstruction35c(Opcode.INVOKE_VIRTUAL, 2, 0, 2, 0, 0, 0,
                ImmutableMethodReference("Landroid/graphics/drawable/Drawable;", "draw", listOf("Landroid/graphics/Canvas;"), "V")),
            ImmutableInstruction10x(Opcode.RETURN_VOID))
        val avatar = MutableMethod(ImmutableMethod(avatarName, "onDraw",
            listOf(ImmutableMethodParameter("Landroid/graphics/Canvas;", emptySet(), null)), "V", 1,
            emptySet(), emptySet(), ImmutableMethodImplementation(3, avatarCode, emptyList(), emptyList())))
        val nativePrefix = avatar.instructions.take(6)
        verify(isAvatarDraw(avatar, avatarOwner), "Supported native avatar fixture")
        installAvatarDraw(avatar, avatarOwner)
        verify(nativePrefix.indices.all { avatar.instructions[it] === nativePrefix[it] }, "Native draw/callback prefix retained in order")
        verify((avatar.instructions[4] as BuilderOffsetInstruction).target.location.instruction === avatar.instructions[6], "Original branch reaches post-draw appearance hook")
        verify(avatar.instructions.last().opcode == Opcode.RETURN_VOID && avatar.instructions.count { it.opcode == Opcode.RETURN_VOID } == 1, "No early return before native image draw")
        verify(avatar.implementation!!.registerCount == 3, "Avatar register layout retained")
        verify(!isAvatarDraw(avatar, avatarOwner), "Avatar duplicate guard")
        if (args.isNotEmpty()) {
            val source = actual(args[0])
            for (point in VISUAL_POINTS) {
                val type = checkNotNull(source[point.owner])
                val matching = type.methods.filter { isVisualPoint(it, type, point) }
                verify(matching.size == 1, "Original native point ${point.owner}")
                val mutable = MutableMethod(matching.single())
                installVisualPoint(mutable, type, point)
                verify(mutable.implementation!!.registerCount == point.registers, "Actual source register preservation")
            }
            for ((name, predicate, installer) in listOf(
                Triple("Lcom/instagram/common/ui/widget/imageview/CircularImageView;", ::isAvatarDraw, ::installAvatarDraw),
                Triple("Lcom/instagram/ui/widget/gradientspinner/GradientSpinner;", ::isRingDraw, ::installRingDraw))) {
                val type = checkNotNull(source[name])
                val matching = type.methods.filter { predicate(it, type) }
                verify(matching.size == 1, "Native draw point $name")
                val mutable = MutableMethod(matching.single())
                installer(mutable, type)
                verify(mutable.implementation!!.registerCount == matching.single().implementation!!.registerCount, "Draw register preservation")
                verify(!predicate(mutable, type), "Duplicate draw hook rejected")
            }
            if (args.size > 1) {
                val out = actual(args[1])
                for (point in VISUAL_POINTS) {
                    val type = checkNotNull(out[point.owner])
                    val matching = type.methods.single { it.name == point.name && it.parameterTypes.map { p -> p.toString() } == point.parameters }
                    verify(matching.implementation!!.instructions.any { instruction ->
                        ((instruction as? ReferenceInstruction)?.reference as? MethodReference)?.toString() == point.hook
                    }, "Real output direct hook exists")
                }
                for (name in listOf("Lcom/instagram/common/ui/widget/imageview/CircularImageView;", "Lcom/instagram/ui/widget/gradientspinner/GradientSpinner;")) {
                    val draw = out.getValue(name).methods.single { it.name == "onDraw" }
                    verify(draw.implementation!!.instructions.any { ((it as? ReferenceInstruction)?.reference as? MethodReference)?.definingClass == "Ldev/friendsonly/patch/runtime/ReferenceChrome;" }, "Real output scoped draw hook")
                }
            }
        }
        println("ReferenceVisualPolicyTest: $checks assertions passed")
    }
}
