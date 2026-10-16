# Native APK installation failure

The first native APK passed cloud compilation, signature, alignment and package
checks, but the user reported **App nicht installiert** on a Samsung S24+ with
**Android 17 / One UI 9**. They reported removing the original Instagram and no
other profile copy. That generic dialog does not contain Android's failure code.
Installation on that device is failed, not verified successful.

The diagnostics app subsequently returned the exact failure on the phone:

```text
Status: FAILURE_INVALID (4)
Legacy status: -25
INSTALL_FAILED_VERSION_DOWNGRADE: Downgrade detected:
Update version code 385709029 is older than current 385806735
```

The tool reported no Instagram package in the current profile and ample free
storage; Android nevertheless compared the installation to version 385806735.
The location/origin of that retained version record has not been established.
No additional profile deletion or application removal is needed to diagnose it.

Version `0.3.0-alpha.2` assigns our personally rebuilt variant version code
**385806736**, while preserving source validation at **385709029**. The version
integer is edited on the unsigned output before alignment/signing; Android's
normal version and signature rules still apply. A later signature or policy
error could still be reported, so the corrected candidate needs another phone
test. Download the
[corrected alpha.2 artifact](https://github.com/fluffydisk/friends-only-instagram/actions/runs/38968571288/artifacts/12075512879),
extract `friends-only-personal.apk`, and select that newly downloaded file in the
diagnostic tool, rather than the old file. Its cloud build and static checks
passed; the second phone result is pending.

## Get the exact error without a computer

The separate [install-diagnostics module](../install-diagnostics/README.md)
builds **Friends Only Install Test / Friends Only Kurulum Testi**. Its package
is `dev.friendsonly.installdiagnostics`; it can coexist with both Instagram and
the earlier WebView app. It has no Internet permission, backend or account access.

1. Download its APK from the **Install diagnostics APK** workflow artifact and
   extract the ZIP. Install `install-diagnostics-debug.apk`.
2. Open it and choose the already downloaded `friends-only-personal.apk` using
   **Choose APK / APK seç**.
3. Choose **Test installation / Kurulumu test et**. If Android asks, allow this
   tool to request installations through the normal per-app source setting.
4. Confirm the normal Android installation dialog, then return to the tool.
5. Use **Copy result / Sonucu kopyala** and paste the result into this chat.

The report includes Android/model, free internal storage, the Instagram package
record visible in the current profile, and Android's status/message/legacy code.
On newer Android versions it also includes developer-verification failure extras
when the system provides them. It reads no passwords, chats, tokens or account
data. Copy/share happen only after pressing the corresponding button.

The test is a real installation request: Android may install the selected APK if
it accepts it. The tool does not force installation or bypass any system check.
If the diagnostics APK itself cannot install, report that fact; do not keep
removing other apps or resetting the phone. The official Instagram can be
restored through Play Store while investigating this prototype.

## What the cloud checks establish

The first APK has a valid v3 personal signature, arm64-v8a support, min SDK 28,
target SDK 36, a stored/aligned resources.arsc, and no residual split requirement.
Compressed native libraries are consistent with extractNativeLibs=true.
These checks do not establish installation compatibility with One UI 9.

APKEditor also emitted namespace closing chunks in the wrong order. Source now
normalizes only those adjacent closing events before patching/signing and checks
the final manifest. The actual merged fixture loses all AAPT namespace warnings
while retaining every other ZIP payload. This is a confirmed packaging defect;
it is **not established as the cause** of the phone's installation rejection.

The cloud has no KVM. A bounded software emulator attempt did not reach a ready
PackageManager before the memory limit, so it produced no APK installation result.
The manual native workflow has an optional hosted emulator install check;
that is separate from Android 17 / Samsung testing and performs no login.
Its first run failed downloading the Google system image before an APK install
could be attempted. A smoke-check failure remains visible; static validation
and the candidate artifact are independent of that optional infrastructure.
