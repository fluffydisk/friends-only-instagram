/* SPDX-License-Identifier: GPL-3.0-only
 * Independent app-identity adaptation of FeurStagram's clone patch. See NOTICE.md.
 */
package dev.friendsonly.patches

import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction21c
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction31c
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.StringReference
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableStringReference
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node

internal const val SOURCE_PACKAGE = "com.instagram.android"
internal const val FRIENDS_ONLY_PACKAGE = "dev.friendsonly.instagram"
internal const val FRIENDS_ONLY_LABEL = "Friends Only"
private const val FRIENDS_ONLY_ICON = "@drawable/friends_only_icon"

private fun Element.childElements(): List<Element> = buildList {
    for (index in 0 until childNodes.length) {
        val child = childNodes.item(index)
        if (child.nodeType == Node.ELEMENT_NODE) add(child as Element)
    }
}

/** Identifier-only transformation; Java/JNI class names and external visibility queries stay intact. */
internal fun renameManifestIdentity(document: Document): Map<String, String> {
    val manifest = document.documentElement
    check(manifest.tagName == "manifest" && manifest.getAttribute("package") == SOURCE_PACKAGE) {
        "Unexpected source manifest identity; refusing to rename it."
    }
    val application = manifest.childElements().single { it.tagName == "application" }
    // Every component in the supported build is already fully qualified. Renaming a manifest with
    // relative component names would resolve them against the new package and break startup.
    val componentNames = setOf("application", "activity", "activity-alias", "service", "receiver", "provider")
    val componentAttributes = setOf("android:name", "android:targetActivity", "android:appComponentFactory", "android:backupAgent")
    (listOf(application) + application.childElements()).forEach { element ->
        if (element.tagName in componentNames) componentAttributes.forEach { attribute ->
            val value = element.getAttribute(attribute)
            check(value.isEmpty() || ('.' in value && !value.startsWith('.'))) {
                "Relative component name $value is unsupported; class names must remain unchanged."
            }
        }
    }

    val replacements = linkedMapOf(SOURCE_PACKAGE to FRIENDS_ONLY_PACKAGE)
    val permissionReplacements = linkedMapOf<String, String>()
    manifest.childElements().filter { it.tagName in setOf("permission", "permission-tree", "permission-group") }
        .forEach { permission ->
            val value = permission.getAttribute("android:name")
            val absolute = if (value.startsWith('.')) SOURCE_PACKAGE + value else value
            if (absolute.startsWith("$SOURCE_PACKAGE.")) {
                val renamed = FRIENDS_ONLY_PACKAGE + absolute.removePrefix(SOURCE_PACKAGE)
                permissionReplacements[value] = renamed
                permissionReplacements[absolute] = renamed
                permission.setAttribute("android:name", renamed)
            }
        }
    check(permissionReplacements.isNotEmpty()) { "Missing expected package-owned permissions." }
    replacements.putAll(permissionReplacements)
    // Include all references, including service/provider read/write permissions, rather than just
    // uses-permission. External Android/Meta permissions are not renamed.
    val allElements = manifest.getElementsByTagName("*")
    for (index in 0 until allElements.length) {
        val element = allElements.item(index) as Element
        val permissionAttributes = mutableListOf("android:permission", "android:readPermission", "android:writePermission", "android:permissionGroup")
        if (element.tagName in setOf("uses-permission", "uses-permission-sdk-23", "uses-permission-sdk-m")) {
            permissionAttributes.add("android:name")
        }
        permissionAttributes.forEach { attribute ->
            permissionReplacements[element.getAttribute(attribute)]?.let { element.setAttribute(attribute, it) }
        }
    }

    val declaredProviders = application.childElements().filter { it.tagName == "provider" }
    check(declaredProviders.isNotEmpty()) { "Missing expected declared providers." }
    declaredProviders.forEach { provider ->
        val oldAuthorities = provider.getAttribute("android:authorities").split(';')
        check(oldAuthorities.all { it.isNotBlank() && it == it.trim() }) { "Unsupported provider authority." }
        val newAuthorities = oldAuthorities.map { old ->
            val renamed = if (old == SOURCE_PACKAGE || old.startsWith("$SOURCE_PACKAGE.")) {
                FRIENDS_ONLY_PACKAGE + old.removePrefix(SOURCE_PACKAGE)
            } else {
                // A declared provider owns its authority even when the name has no source prefix.
                // A provider under <queries> only describes another app and is deliberately skipped.
                "${FRIENDS_ONLY_PACKAGE}_$old"
            }
            val previous = replacements.put(old, renamed)
            check(previous == null || previous == renamed) { "Conflicting provider identity mapping." }
            // The pinned source also contains these exact root-URI forms (including one trailing
            // slash). Preserve all other URI text; never rewrite arbitrary substrings or queries.
            replacements["content://$old"] = "content://$renamed"
            replacements["content://$old/"] = "content://$renamed/"
            renamed
        }
        provider.setAttribute("android:authorities", newAuthorities.joinToString(";"))
    }
    manifest.setAttribute("package", FRIENDS_ONLY_PACKAGE)
    application.setAttribute("android:icon", FRIENDS_ONLY_ICON)
    application.setAttribute("android:roundIcon", FRIENDS_ONLY_ICON)
    application.childElements().filter { component ->
        component.tagName in setOf("activity", "activity-alias") &&
            component.childElements().any { filter ->
                filter.tagName == "intent-filter" &&
                    filter.childElements().any { it.tagName == "action" && it.getAttribute("android:name") == "android.intent.action.MAIN" } &&
                    filter.childElements().any { it.tagName == "category" && it.getAttribute("android:name") == "android.intent.category.LAUNCHER" }
            }
    }.forEach {
        it.setAttribute("android:icon", FRIENDS_ONLY_ICON)
        it.setAttribute("android:roundIcon", FRIENDS_ONLY_ICON)
    }
    return replacements
}

/** Rewrites only exact string constants. No descriptors, native code or integrity checks change. */
private fun BytecodePatchContext.renameIdentityStrings(replacements: Map<String, String>) {
    var packageReferences = 0
    getAllClassesWithStrings().forEach { owner ->
        val mutableClass by lazy { mutableClassDefBy(owner) }
        owner.methods.forEach { originalMethod ->
            // Other selected hooks may already have inserted instructions. Use immutable code only
            // as a cheap candidate filter, then locate replacements in the current mutable method.
            if (originalMethod.implementation?.instructions?.any { instruction ->
                    (instruction.opcode == Opcode.CONST_STRING || instruction.opcode == Opcode.CONST_STRING_JUMBO) &&
                        ((instruction as? ReferenceInstruction)?.reference as? StringReference)?.string in replacements
                } != true) return@forEach
            val mutableMethod by lazy {
                mutableClass.methods.single {
                    it.name == originalMethod.name && it.returnType == originalMethod.returnType &&
                        it.parameterTypes.map { type -> type.toString() } == originalMethod.parameterTypes.map { type -> type.toString() }
                }
            }
            mutableMethod.implementation?.instructions?.toList()?.forEachIndexed { index, instruction ->
                if (instruction.opcode != Opcode.CONST_STRING && instruction.opcode != Opcode.CONST_STRING_JUMBO) return@forEachIndexed
                val old = ((instruction as? ReferenceInstruction)?.reference as? StringReference)?.string ?: return@forEachIndexed
                val renamed = replacements[old] ?: return@forEachIndexed
                val register = (instruction as OneRegisterInstruction).registerA
                val reference = ImmutableStringReference(renamed)
                // Preserve jumbo width when present; Morphe's final writer can promote a normal
                // const-string if its rebuilt string pool needs a larger reference index.
                mutableMethod.replaceInstruction(index, if (instruction.opcode == Opcode.CONST_STRING_JUMBO) {
                    BuilderInstruction31c(Opcode.CONST_STRING_JUMBO, register, reference)
                } else {
                    BuilderInstruction21c(Opcode.CONST_STRING, register, reference)
                })
                if (old == SOURCE_PACKAGE) packageReferences++
            }
        }
    }
    check(packageReferences > 0) { "No source app-identity references found; unsupported bytecode." }
}

@Suppress("unused")
val appIdentity = resourcePatch(
    name = "Friends Only app identity",
    description = "Installs as Friends Only under its own package; normal Android signing and Instagram authentication checks remain in force.",
    default = true,
) {
    compatibleWith(INSTAGRAM)
    var bytecodeContext: BytecodePatchContext? = null
    dependsOn(bytecodePatch { execute { bytecodeContext = this } })
    execute {
        val replacements = document("AndroidManifest.xml").use { document -> renameManifestIdentity(document) }
        get("res/drawable/friends_only_icon.xml").apply {
            parentFile.mkdirs()
            writeText("""
                <vector xmlns:android="http://schemas.android.com/apk/res/android"
                    android:width="48dp" android:height="48dp"
                    android:viewportWidth="48" android:viewportHeight="48">
                    <path android:fillColor="#202124" android:pathData="M10,0H38Q48,0 48,10V38Q48,48 38,48H10Q0,48 0,38V10Q0,0 10,0Z" />
                    <path android:fillColor="#FFFFFF" android:pathData="M18,12A5,5 0,1 0,18,22A5,5 0,1 0,18,12M30,12A5,5 0,1 0,30,22A5,5 0,1 0,30,12M8,36V32Q8,25 18,25Q24,25 26,29Q28,25 30,25Q40,25 40,32V36Z" />
                </vector>
            """.trimIndent())
        }
        // This is the sole app/launcher label in the pinned build. Match its resource ID, never
        // replace every occurrence of "Instagram" in login, help, permissions or other product UI.
        val labelName = document("res/values/public.xml").use { document ->
            val resources = document.documentElement.childElements().filter {
                it.tagName == "public" && it.getAttribute("type") == "string" &&
                    it.getAttribute("id").lowercase() == "0x7f130009"
            }
            check(resources.size == 1) { "Unexpected app-label resource; refusing to rename unrelated text." }
            resources.single().getAttribute("name")
        }
        document("res/values/strings.xml").use { document ->
            val labels = document.documentElement.childElements().filter {
                it.tagName == "string" && it.getAttribute("name") == labelName
            }
            check(labels.size == 1 && labels.single().textContent == "Instagram") { "Unexpected app-label string." }
            labels.single().textContent = FRIENDS_ONLY_LABEL
        }
        val context = checkNotNull(bytecodeContext) { "Missing bytecode context for app identity." }
        context.renameIdentityStrings(replacements)
        bytecodeContext = null
    }
}
