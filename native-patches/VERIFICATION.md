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
- 37 Python tests covering APK validation and patch-result handling. Validator
  Android-tool responses are mocked; archive integrity cases use test fixtures.
  These tests do not validate a real Instagram signature or installation.
- Workflow YAML and embedded Python parsed locally; `git diff --check` passed.

Javac 21 reports warnings that Java 8 source/target support is obsolete; the
compilation succeeds. Generated reports are under `native-patches/reports/` and
are uploaded by the native workflow. Generated binaries and downloaded tools
are ignored by Git.

## Not run

No original Instagram APK was supplied. Consequently original APK validation,
fingerprint matching on Instagram, DEX rewriting/rebuilding of Instagram,
output signing/installation, and all device/account behavior are **unverified**.
No patched Instagram APK has been produced. No emulator or physical Android
device is attached to this cloud environment.

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
