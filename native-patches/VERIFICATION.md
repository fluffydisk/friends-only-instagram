# Native patch evidence · 2026-10-16

This module is an initial native-patching experiment, version `0.3.0-alpha.1`.
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
- 81 Python tests covering APK/APKM validation, merge audits and patch-result handling. Validator
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

## Not run

No emulator or physical Android device is attached to this cloud environment.
Installation, cold launch and all device/account behavior remain **unverified**.

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
