# Native APK installation results

The latest real-phone test identifies a **signing identity conflict**, rather
than an unknown installation failure. On the Samsung Galaxy S24+ (SM-S926B),
Android 17 / SDK 37 / One UI 9, the v2 diagnostic helper returned:

```text
Status: FAILURE_CONFLICT (5)
Legacy status: -7
INSTALL_FAILED_UPDATE_INCOMPATIBLE: Existing package com.instagram.android signatures do not match newer version; ignoring!
```

Android will not accept our personal signing key as an update to Instagram's
`com.instagram.android` identity. The tool reported no Instagram record visible
in the current profile, and the user reported removing the official app and no
other-profile installation. The location/origin of Android's conflicting record
is unknown. This is not evidence that another profile copy exists.

## Alpha.3 uses its own application identity

Version **0.3.0-alpha.3** builds as **`dev.friendsonly.instagram`**, with the
**Friends Only** launcher name and our own icon. Its output version code is
**385806737**. Re-scoped app permissions and provider authorities are intended
to let it coexist with official Instagram under Android's normal checks.
There is no signature, authentication or integrity bypass.

The local bundle build passed 138 Python tests, 113 Java policy assertions and
26 Kotlin identity assertions. A signed alpha.3 APK was produced locally in cloud
and passed all static output validators. The [full GitHub APK workflow](https://github.com/fluffydisk/friends-only-instagram/actions/runs/39019337357)
also succeeded. Alpha.3 installation and launch remain unverified.

1. Download [the alpha.3 `friends-only-personal-test-apk-35` ZIP](https://github.com/fluffydisk/friends-only-instagram/actions/runs/39019337357/artifacts/12095918541)
   while signed into GitHub, and extract the new `friends-only-personal.apk`.
2. Open that APK through the phone's file manager and complete the normal
   Android confirmation. Allow the selected source to request installation if
   Android asks. Do not choose the older `friends-only-personal-test-apk-17` file.
3. Launch **Friends Only**. Test opening before testing account login, then
   report each feature separately: login, Home, Stories, videos, Direct/groups,
   and Home long-press filter settings.

**You do not need to remove official Instagram, delete profiles, or reset the
phone.** Official Instagram can remain installed, or be restored from Play
Store. Its local account data is separate from the new Friends Only package.
Instagram may still reject a modified client; successful Android installation
would not prove login or account functionality.

The current v2 diagnostic helper was built specifically for the older
`com.instagram.android` output. Use normal file-manager installation for
alpha.3; do not select the new package in that helper.

## Recorded installation history

| Output and tool | Real-phone result |
| --- | --- |
| Alpha.1, diagnostic v1 | `FAILURE_INVALID (4)`, legacy `-25`: version `385709029` older than recorded `385806735` |
| Alpha.2, diagnostic v1 | `FAILURE (1)`, legacy `-110`: `INSTALL_FAILED_INTERNAL_ERROR: Session files in use` |
| Alpha.2, file manager | About 30 seconds of installation, then generic not-installed dialog; no detailed code |
| Alpha.2, diagnostic v2 | `FAILURE_CONFLICT (5)`, legacy `-7`: `INSTALL_FAILED_UPDATE_INCOMPATIBLE`, signature mismatch |
| Alpha.3 | Not yet tested on the phone |

Alpha.2 raised the output version code to **385806736**, retaining strict source
validation at **385709029**. The v2 test used the exact alpha.2 CI APK:
**253,019,230 bytes**, SHA-256
`cc23681600c92ccf1b83fb55bb9fd2e7f6c263f99b758ff2e0ae9ddcf65a85a3`.
Session **1936897936** first reported `PENDING_USER_ACTION (-1)` and then the
final signature conflict, with **confirmation launch count 1**. The final report
showed **59,754,876,928** free internal bytes. Storage was ample.

V1 had a confirmation re-entry path. The v2 helper fixes that path with a
single persisted active session, one-shot confirmation and explicit
cancellation. Its 15 tests passed, lint reported zero errors and six warnings,
and its APK built in [run 39017587861](https://github.com/fluffydisk/friends-only-instagram/actions/runs/39017587861).
The user installed and ran v2 successfully. This does not establish why the
independent file-manager install produced its generic error; the v2 result does
establish a signing conflict for the selected alpha.2 APK.

## Sharing a useful problem report

Include the APK's workflow/run number, Android and One UI versions, exact steps,
and whether installation, opening, login or a later feature failed. A screenshot
of Android's dialog is useful; redact account names and messages. Do not send
passwords, two-factor codes, cookies, tokens or private chats. If a future helper
supports the new package, its copied result can provide Android's exact error.

The separate [diagnostics module](../install-diagnostics/README.md) has no
Internet permission, backend or account access. It sends nothing automatically;
copy/share occur only when the user presses those buttons. Android may install
a selected APK if it accepts it: the helper uses a real, user-confirmed request
and cannot force installation.

## What cloud checks establish

The earlier APKs passed signature, package/version/arm64, manifest and 16 KiB
alignment checks. Compressed native libraries are consistent with
`extractNativeLibs=true`. A confirmed APKEditor namespace-order defect is
normalized before patching/signing; that repair was not established as the
phone rejection's cause.

Cloud installation checks have not produced a PackageInstaller verdict: earlier
attempts failed on service readiness, emulator data space, and graphics crashes.
The exact alpha.3 CI APK was copied successfully in
[run 39019676627](https://github.com/fluffydisk/friends-only-instagram/actions/runs/39019676627),
after API 37/ARM64, readiness and ample-storage checks passed. Emulator graphics
crashes before and during installation killed services; ADB returned
`Broken pipe (32)` / exit 224. The modern SwiftShader renderer did not resolve
that emulator failure. No APK acceptance/rejection, app launch or login result
was obtained. [Smoke logs](https://github.com/fluffydisk/friends-only-instagram/actions/runs/39019676627/artifacts/12096526389)
record that failed infrastructure check. This is separate from the phone's
exact alpha.2 `-7` result; alpha.3's phone outcome is still unknown.
See [native verification](../native-patches/VERIFICATION.md) for the evidence
and limits.
