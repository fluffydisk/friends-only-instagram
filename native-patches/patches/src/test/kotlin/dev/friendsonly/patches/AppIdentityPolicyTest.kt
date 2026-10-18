/* SPDX-License-Identifier: GPL-3.0-only */
package dev.friendsonly.patches

import java.io.ByteArrayInputStream
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Document
import org.w3c.dom.Element

/** JVM checks for the manifest cases that can silently break an independently installed build. */
object AppIdentityPolicyTest {
    @JvmStatic
    fun main(args: Array<String>) {
        var assertions = 0
        fun verify(value: Boolean, message: String) {
            check(value) { message }
            assertions++
        }
        fun document(extra: String = "", packageName: String = SOURCE_PACKAGE, activity: String = "com.instagram.main.MainActivity"): Document {
            val xml = """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android" package="$packageName">
                    <permission android:name="$SOURCE_PACKAGE.permission.SYSTEM_ONLY" />
                    <permission android:name=".permission.RECEIVE_ADM_MESSAGE" />
                    <uses-permission android:name="$SOURCE_PACKAGE.permission.SYSTEM_ONLY" />
                    <uses-permission android:name=".permission.RECEIVE_ADM_MESSAGE" />
                    <uses-permission android:name="android.permission.INTERNET" />
                    <queries><provider android:authorities="com.facebook.external.query" /></queries>
                    <application android:name="com.instagram.app.InstagramApplication" android:label="@string/app_label">
                        <activity android:name="$activity" android:permission="$SOURCE_PACKAGE.permission.SYSTEM_ONLY">
                            <intent-filter><action android:name="android.intent.action.MAIN" /><category android:name="android.intent.category.LAUNCHER" /></intent-filter>
                        </activity>
                        <activity-alias android:name="com.instagram.main.LauncherAlias" android:targetActivity="com.instagram.main.MainActivity" />
                        <provider android:name="com.instagram.OwnProvider" android:authorities="$SOURCE_PACKAGE.files;com.instagram.family" android:readPermission="$SOURCE_PACKAGE.permission.SYSTEM_ONLY" android:writePermission="android.permission.WRITE_CONTACTS" />
                        <provider android:name="com.instagram.StartupProvider" android:authorities="$SOURCE_PACKAGE.files" />
                        <service android:name="com.instagram.ExternalService" android:permission="$SOURCE_PACKAGE.permission.CREATE_SHORTCUT" />
                        $extra
                    </application>
                </manifest>
            """.trimIndent()
            val factory = DocumentBuilderFactory.newInstance()
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            return factory.newDocumentBuilder().parse(ByteArrayInputStream(xml.toByteArray()))
        }
        val manifest = document()
        val replacements = renameManifestIdentity(manifest)
        fun element(tag: String, index: Int = 0) = manifest.getElementsByTagName(tag).item(index) as Element
        verify(manifest.documentElement.getAttribute("package") == FRIENDS_ONLY_PACKAGE, "Package was not renamed")
        verify(element("application").getAttribute("android:name") == "com.instagram.app.InstagramApplication", "Application class must stay unchanged")
        verify(element("activity").getAttribute("android:name") == "com.instagram.main.MainActivity", "Activity class must stay unchanged")
        verify(element("activity-alias").getAttribute("android:targetActivity") == "com.instagram.main.MainActivity", "Alias target must stay unchanged")
        verify(element("provider").getAttribute("android:authorities") == "com.facebook.external.query", "Visibility queries describe external apps")
        verify(element("provider", 1).getAttribute("android:authorities") == "$FRIENDS_ONLY_PACKAGE.files;${FRIENDS_ONLY_PACKAGE}_com.instagram.family", "Declared authorities need independent identity")
        verify(element("provider", 2).getAttribute("android:authorities") == "$FRIENDS_ONLY_PACKAGE.files", "Duplicate same-package authorities must map consistently")
        verify(element("permission").getAttribute("android:name") == "$FRIENDS_ONLY_PACKAGE.permission.SYSTEM_ONLY", "Permission declaration must match new app")
        verify(element("permission", 1).getAttribute("android:name") == "$FRIENDS_ONLY_PACKAGE.permission.RECEIVE_ADM_MESSAGE", "Relative custom permission must resolve consistently")
        verify(element("uses-permission").getAttribute("android:name") == "$FRIENDS_ONLY_PACKAGE.permission.SYSTEM_ONLY", "Uses-permission must match declaration")
        verify(element("uses-permission", 1).getAttribute("android:name") == "$FRIENDS_ONLY_PACKAGE.permission.RECEIVE_ADM_MESSAGE", "Relative uses-permission must match declaration")
        verify(element("uses-permission", 2).getAttribute("android:name") == "android.permission.INTERNET", "Platform permission must stay unchanged")
        verify(element("activity").getAttribute("android:permission") == "$FRIENDS_ONLY_PACKAGE.permission.SYSTEM_ONLY", "Component permission must match declaration")
        verify(element("provider", 1).getAttribute("android:readPermission") == "$FRIENDS_ONLY_PACKAGE.permission.SYSTEM_ONLY", "Provider read permission must match declaration")
        verify(element("provider", 1).getAttribute("android:writePermission") == "android.permission.WRITE_CONTACTS", "External write permission must stay unchanged")
        verify(element("service").getAttribute("android:permission") == "$SOURCE_PACKAGE.permission.CREATE_SHORTCUT", "External source-prefix permission must stay unchanged")
        verify(replacements[SOURCE_PACKAGE] == FRIENDS_ONLY_PACKAGE, "Exact package literal must map")
        verify(replacements["content://$SOURCE_PACKAGE.files/"] == "content://$FRIENDS_ONLY_PACKAGE.files/", "Exact trailing-slash provider URI must map")
        verify(replacements["content://com.instagram.family"] == "content://${FRIENDS_ONLY_PACKAGE}_com.instagram.family", "Exact external-prefix owned-provider URI must map")
        verify("content://com.facebook.external.query" !in replacements, "External query URI must stay unchanged")
        verify("com.instagram.main.MainActivity" !in replacements, "Class descriptor identities must stay unchanged")
        verify(element("application").getAttribute("android:icon") == "@drawable/friends_only_icon", "App needs its own icon")
        verify(element("activity").getAttribute("android:icon") == "@drawable/friends_only_icon", "Launcher overrides need their own icon")
        verify(runCatching { renameManifestIdentity(document(activity = ".MainActivity")) }.isFailure, "Relative components must fail closed")
        verify(runCatching { renameManifestIdentity(document(packageName = "unrelated.package")) }.isFailure, "Unexpected source package must fail closed")
        verify(runCatching { renameManifestIdentity(document(extra = "<provider android:name=\"com.instagram.BadProvider\" android:authorities=\"empty;;authority\" />")) }.isFailure, "Ambiguous provider authority must fail closed")
        println("App identity manifest checks: $assertions assertions passed")
    }
}
