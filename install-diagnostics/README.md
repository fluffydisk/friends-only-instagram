# Legacy Android installation diagnostic v2

**Use the normal file manager for current Friends Only APKs, not this helper.**
It supports only old `com.instagram.android` builds; alpha.3 and later use
`dev.friendsonly.instagram`. The helper's legacy -2 package-hint mismatch does
not diagnose normal installation or startup.

<!-- ALPHA8_DELIVERY_START -->
The [current native alpha.8 APK](https://github.com/fluffydisk/friends-only-instagram/actions/runs/39114270905/artifacts/12132185041) (code **385806742**) repeats inline feed
videos without the supported **View in Reels / Watch again** end card. Its local
and [GitHub builds](https://github.com/fluffydisk/friends-only-instagram/actions/runs/39114270905) passed ten patches, 163 Python tests and 615 Java/Kotlin
assertions; phone playback remains unverified. Install the extracted APK with
the normal file manager. Remove only an older Friends Only app for the changed
signing key (local session/settings are lost). **This old helper is incompatible
with the own-package APK and must not be used for its installation.**
<!-- ALPHA8_DELIVERY_END -->

**[Download the current alpha.7 APK](https://github.com/fluffydisk/friends-only-instagram/actions/runs/39081645229/artifacts/12119595829)** and extract
`friends-only-personal.apk`. Both its local and GitHub builds passed nine
patches, **163 Python tests and 558 Java/Kotlin assertions**, signatures,
identity, alignment and the mandatory emitted-theme guard. Version code:
**385806741**. **Alpha.7 phone behavior is unverified.** Use Files to install
it; this legacy helper is incompatible with its package.

The user confirms alpha.6 opens on their Galaxy S24+ / Android 17 / One UI 9,
while a Home swipe still reaches Reels. Alpha.7 addresses navigation, own
settings and in-feed hold-to-2× behavior. The [alpha.6 fallback APK](https://github.com/fluffydisk/friends-only-instagram/actions/runs/39064660107/artifacts/12112951982)
remains historical known-to-open evidence. Alpha.5 is withdrawn after normal
installation followed by immediate closing; do not reinstall artifact 55.

For a new-key native build, remove only the older **Friends Only** app first,
clearing its local session/settings, then extract/install the APK from Files.
Official Instagram does not need removal and the phone does not need resetting.
See [current troubleshooting](../docs/INSTALLATION_TROUBLESHOOTING.md) and
[the native guide](../native-patches/README.md) for current downloads and checks.

[Download the v2 diagnostics APK](https://github.com/fluffydisk/friends-only-instagram/actions/runs/39017587861/artifacts/12094909613)
from the successful cloud build. Sign into GitHub, extract the ZIP and install
`install-diagnostics-debug.apk`. Version **0.2.0** emits **diagnostic v2** reports;
15 unit tests, lint and APK compilation passed in
[GitHub Actions](https://github.com/fluffydisk/friends-only-instagram/actions/runs/39017587861).
The user installed and ran v2 on a Galaxy S24+ (SM-S926B), Android 17 / One UI 9.
For the exact alpha.2 APK, it returned a final **FAILURE_CONFLICT (5), legacy -7**:
`INSTALL_FAILED_UPDATE_INCOMPATIBLE`, because the existing
`com.instagram.android` signing identity does not match our key. It recorded
one confirmation launch in session 1936897936, first pending then finished.
The earlier v1 tool returned downgrade (-25) and Session files in use (-110)
errors. V2's CI results were 15 tests, zero failures/ignored; lint zero errors,
six warnings; and a successful APK build.

Clean CI builds use different disposable debug signing keys. Remove the older
**Friends Only Install Test / Friends Only Kurulum Testi** before installing this
new tool if it is already installed. Removing this offline helper clears its
selected-file permission and local report; it contains no Instagram account data.

**This v2 helper targets the older `com.instagram.android` test APKs.** Alpha.3
uses `dev.friendsonly.instagram`; install it through the normal file manager
rather than this helper. The package displayed by v2 is fixed to the older
identity and is not a generic APK identity detector. The user selected alpha.3
in v2 and got legacy **-2**, because the APK's real package did not match its
`com.instagram.android` session hint. The selected file's SHA-256 matched the
correct alpha.3 CI output,
`ab5d7814d353fb5d4f3541aa8b1655ee8ea656aeb971a24a97bcd7d046daa6c9`.
That is a tool compatibility error, not evidence that alpha.3 cannot install:
the normal file-manager installation succeeded.

This small, separate app (`dev.friendsonly.installdiagnostics`) requests an ordinary, user-confirmed Android installation of a selected `com.instagram.android` APK. It captures the real `PackageInstaller` result when Samsung’s file manager shows only “App not installed”. It does not patch APKs, bypass security, silently install apps, use ADB/root, or access an Instagram account. It has no Internet permission, backend or analytics.

The following historical test steps apply **only to a `com.instagram.android`
APK**, such as alpha.1/alpha.2:

1. Install and open **Friends Only Install Test** (Turkish: **Friends Only Kurulum Testi**).
2. Choose a compatible old `friends-only-personal.apk` file, not its ZIP/APKM archive; do not choose alpha.3 or later.
3. Press **Test installation**. If Android asks, allow installation from this diagnostic app only, then return and press the button again.
4. Confirm Android’s normal installation dialog.
5. Return to this app. Press **Copy result** or **Share result** to send the diagnostic yourself. Nothing is sent automatically.

The report contains Android/device versions, numeric installation status, any legacy Android error code, the installer’s error text, and a conflicting package name if Android provides one. It contains no account data, chats, passwords, cookies or logcat. File selection uses Android’s document picker; the file is streamed into a private Android installation session with a 1 GiB limit. No broad storage access is requested. The selected document permission and last report stay on the phone until app data is cleared or this tool is uninstalled.

The app does not require changing general device protections. Its permission to request installations can be revoked in Android settings after testing. It may install Instagram successfully if Android accepts the supplied APK; “pending user action” is explicitly shown as pending, never as success. Cloud/JVM checks do not constitute a physical-device installation test.

Version 2 keeps one installation active until a final result or explicit
cancellation. The confirmation Intent is consumed once; repeated callbacks,
button taps and process restoration cannot replay it. If the app process loses
a pending confirmation, **Cancel current test** releases this tool's owned
session before another test. A genuine Android terminal result wins a race with
cancellation. These changes fix a real re-entry path in the tool; they are not
proof that this caused the phone's independent file-manager failure.

The copied report adds session ID, phase, copied bytes, source APK SHA-256 and
confirmation launch count, without recording the selected URI. The alpha.2 APK
from `friends-only-personal-test-apk-17` is **253,019,230 bytes** and has SHA-256
`cc23681600c92ccf1b83fb55bb9fd2e7f6c263f99b758ff2e0ae9ddcf65a85a3`.
The phone report matches both values. Its final free-storage reading was
59,754,876,928 bytes, and its current-profile Instagram query was absent.
Android still rejected the signing identity; the retained record's location is
unknown. No other-profile copy is inferred from this result.

Build with `./gradlew :install-diagnostics:assembleDebug :install-diagnostics:testDebugUnitTest :install-diagnostics:lintDebug` using the repository’s JDK 17 / Android SDK 35 setup.
