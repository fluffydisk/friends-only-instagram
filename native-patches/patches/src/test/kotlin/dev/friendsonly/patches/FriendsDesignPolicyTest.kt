/* SPDX-License-Identifier: GPL-3.0-only */
package dev.friendsonly.patches

import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.*
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.immutable.*
import com.android.tools.smali.dexlib2.immutable.instruction.*
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableMethodReference
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.util.zip.ZipFile
import com.android.tools.smali.dexlib2.Opcodes
import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile
import javax.xml.parsers.DocumentBuilderFactory

object FriendsDesignPolicyTest {
    private var checks = 0
    private fun verify(value: Boolean, message: String) { check(value) { message }; checks++ }
    private fun document(target: String = NATIVE_ENTRY, packageName: String = FRIENDS_ONLY_PACKAGE): Document {
        val factory = DocumentBuilderFactory.newInstance()
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        return factory.newDocumentBuilder().parse(ByteArrayInputStream("""
            <manifest xmlns:android="http://schemas.android.com/apk/res/android" package="$packageName">
              <uses-permission android:name="android.permission.INTERNET" />
              <application android:name="com.instagram.app.InstagramApplication">
                <activity android:name="$NATIVE_ENTRY" android:exported="true" />
                <activity android:name="com.instagram.login.LoginActivity" android:exported="false" />
                <activity-alias android:name="com.instagram.Launcher" android:targetActivity="$target" android:exported="true">
                  <intent-filter><action android:name="android.intent.action.MAIN" /><category android:name="android.intent.category.LAUNCHER" /></intent-filter>
                </activity-alias>
                <activity-alias android:name="com.instagram.DisabledLauncher" android:targetActivity="$target" android:enabled="false">
                  <intent-filter><action android:name="android.intent.action.MAIN" /><category android:name="android.intent.category.LAUNCHER" /></intent-filter>
                </activity-alias>
                <activity-alias android:name="com.instagram.DeepLink" android:targetActivity="$NATIVE_ENTRY">
                  <intent-filter><action android:name="android.intent.action.VIEW" /><category android:name="android.intent.category.BROWSABLE" /></intent-filter>
                </activity-alias>
              </application>
            </manifest>
        """.trimIndent().toByteArray()))
    }
    private fun call(name: String, owner: String): Instruction = ImmutableInstruction35c(Opcode.INVOKE_VIRTUAL,
        if (name == "inflate") 4 else 1, 2, 3, 4, 5, 0, ImmutableMethodReference(owner, name,
            if (name == "inflate") listOf("I", "Landroid/view/ViewGroup;", "Z") else emptyList(),
            if (name == "inflate") "Landroid/view/View;" else "V"))
    private fun fixture(owner: String, name: String = "inflate", registers: Int = 6, returns: String = "Landroid/view/View;",
        parameters: List<String> = listOf("I", "Landroid/view/ViewGroup;", "Z"), flags: Int = AccessFlags.PUBLIC.value): ImmutableMethod {
        val fast = owner == "LX/01Mq;"
        val code = MutableList<Instruction>(if (fast) 56 else 35) { ImmutableInstruction10x(Opcode.NOP) }
        for (i in 1..4) code[i] = call("inflate", "Landroid/view/LayoutInflater;")
        code[6] = call("unlock", "Ljava/util/concurrent/locks/ReentrantLock;")
        for (i in (if (fast) listOf(30,43,55) else listOf(7,31))) code[i] = ImmutableInstruction11x(Opcode.RETURN_OBJECT, 0)
        code[0] = ImmutableInstruction10t(Opcode.GOTO, code.take(if (fast) 30 else 7).sumOf { it.codeUnits })
        val handler = ImmutableExceptionHandler(null, code.take(10).sumOf { it.codeUnits })
        val tries = List(if (fast) 2 else 1) { i -> ImmutableTryBlock(i, 1, listOf(handler)) }
        return ImmutableMethod(owner, name, parameters.map { ImmutableMethodParameter(it, emptySet(), null) }, returns,
            flags, emptySet(), emptySet(), ImmutableMethodImplementation(registers, code, tries, emptyList()))
    }
    private fun owner(type: String, superclass: String = "Landroid/view/LayoutInflater;") =
        ImmutableClassDef(type, AccessFlags.PUBLIC.value, superclass, emptyList(), null, emptySet(), emptyList(), emptyList())

    private fun actionFixture(registers: Int = 6): ImmutableMethod {
        val code = MutableList<Instruction>(104) { ImmutableInstruction10x(Opcode.NOP) }
        code[1] = ImmutableInstruction35c(Opcode.INVOKE_DIRECT, 2, 4, 5, 0, 0, 0,
            ImmutableMethodReference("LX/0Gb5;", "<init>", listOf("Landroid/view/View;"), "V"))
        listOf(0x7f0b3724,0x7f0b3722,0x7f0b3729,0x7f0b3728,0x7f0b3766,0x7f0b3740).forEachIndexed { i, id ->
            code[i + 3] = ImmutableInstruction31i(Opcode.CONST, 0, id)
        }
        code[101] = ImmutableInstruction10x(Opcode.RETURN_VOID)
        return ImmutableMethod("LX/07bY;", "<init>", listOf(ImmutableMethodParameter("Landroid/view/View;", emptySet(), null)),
            "V", AccessFlags.PUBLIC.value or AccessFlags.CONSTRUCTOR.value, emptySet(), emptySet(),
            ImmutableMethodImplementation(registers, code, emptyList(), emptyList()))
    }

    private fun nativeAudit(args: Array<String>) {
        fun classes(path: String): Map<String, com.android.tools.smali.dexlib2.iface.ClassDef> {
            val found = linkedMapOf<String, com.android.tools.smali.dexlib2.iface.ClassDef>()
            ZipFile(path).use { zip ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    if (!entry.name.matches(Regex("classes(?:[0-9]+)?\\.dex"))) continue
                    val dex = DexBackedDexFile(Opcodes.forApi(35), ByteBuffer.wrap(zip.getInputStream(entry).use { it.readBytes() }))
                    dex.classes.filter { it.type in setOf("LX/01Mq;", "LX/0A3y;", "LX/07bY;", "Ldev/friendsonly/patch/runtime/WelcomeActivity;", "Ldev/friendsonly/patch/runtime/FocusSettings;") }
                        .forEach { verify(found.put(it.type, it) == null, "Unique native/extension class definition") }
                }
            }
            return found
        }
        val original = classes(args[0])
        for (type in listOf("LX/01Mq;", "LX/0A3y;", "LX/07bY;")) {
            val definition = checkNotNull(original[type])
            val candidates = definition.methods.filter { if (type == "LX/07bY;") isFeedActionHolder(it, definition) else isDesignInflater(it, definition) }
            verify(candidates.size == 1, "Actual original native contract: $type")
            val mutable = MutableMethod(candidates.single())
            if (type == "LX/07bY;") decorateFeedActions(mutable, definition) else decorateInflater(mutable, definition)
            verify(mutable.implementation!!.registerCount == 6, "Actual native emission register count")
        }
        if (args.size > 1) {
            val emitted = classes(args[1])
            for (type in listOf("LX/01Mq;", "LX/0A3y;", "LX/07bY;")) {
                val definition = checkNotNull(emitted[type])
                val hooked = definition.methods.filter { method -> method.implementation?.instructions?.any {
                    ((it as? ReferenceInstruction)?.reference as? MethodReference)?.definingClass == "Ldev/friendsonly/patch/runtime/ScreenDesign;"
                } == true }
                verify(hooked.size == 1, "Actual output hooks exactly one method: $type")
                val count = hooked.single().implementation!!.instructions.count {
                    ((it as? ReferenceInstruction)?.reference as? MethodReference)?.definingClass == "Ldev/friendsonly/patch/runtime/ScreenDesign;"
                }
                verify(count == (if (type == "LX/01Mq;") 3 else if (type == "LX/0A3y;") 2 else 1), "Actual output hook count")
            }
            verify(emitted["Ldev/friendsonly/patch/runtime/WelcomeActivity;"]?.superclass == "Landroid/app/Activity;", "Real own launcher Activity definition exists")
            verify(emitted.containsKey("Ldev/friendsonly/patch/runtime/FocusSettings;"), "Real own settings class exists")
        }
    }

    @JvmStatic fun main(args: Array<String>) {
        val doc = document()
        verify(installWelcome(doc) == 2, "Both enabled and disabled launcher aliases must target own welcome")
        val activities = doc.getElementsByTagName("activity")
        val welcome = (0 until activities.length).map { activities.item(it) as Element }.single { it.getAttribute("android:name") == WELCOME }
        verify(welcome.getAttribute("android:exported") == "false", "Only launcher alias is public")
        verify(welcome.getAttribute("android:label") == FRIENDS_ONLY_LABEL, "Own welcome label")
        verify(welcome.getAttribute("android:theme").startsWith("@android:style/"), "Independent platform theme")
        verify((activities.item(1) as Element).getAttribute("android:name") == "com.instagram.login.LoginActivity", "Original auth Activity unchanged")
        verify(doc.getElementsByTagName("uses-permission").length == 1, "No new permission")
        val aliases = doc.getElementsByTagName("activity-alias")
        verify((aliases.item(2) as Element).getAttribute("android:targetActivity") == NATIVE_ENTRY, "Deep link target unchanged")
        verify((aliases.item(1) as Element).getAttribute("android:enabled") == "false", "Original alias state unchanged")
        verify(runCatching { installWelcome(doc) }.isFailure, "Reject duplicate entry")
        verify(runCatching { installWelcome(document(target = "unrelated.Entry")) }.isFailure, "Reject unexpected launcher routing")
        verify(runCatching { installWelcome(document(packageName = SOURCE_PACKAGE)) }.isFailure, "Identity must run before welcome")
        verify(DESIGN_LAYOUTS.size == 18 && DESIGN_LAYOUTS.values.toSet().size == 18, "Exact distinct native layout inventory")
        for (type in listOf("LX/01Mq;", "LX/0A3y;")) {
            val original = fixture(type)
            verify(isDesignInflater(original, owner(type)), "Supported resource-ID inflater")
            verify(!isDesignInflater(fixture(type, name = "inflateOther"), owner(type)), "Wrong method")
            verify(!isDesignInflater(fixture(type, registers = 7), owner(type)), "Wrong registers")
            verify(!isDesignInflater(fixture(type, returns = "Ljava/lang/Object;"), owner(type)), "Wrong return")
            verify(!isDesignInflater(fixture(type, parameters = listOf("Lorg/xmlpull/v1/XmlPullParser;", "Landroid/view/ViewGroup;", "Z")), owner(type)), "Parser overload must not be hooked")
            verify(!isDesignInflater(fixture(type, flags = AccessFlags.PUBLIC.value or AccessFlags.STATIC.value), owner(type)), "Wrong invocation")
            verify(!isDesignInflater(original, owner(type, "Landroid/widget/FrameLayout;")), "Wrong owner parent")
            val mutable = MutableMethod(original)
            decorateInflater(mutable, owner(type))
            val code = mutable.instructions.toList()
            val hooks = code.filter { ((it as? ReferenceInstruction)?.reference as? MethodReference)?.definingClass == "Ldev/friendsonly/patch/runtime/ScreenDesign;" }
            verify(hooks.size == (if (type == "LX/01Mq;") 3 else 2), "Every return hooks")
            verify(mutable.implementation!!.registerCount == 6, "No scratch registers")
            verify(code.count { it.opcode == Opcode.RETURN_OBJECT } == hooks.size, "All native returns remain")
            var address = 0
            val addresses = code.associateBy { val result = address; address += it.codeUnits; result }
            val jump = code.first() as OffsetInstruction
            verify(((addresses[jump.codeOffset] as ReferenceInstruction).reference as MethodReference).name == "inflated", "Jump to original return must enter hook")
            for (hook in hooks) {
                val registers = hook as FiveRegisterInstruction
                verify(registers.registerCount == 3 && registers.registerC == 0 && registers.registerD == 3 && registers.registerE == 5,
                    "Exact native result, layout and attachToRoot registers")
            }
            verify(runCatching { decorateInflater(mutable, owner(type)) }.isFailure, "Reject double patch")
        }
        verify(!isDesignInflater(fixture("LX/Unknown;"), owner("LX/Unknown;")), "Unknown inflater untouched")
        val holder = owner("LX/07bY;", "LX/0Gb5;")
        val action = actionFixture()
        verify(isFeedActionHolder(action, holder), "Native action constructor")
        verify(!isFeedActionHolder(actionFixture(registers = 7), holder), "Changed action registers rejected")
        verify(!isFeedActionHolder(action, owner("LX/Other;", "LX/0Gb5;")), "Other holder rejected")
        val mutableAction = MutableMethod(action)
        val previous = mutableAction.instructions.toList()
        decorateFeedActions(mutableAction, holder)
        verify(mutableAction.instructions.size == 105, "One holder hook added")
        verify(previous.indices.all { mutableAction.instructions[if (it < 101) it else it + 1] === previous[it] }, "All native holder instructions retained")
        verify(runCatching { decorateFeedActions(mutableAction, holder) }.isFailure, "Reject repeated holder patch")
        if (args.isNotEmpty()) nativeAudit(args)
        println("FriendsDesignPolicyTest: $checks assertions passed")
    }
}
