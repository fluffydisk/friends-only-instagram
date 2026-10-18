# Verification record

Date: **2026-10-16 UTC**. This record distinguishes compiler/fixture evidence
from account and device behavior. No physical Android device or emulator was
available, and no Instagram credentials were entered.

## Public website reachability

A lightweight public HTTP HEAD check, with redirects and without user-agent
spoofing, was performed at approximately 13:37 UTC:

| URL | Observed result |
| --- | --- |
| `https://www.instagram.com/` | HTTP 200 |
| `https://www.instagram.com/direct/inbox/` | HTTP 302 to Instagram login, followed by HTTP 429 |

No explicit cloud destination-policy denial was observed. These results show
public HTTP reachability only. A command-line HTTP response does **not** prove
JavaScript rendering, Android WebView compatibility, successful login, or access
to authenticated content. No further attempts to work around the 429 were made.

## Build/check results

| Check | Actual result |
| --- | --- |
| `npm ci --ignore-scripts --no-audit --no-fund` in `tools/filter-tests` | Passed |
| `npm test` in `tools/filter-tests` | 19/19 offline DOM fixtures passed |
| `./gradlew --no-daemon testDebugUnitTest lintDebug` | Passed; 5/5 JVM tests, zero lint errors |
| `./gradlew --no-daemon assembleDebug` | Passed; installable debug APK generated |
| Android Build Tools `apksigner verify` | Passed; APK v2 signature valid |
| Android Build Tools `aapt dump badging/permissions` | Correct package/version, API26 minimum, API35 target, INTERNET only |
| Embedded `assets/filters.js` byte comparison | Matches final source exactly |

Lint reports one nonblocking `UnusedAttribute` warning for
`enableOnBackInvokedCallback`, which is supported on API33+ and ignored on older
Android versions. The Activity implements legacy back handling for older
versions. An earlier API-level theme error was fixed and the checks rerun.

APK: `app/build/outputs/apk/debug/app-debug.apk` (**861,865 bytes**).
The delivery copy is named `friends-only-0.1.0-debug.apk`.

```text
APK SHA-256:
f2a250df4853d8b58098a8c9050b64872ef48616e796c7f4ea04d9451be194f6

Embedded filter source SHA-256:
561d28d85a042a63b47681c0abec5f44bbb7a4e8c4976f7e9750ec0e813a43e8
```

The 5 JVM tests cover trusted/external/unsafe URLs, restricted injection hosts,
and Direct selection. The 19 offline DOM tests cover navigation precision,
ordinary videos/Stories/Direct preservation, explicit suggestion structures,
dynamic mutations, reversible toggles, idempotence, batching, text-observation
scope, cleanup, and early document injection. These use constructed fixtures,
not a captured authenticated Instagram page. No device/instrumented tests ran.

The build used official Gradle 8.11.1, AGP 8.9.2, Kotlin Android plugin 2.1.20,
Temurin JDK 17.0.20.1, Android platform 35, and Build Tools 35.0.0. Temporary
cloud tools are installed under `/tmp/friends-only-tools`; this session can
rebuild with:

```sh
. /tmp/friends-only-tools/environment.sh
cd /workspace/friends-only
./gradlew --no-daemon testDebugUnitTest lintDebug assembleDebug
```

That temporary script configures this environment’s tools, proxy, and trusted
certificate store. It is not part of the portable project and contains no
required application configuration. Future workspaces should use the README
toolchain instructions or the complete GitHub Actions workflow.

## Account/device evidence

| Capability | Status |
| --- | --- |
| Real Instagram WebView rendering | Unverified; APK has not been launched on Android |
| Login, 2FA, challenges | Unverified |
| Persistent logged-in session | Configured, unverified on device |
| Home feed and Following option/order | Unverified |
| Stories / reactions / ordinary videos | Unverified |
| Direct text sending and receipt | Unverified |
| Existing group conversations | Unverified |
| Filtering Instagram’s current authenticated DOM | Unverified; offline fixtures only |
| S24+ insets, keyboard, back, rotation, theme, media | Implemented, unverified on device |
| GitHub Actions workflow execution | Initial local delivery did not run Actions; see the repository Actions page for current status |

See [DEVICE_TESTING.md](DEVICE_TESTING.md) for the next verification step.

## Native design 0.4.0-alpha.1 — 18 October 2026

Source: `d21cbf68ce53ac27e2e33e28ba62f6cfa0192e5b` on
`feature/native-apk-patches`. The [implementation scope and device checklist](REDESIGN_IMPLEMENTATION.md)
distinguish our own welcome/settings screens from scoped presentation of the
original native feed/Direct/profile components. This is not the full planned
Stories list/profile/chat layout replacement.

Local checks passed: **167 Python tests, 389 Java and 508 Kotlin assertions
(897 total)**. The additional audit of the actual original and emitted APK
passed **70 assertions including the reused policy fixtures**. All 11 selected
patches applied, the APK signed/aligned successfully, and the existing mandatory
7-style / 47-reference / 14-color theme guard passed. Both offline font/license
assets passed pinned final-output digest checks. Welcome/settings classes exist
in the emitted DEX; the output manifest has a declared, enabled launcher target.

The independent `native-patches/scripts/DesignDexAudit.java` compared **861,788
native methods** against the **local alpha.8 APK**. Only the expected two
resource-ID inflaters and one feed-action-holder constructor changed. All **3,166
original assets and 18 native libraries** from that baseline were preserved.
This is an instruction/metadata and bundled-file comparison, not an ART or phone
rendering test. Audit with the pinned Morphe CLI on the Java classpath:

```sh
javac -cp native-patches/.tools/morphe-desktop-1.18.1-all.jar \
  -d /tmp/friends-only-design-audit native-patches/scripts/DesignDexAudit.java
java -Xmx3g -cp /tmp/friends-only-design-audit:native-patches/.tools/morphe-desktop-1.18.1-all.jar \
  DesignDexAudit /path/to/local-alpha8.apk /path/to/design-alpha1.apk
```

Local output SHA-256: `58741552bd80184c19389a5588c0b59b0648ebe324cbec9eaddba2c6728c27aa`;
size **252,892,566 bytes**. This local signature is separate from the CI signature.
Machine-readable evidence: [native-design-2026-10-18.json](evidence/native-design-2026-10-18.json).

**No phone test has been run for 0.4.0-alpha.1.** Opening, welcome handoff,
authentication, actual appearance/coverage, Story reply, individual/group Direct,
following-only coverage, video gestures/loop and accessibility remain unverified.
Historical success opening alpha.6 does not verify this changed launcher/design.

GitHub [run 78](https://github.com/fluffydisk/friends-only-instagram/actions/runs/39120305766) succeeded on the same source revision, with the same
**167 Python tests / 897 Java+Kotlin assertions / 11 applied patches** confirmed
from its completed logs. [APK artifact 12133549996](https://github.com/fluffydisk/friends-only-instagram/actions/runs/39120305766/artifacts/12133549996) is
`friends-only-personal-test-apk-78`, containing `friends-only-personal.apk`. Its
**ZIP artifact digest**, reported by GitHub, is
`sha256:1a8541166a6bde36f1602d867a8d478fc3ed1b4915bc735b9f1cdbbb5fe8351d`
(228,139,589 ZIP bytes); this is not the contained APK's SHA-256. The report
artifact was published too, but its download redirect returned HTTP 403 from
this execution environment, so no CI APK file hash or signer was copied from it.
The local APK hash above is explicitly separate. No emulator or physical-phone
check ran in this workflow.
