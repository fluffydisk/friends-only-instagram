# Native APK installation and phone testing

**The user now confirms alpha.6 opens on the Galaxy S24+ (SM-S926B), Android 17 /
SDK 37 / One UI 9.** Its reported defect is that a Home swipe still enters
Reels. Alpha.7 addresses that native navigation path and simplifies Settings;
its phone behavior is not yet verified. Opening is separate from Story
playback, Direct/groups, login/challenges and following-filter success.

<!-- ALPHA8_DELIVERY_START -->
**[Download alpha.8 APK](https://github.com/fluffydisk/friends-only-instagram/actions/runs/39114270905/artifacts/12132185041)** — extract the ZIP and open
`friends-only-personal.apk` with the normal file manager. Version code:
**385806742**. Feed videos now use the existing inline loop instead of opening
**View in Reels / Watch again** end cards. No fullscreen viewer, timer or
synthetic replay tap is added.

Local and [GitHub APK builds](https://github.com/fluffydisk/friends-only-instagram/actions/runs/39114270905) passed all **ten patches**, **163 Python
tests and 615 Java/Kotlin assertions**, signatures/identity, 16 KiB alignment
and the emitted-theme guard. **Phone playback is still unverified.** Remove
only the older **Friends Only** app for this new CI signing key; its local
session/settings are cleared. Official Instagram may stay installed. Do not
use the legacy v2 installation helper.
<!-- ALPHA8_DELIVERY_END -->

The end-card issue report does not name the installed APK/run; it is not
version-specific confirmation that alpha.7 was installed or tested.

<!-- ALPHA7_DELIVERY_START -->
**Previous version: [alpha.7, artifact 67](https://github.com/fluffydisk/friends-only-instagram/actions/runs/39081645229/artifacts/12119595829)**, then extract
`friends-only-personal.apk`. Version code: **385806741**. Both local and
[GitHub builds](https://github.com/fluffydisk/friends-only-instagram/actions/runs/39081645229) passed all **nine patches**, **163 Python tests and 558
Java/Kotlin assertions**, signatures/identity, 16 KiB alignment and the
mandatory **7/47/14** emitted-theme guard. **Alpha.7 phone results are pending.**
The [alpha.6 fallback, artifact 62](https://github.com/fluffydisk/friends-only-instagram/actions/runs/39064660107/artifacts/12112951982) is known to open, but retains the
reported Home-to-Reels swipe.
<!-- ALPHA7_DELIVERY_END -->

**Alpha.5 is withdrawn. Do not reinstall artifact 55.** Normal installation
succeeded but opening immediately closed on this phone. A confirmed native
color-resource contract bug is consistent with that failure; no phone stack
trace establishes it as the sole cause. Alpha.6 restores all 47 semantic color
attributes to resource references. Both its local and GitHub APK passed the
mandatory **7 style families / 47 nonzero references / 14 palette colors**
guard; the independent full preservation audit of **1,097** original colors was
local-only. Alpha.6's later opening report is user evidence.

## Install a new-key Friends Only build

1. Sign into GitHub, download [alpha.8 artifact 72](https://github.com/fluffydisk/friends-only-instagram/actions/runs/39114270905/artifacts/12132185041) and extract
   `friends-only-personal.apk` from the ZIP.
2. Remove only the older **Friends Only** native app when updating between
   these disposable-signature builds. This clears its local session/settings;
   you may need to sign in again. Official Instagram can stay installed.
3. Open the APK through the normal file manager and confirm Android's normal
   installation dialog. Check opening first, then the individual features.
   Do not reset the phone or use the legacy v2 installation helper.

Stable signing for later updates is supported through the optional secrets in
[the native guide](../native-patches/README.md). Compilation does not require a
secret, a local compiler or a paid service; the current GitHub integration
cannot configure repository secrets automatically.

## Legacy helper package error is separate

The old v2 diagnostic tool creates a session with fixed package hint
**`com.instagram.android`**, but alpha.3 and later use
**`dev.friendsonly.instagram`**. Its legacy **-2** “inconsistent package” result
is a helper limitation, not a verdict on normal file-manager installation.
The same alpha.3 APK installed/opened normally. Alpha.5 also installed normally,
then failed opening; the helper does not diagnose that startup failure.

Do not use this helper for new own-package APKs. Its printed
`Package: com.instagram.android` is fixed, not a real selected-file identity
probe. Keep historical diagnostics separate from newer app behavior.

## Alpha.7 feature checks after installation

Alpha.7's source removes Reels, Explore and Create from the shared native
tab factory for initial and refreshed navigation/pager lists, defaults to Home
and blocks the three supported central Reels viewer launchers from every
source. There is no switch to re-enable discovery. This must be checked on the phone by swiping Home, opening video posts from Home
and profiles, and opening Reels shared through chats. Ordinary photo carousels
and inline video playback must remain usable. We do not delete arbitrary
native classes or globally block all horizontal gestures.

Home Settings/long-press and the supported profile settings entry open only
Friends Only settings: **Only accounts I follow / Yalnızca takip ettiğim
hesaplar**, a hold-to-2× usage hint and Done. Instagram/Meta's general settings
hub is no longer exposed through that own-profile entry. Native interception
happens before the original menu is built; the native settings-action hook is
its fallback. No generic profile-menu view binding is installed, so other
people's follow/report/block menus remain native. Genuine login, account
checks and necessary legal screens remain platform-owned; other original
features or branding outside the exact changes can remain. The two exact
supported profile Threads and Direct-composer Meta AI promotional controls are
hidden when the bounded view scan encounters them; content lists are not scanned.

The optional following filter is on by default. Missing author relationship
metadata rejects the item, so followed-account posts can disappear and Home
can become short or empty. Turn it off in Friends Only Settings and refresh
Home to recover the original feed; refresh again after re-enabling. It does
not change chronological ordering or establish cache/alternate-parser coverage.
Embedded Story cards without a primary post may also be dropped; top Stories
playback, Direct sending and groups still need separate tests.

Hold a playing inline feed video at its center or edges and check 2× while
held. Native release/cancel/recycle reset is preserved in source, but must be
checked on the phone too, including scrolling away and background/resume. The
dedicated feed gate also keeps the existing background cleanup path enabled;
global configuration getters and Story/Direct players are unchanged.
There is no speed setting, fullscreen requirement or Reels transition.

## Alpha.8 video-end check

After installing the identified new build, let a playing feed video reach its
end. **View in Reels** and **Watch again** should both stay hidden; the same
video should restart inline without opening Reels. Check multiple cycles,
scroll-away/return, background/resume and hold-to-2× around the video boundary.
Story/Direct playback remains a separate regression check. These are intended
results, not reported phone successes.

## Recorded installation history

| Output and tool | Real-phone result |
| --- | --- |
| Alpha.1, diagnostic v1 | `FAILURE_INVALID (4)`, legacy `-25`: version `385709029` older than recorded `385806735` |
| Alpha.2, diagnostic v1 | `FAILURE (1)`, legacy `-110`: `INSTALL_FAILED_INTERNAL_ERROR: Session files in use` |
| Alpha.2, file manager | About 30 seconds of installation, then generic not-installed dialog; no detailed code |
| Alpha.2, diagnostic v2 | `FAILURE_CONFLICT (5)`, legacy `-7`: `INSTALL_FAILED_UPDATE_INCOMPATIBLE`, signature mismatch |
| Alpha.3, normal file manager | User confirmed installation/opening; screenshot shows logged-in Home/feed, Stories tray and Home/Messages/Profile navigation |
| Alpha.3, old diagnostic v2 | Legacy -2 package-hint mismatch from helper hardcoded for another package; normal install works |
| Alpha.4 | Combined Home/profile local and GitHub APK checks passed; user explicitly has not phone-tested it |
| Alpha.5 | Normal installation succeeded after old Friends Only removal; opening closes immediately; withdrawn despite passing static builds/audits |
| Alpha.6 | Local/GitHub APK and mandatory theme guard passed; user confirms opening, but Home swipe still reaches Reels |
| Alpha.7 | Local/GitHub signed APK and nine-patch suites passed: Python 163, Java 155, Kotlin 403; phone tests pending |

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
historical alpha.2 `-7` result. The user's subsequent alpha.3 file-manager
installation and opening succeeded.
See [native verification](../native-patches/VERIFICATION.md) for the evidence
and limits.
