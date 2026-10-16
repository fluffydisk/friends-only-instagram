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
