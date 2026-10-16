# Local Android installation diagnostic

This small, separate app (`dev.friendsonly.installdiagnostics`) requests an ordinary, user-confirmed Android installation of a selected `com.instagram.android` APK. It captures the real `PackageInstaller` result when Samsung’s file manager shows only “App not installed”. It does not patch APKs, bypass security, silently install apps, use ADB/root, or access an Instagram account. It has no Internet permission, backend or analytics.

1. Install and open **Friends Only Install Test** (Turkish: **Friends Only Kurulum Testi**).
2. Choose the downloaded `friends-only-personal.apk` file, not its ZIP/APKM archive.
3. Press **Test installation**. If Android asks, allow installation from this diagnostic app only, then return and press the button again.
4. Confirm Android’s normal installation dialog.
5. Return to this app. Press **Copy result** or **Share result** to send the diagnostic yourself. Nothing is sent automatically.

The report contains Android/device versions, numeric installation status, any legacy Android error code, the installer’s error text, and a conflicting package name if Android provides one. It contains no account data, chats, passwords, cookies or logcat. File selection uses Android’s document picker; the file is streamed into a private Android installation session with a 1 GiB limit. No broad storage access is requested. The selected document permission and last report stay on the phone until app data is cleared or this tool is uninstalled.

The app does not require changing general device protections. Its permission to request installations can be revoked in Android settings after testing. It may install Instagram successfully if Android accepts the supplied APK; “pending user action” is explicitly shown as pending, never as success. Cloud/JVM checks do not constitute a physical-device installation test.

Build with `./gradlew :install-diagnostics:assembleDebug :install-diagnostics:testDebugUnitTest :install-diagnostics:lintDebug` using the repository’s JDK 17 / Android SDK 35 setup.
