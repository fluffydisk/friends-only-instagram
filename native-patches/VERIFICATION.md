# Native patch evidence · updated 2026-10-17

This module is a native-patching experiment, now `0.3.0-alpha.3`.
It targets only Instagram `451.0.0.20.80`; the upstream project's support claim
is not evidence that our modified patches match that APK.

## Checks actually run in cloud

Command, with JDK 21 and Android SDK 35 installed:

```sh
python3 native-patches/scripts/build.py --check
```

Passed:

- Java runtime compilation against Android 35, targeting Java 8 bytecode.
- D8 native extension generation, minimum API 23.
- Kotlin 2.4.21 patch compilation against the pinned Morphe 1.18.1 CLI.
- Three named patches discovered by the actual engine, with the exact Instagram
  version in their compatibility metadata: native controls, discovery blocking
  and app identity.
- 113 standalone Java policy assertions covering protected feed/Story/Direct
  routes, ordinary media, endpoint boundaries, unknown hosts and filter toggles.
- 138 Python tests covering APK/APKM validation, merge audits, version-code edits,
  namespace/ZIP headers, output identity and patch-result handling. Validator
  Android-tool responses are mocked; archive integrity cases use test fixtures.
  These tests do not validate a real Instagram signature or installation.
- 26 Kotlin app-identity assertions, including owned permission references,
  provider authorities, relative components and preservation of external names.
- Workflow YAML and embedded Python parsed locally; `git diff --check` passed.

The [GitHub Actions native build](https://github.com/fluffydisk/friends-only-instagram/actions/runs/38953816945)
also passed on commit `6314530`, including the same 113 assertions and 37 tests.
Its `friends-only-native-patches-4` artifact contains the `.mpp` bundle, not an APK.

Javac 21 reports warnings that Java 8 source/target support is obsolete; the
compilation succeeds. Generated reports are under `native-patches/reports/` and
are uploaded by the native workflow. Generated binaries and downloaded tools
are ignored by Git.

## Actual APKM application

The user supplied an APKM via the repository's `instagram-input` release.
Its SHA-256 is `7f1dc7549a3d970124983935e977447de6d3dee41e7334278d0821b5472089ad`.
All 11 APKs passed signature verification and matched package/version/build
`com.instagram.android / 451.0.0.20.80 / 385709029`, with arm64-v8a libraries.
Density splits correctly inherit an absent versionName from the verified base.

The active signer `3a10c50c18ba937506c2875e9047be74f8fd8b86fae2ff9feaa50a81fcb4c014`
and its older platform-range signer are independently published at Instagram's
HTTPS `/.well-known/assetlinks.json`. Source stamp certificates are excluded from
the publisher check. Cryptographic verification runs over the full supported SDK range.

Passed on the actual input:

- APKEditor 1.4.9 merge; exact preservation of 23 DEX payloads, 18 native
  libraries and 3,166 assets. Manifest/resources are rebuilt by the merger.
- Unique native tab-bar fingerprint, identified by the verified `tab_bar` and
  `tab_bar_shadow` resource constants. Ambiguous matches stop the build.
- Unique Tigon URI hook inside the existing IOException handler.
- Both Friends Only patches, full DEX compilation and APK rebuilding.
- Personal test signing, apksigner verification, package/version/arm64 checks,
  extension presence and 16 KiB ZIP alignment checks.

The workspace output is `friends-only-native-0.3.0-alpha.1.apk`, 256,525,288 bytes.
Its SHA-256 is `bff216eae46dab1a0843d4398d367e68ff3d41cd26a2d52e8df5bffb5fbd6c36`.
The complete public metadata record is [native-apkm-2026-10-16.json](evidence/native-apkm-2026-10-16.json).
This build used a disposable personal signing key. Separate clean builds use
different keys unless stable signing secrets are configured; their APK hashes
and certificates differ. The original Meta signature cannot sign our output.

The full [GitHub Actions manual APK build](https://github.com/fluffydisk/friends-only-instagram/actions/runs/38962328897)
also passed on source commit `07096ab`, including all 113 Java assertions and 81
Python tests, original APKM validation, merge, both patches, rebuilding, signing,
static output checks and APK artifact upload. Its artifact is
[`friends-only-personal-test-apk-9`](https://github.com/fluffydisk/friends-only-instagram/actions/runs/38962328897/artifacts/12073995063).
That independently signed CI output has its own checksum/certificate in the
run's `native-patch-reports-9` artifact; the workspace checksum above refers only
to the workspace build. Neither successful build establishes device behavior.

## Device installation report

After the first APK delivery, the user reported **App nicht installiert** on
their Samsung S24+ with **Android 17 / One UI 9**, after removing the original
Instagram and with no other profile copy reported. Installation on that device
is a known reported failure. The initial generic screenshot did not identify the
PackageInstaller failure code; the diagnostics result below established a
version downgrade. It has not been reproduced in cloud.

The separate install-diagnostics app requests a normal user-confirmed installation
and displays Android's exact error. The initial v1 app's clean local build, six
unit tests and lint check passed. Namespace normalization has 20 regression cases among the
then-current 115 Python tests. A real unsigned fixture had two namespace END
chunks reordered; all other 31,599 payloads remained identical. This packaging
fix is not established as the Samsung install failure's cause.

See [installation troubleshooting](../docs/INSTALLATION_TROUBLESHOOTING.md).

## Alpha.2 version-code correction

The diagnostics tool ran on the phone and returned
`INSTALL_FAILED_VERSION_DOWNGRADE` / legacy status `-25`: original output
`385709029` was older than Android's recorded `385806735`.

The source is still verified as Instagram version code **385709029**. After the
two bytecode patches, the unsigned variant receives its own version code
**385806736**, before zip alignment and personal signing. Source versionName,
resources, native libraries and authentication code are unchanged by this
metadata edit. The parser checks a unique root Android integer field and refuses
ambiguous/major-version or already signed inputs.

The corrected APK was built in cloud and passed package/version/arm64, namespace,
signature and 16 KiB alignment checks. ZIP rewrites use classic local headers;
a regression check rejects ZIP64 local sizes that Android's native reader cannot
handle. All 115 Python tests and 113 Java policy assertions passed.
See [alpha.2 evidence](evidence/native-alpha2-2026-10-16.json) for its exact
workspace checksum, version-code edit and payload audit. That checksum is for
the workspace build; independently signed CI builds have their own report.

The [alpha.2 GitHub Actions build](https://github.com/fluffydisk/friends-only-instagram/actions/runs/38968571288)
also passed on commit `5864c32ef06aed67905344c2b6583f8393765ef1`, including all
115 Python tests and 113 Java assertions, validation/merge of the supplied APKM,
both patches, version code **385806736**, rebuilding, signing and static output
checks. Its historical
[`friends-only-personal-test-apk-17`](https://github.com/fluffydisk/friends-only-instagram/actions/runs/38968571288/artifacts/12075512879)
artifact contains `friends-only-personal.apk`; this same-package build is known
to fail on the phone and is not the current installation recommendation.
Its independently signed checksum/certificate is recorded in that run's
`native-patch-reports-17` artifact.

The user tried alpha.2 on **SM-S926B, Android 17 / SDK 37 / One UI 9**. The tool
reported **59,559,583,744 bytes** free and no Instagram record visible in the
current profile, then returned **FAILURE (1), legacy -110**:

```text
INSTALL_FAILED_INTERNAL_ERROR: INSTALL_FAILED_INTERNAL_ERROR: Session files in use
```

Opening the APK directly in the file manager also reportedly failed after about
30 seconds, with a generic not-installed message and no detailed code. Phone
installation remains failed; launch/account behavior has not been tested.

## Diagnostic v2: confirmed same-package signature conflict

The first diagnostics version had a re-entry path: a previous confirmation
could reopen while another install request was active. Diagnostics **0.2.0**
fixes that path with a persisted session guard, one-shot confirmation and
explicit cancellation. Its [CI build](https://github.com/fluffydisk/friends-only-instagram/actions/runs/39017587861)
passed **15 unit tests, zero failures/ignored**, lint **zero errors, six warnings**,
and APK compilation. The [v2 helper](https://github.com/fluffydisk/friends-only-instagram/actions/runs/39017587861/artifacts/12094909613)
was then installed and run successfully on the actual phone.

The user tested the exact alpha.2 CI file: **253,019,230 bytes**, SHA-256
`cc23681600c92ccf1b83fb55bb9fd2e7f6c263f99b758ff2e0ae9ddcf65a85a3`.
V2 returned pending user action and then a final result for the **same session
1936897936**, with **confirmation launch count 1**. The final report was:

```text
Device: samsung SM-S926B
Android: 17 (SDK 37)
Free internal bytes: 59754876928
Instagram record in current profile: absent
Package: com.instagram.android
Status: FAILURE_CONFLICT (5)
Legacy status: -7
Android message: INSTALL_FAILED_UPDATE_INCOMPATIBLE: Existing package com.instagram.android signatures do not match newer version; ignoring!
Phase: FINISHED
```

This establishes Android's same-package signing conflict for the selected APK.
The earlier `Session files in use` result is historical; it does not establish
the cause of the separate generic file-manager failure. The current-profile
query remains absent. Android nevertheless has a conflicting package identity;
its location/origin is unknown. No other-profile copy is asserted, and further
removal of official Instagram or profile data is not the proposed remedy.

## Alpha.3: separate Friends Only application identity

Alpha.3 adds a third patch, **Friends Only app identity**. Its output package is
**`dev.friendsonly.instagram`**, with **Friends Only** launcher labels and our
own vector icon. The source must still pass exact verification as Instagram
`com.instagram.android / 451.0.0.20.80 / 385709029`. The output version code is
**385806737**, set on the unsigned file before alignment and personal signing.

The identity patch re-scopes four app-defined permissions and the 23 authority
identities across 24 declared providers, including exact matching DEX strings.
External package queries and external permissions retain their names. Component
class names, JNI paths and native libraries are retained. This uses Android's
normal separate-package installation rules; no signature, authentication or
integrity bypass is introduced. Coexistence is intended, not yet phone-tested.
The patch does not provide a complete replacement of Instagram's native UI.

The local full bundle build passed **138 Python tests, 113 Java policy assertions
and 26 Kotlin identity assertions**. Applying all three patches to the actual
verified input produced a personally signed alpha.3 APK. All local static output
validators passed, recorded in `reports/alpha3-local-static-report.json`.

Full resource rebuilding exposed two validation/packaging issues before delivery:
AAPT 35 omits a valid source launcher alias from its badging output, so output
validation now requires strict matching proof from the manifest. The rebuild
also reintroduced two out-of-order namespace END chunks. The pipeline now
normalizes the **unsigned post-patch** manifest before version-code editing,
alignment and signing. Neither issue was bypassed or delivered as a successful
APK. The first alpha.3 [CI run](https://github.com/fluffydisk/friends-only-instagram/actions/runs/39018473490)
failed validation; the corrected [GitHub APK build](https://github.com/fluffydisk/friends-only-instagram/actions/runs/39019337357)
on source commit `5cb46fa1960237cf91e5ca36f81a91a8e9222b7b` succeeded. It ran
**137 Python tests**, 113 Java policy assertions and 26 Kotlin identity
assertions, verified the original APKM, merged it, applied all three patches,
rebuilt/signed the APK and passed static output checks. Its downloadable
[`friends-only-personal-test-apk-35`](https://github.com/fluffydisk/friends-only-instagram/actions/runs/39019337357/artifacts/12095918541)
artifact contains `friends-only-personal.apk`; its independent CI signing key
and checksum differ from the local build. The workflow's
`native-patch-reports-35` artifact contains its actual metadata. The later local
138-test count includes one additional version-code regression not present in
that CI commit.

The final local APK independently passed a read-only package audit: all **747
component records**, **18 native libraries** and **3,166 assets** were preserved;
all **164 scoped DEX instruction references** were consistently mapped. Launcher
alias enablement/targets were preserved, the package/resource table and launcher
label/icon match Friends Only, and final signature, namespace order and 16 KiB
alignment passed. [Alpha.3 evidence](evidence/native-alpha3-2026-10-17.json)
records the exact local file hash and audit. This is static evidence; no
alpha.3 device installation, launch or account behavior has been verified.

## Cloud installation attempts

These attempts are separate from the user's phone and none produced an Android
PackageInstaller verdict for the APK:

- The first API 37 [attempt](https://github.com/fluffydisk/friends-only-instagram/actions/runs/38971499479)
  fetched the exact alpha.2 CI artifact, but input/settings services were not
  ready; the emulator action exited before the install script ran.
- The [readiness-corrected attempt](https://github.com/fluffydisk/friends-only-instagram/actions/runs/39017434370)
  reached live services and passed SDK/ABI checks, then ADB ran out of space
  copying into the small emulator data partition. The phone had ample storage;
  this was a cloud emulator failure only.
- With larger userdata, [run 39018011441](https://github.com/fluffydisk/friends-only-instagram/actions/runs/39018011441)
  encountered graphics crashes before/during ADB installation, without a
  PackageInstaller result. Its [smoke report](https://github.com/fluffydisk/friends-only-instagram/actions/runs/39018011441/artifacts/12094864020)
  records that infrastructure failure.
- The exact alpha.3 CI artifact was tested in [run 39019676627](https://github.com/fluffydisk/friends-only-instagram/actions/runs/39019676627).
  API 37/ARM64, service readiness, **7,346,233,344 bytes free** and copied-APK
  checks passed. The copied file was **253,154,478 bytes**, SHA-256
  `ab5d7814d353fb5d4f3541aa8b1655ee8ea656aeb971a24a97bcd7d046daa6c9`.
  SurfaceFlinger asserted on `hasReadColorBufferDma` before installation and
  again during installation, killing system services. ADB returned
  **Broken pipe (32), exit 224**. The emulator's modern SwiftShader renderer
  (37.2.12) did not resolve this graphics failure. The
  [smoke evidence](https://github.com/fluffydisk/friends-only-instagram/actions/runs/39019676627/artifacts/12096526389)
  contains logs; there was **no PackageManager installation verdict** and no
  Instagram launch/login attempt. This is a failed cloud infrastructure check,
  not evidence that Android accepted or rejected the APK.

## Still not verified

No physical Android device is attached to this cloud environment. Native
installation has failed on the user's phone for alpha.1/alpha.2; alpha.3 has not
yet been tested there. Cold launch and all native account behavior remain
**unverified**. An earlier software Android 35 emulator did not reach
PackageManager readiness within the cloud memory limit. An opt-in hosted
Android 35 attempt failed while downloading its system image. Neither produced
an APK installation result. The latest alpha.3 hosted API 37 attempt also
failed in emulator graphics infrastructure, without a PackageManager verdict.
Successful static builds alone establish no device or emulator installation
compatibility.

Login, challenges, native library loading, Home/Stories, ordinary video posts,
Direct sending, groups, filter behavior and settings must be tested on the
actual output. Instagram can reject a modified/re-signed client. This project
contains no signature-check, authentication or integrity bypass.

## Test the alpha.3 APK on a phone

Keep official Instagram installed, or restore it through Play Store if desired.
Download the [verified alpha.3 artifact](https://github.com/fluffydisk/friends-only-instagram/actions/runs/39019337357/artifacts/12095918541),
extract `friends-only-personal.apk` and install it through the normal file-manager
flow. The existing v2 diagnostic helper targets
the old `com.instagram.android` output; it should not be used for alpha.3.
Record each item separately:

1. Installation and cold launch; no account login required for initial crash check.
2. Native Instagram login/challenge; stop on platform rejection.
3. Home, Stories and normal photo/video posts; retain shared individual reels.
4. Existing Direct/group conversations and sending a test message manually.
5. Hidden tabs, Home long-press, each toggle, Show all and relaunch persistence.
6. Rotation, background/foreground, offline/online and navigation recovery.

When reporting issues, include APK/run identifier, Instagram version, S24+
Android/One UI version, steps and a screenshot with names/messages redacted.
Do not send passwords, two-factor codes, cookies, tokens or unredacted chats.
