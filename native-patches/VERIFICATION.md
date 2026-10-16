# Native patch evidence · 2026-10-16

This module is a native-patching experiment, now `0.3.0-alpha.2`.
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
- Both named patches discovered by the actual engine, with the exact Instagram
  version in their compatibility metadata.
- 113 standalone Java policy assertions covering protected feed/Story/Direct
  routes, ordinary media, endpoint boundaries, unknown hosts and filter toggles.
- 115 Python tests covering APK/APKM validation, merge audits, version-code edits,
  namespace/ZIP headers and patch-result handling. Validator
  Android-tool responses are mocked; archive integrity cases use test fixtures.
  These tests do not validate a real Instagram signature or installation.
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
and displays Android's exact error. Its clean local build, six unit tests and
lint check passed. Namespace normalization has 20 regression cases among the
current 115 Python tests. A real unsigned fixture had two namespace END
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
checks. Download its
[`friends-only-personal-test-apk-17`](https://github.com/fluffydisk/friends-only-instagram/actions/runs/38968571288/artifacts/12075512879)
artifact and extract `friends-only-personal.apk`. Its independently signed
checksum/certificate is recorded in that run's `native-patch-reports-17` artifact.

The corrected candidate still requires a new phone installation attempt; a
version-code correction is not proof that signature, policy or runtime checks
will also succeed.

## Still not verified

No emulator or physical Android device is attached to this cloud environment.
Cold launch and all account behavior remain **unverified**. The attempted
software Android 35 emulator did not reach PackageManager readiness before the
cloud memory limit; it produced no APK installation result. A hosted Android 35
installation check is available as an explicit opt-in for manual native APK
builds. Its first hosted attempt failed while downloading the system image,
before any APK installation. The successful alpha.2 run did not request this
optional check and makes no emulator installation claim.

Login, challenges, native library loading, Home/Stories, ordinary video posts,
Direct sending, groups, filter behavior and settings must be tested on the
actual output. Instagram can reject a modified/re-signed client. This project
contains no signature-check, authentication or integrity bypass.

## First device check when a verified output exists

Keep the official app installed until the patched output is available and its
report passes. Review the same-package/different-signature installation and
local-data implications in the README before replacing it. Then record each
item separately:

1. Installation and cold launch; no account login required for initial crash check.
2. Native Instagram login/challenge; stop on platform rejection.
3. Home, Stories and normal photo/video posts; retain shared individual reels.
4. Existing Direct/group conversations and sending a test message manually.
5. Hidden tabs, Home long-press, each toggle, Show all and relaunch persistence.
6. Rotation, background/foreground, offline/online and navigation recovery.

When reporting issues, include APK/run identifier, Instagram version, S24+
Android/One UI version, steps and a screenshot with names/messages redacted.
Do not send passwords, two-factor codes, cookies, tokens or unredacted chats.
