# Native APK installation results

**Alpha.5 installs but closes immediately on opening on the user's Galaxy S24+
(SM-S926B), Android 17 / SDK 37 / One UI 9. It is withdrawn.** The user removed
the older Friends Only app before the successful normal installation. The
selected file's SHA-256/size match CI artifact 55. **Do not download or reinstall
that artifact.** [Alpha.6's test APK is now available](https://github.com/fluffydisk/friends-only-instagram/actions/runs/39064660107/artifacts/12112951982);
local/GitHub builds and its new mandatory color-reference guard passed. Its
phone installation/opening is still unverified.

A confirmed theme code-contract bug is consistent with this crash: native color
consumers expect resource IDs, but alpha.5 supplies inline colors that resolve
to ID zero. No phone stack trace has been captured, so that is not established
as the sole phone cause. Passing compilation, signing and static audits did not
validate startup.

Alpha.3 is the only native build known to open on this phone; its screenshot
shows logged-in Home/feed, Stories tray and three-tab navigation. Story playback,
message sending, groups and the login/challenge flow were not separately
verified. The user has not tested alpha.4. See
[native verification](../native-patches/VERIFICATION.md) for the updated evidence.

## Legacy helper error on alpha.3

The old v2 diagnostic tool creates an install session with the fixed package
hint **`com.instagram.android`**. Alpha.3's actual package is
**`dev.friendsonly.instagram`**. Selecting alpha.3 in that helper produced legacy
**-2**, reporting that the real package does not match the session hint.
The reported SHA-256 matches the delivered alpha.3 APK:
`ab5d7814d353fb5d4f3541aa8b1655ee8ea656aeb971a24a97bcd7d046daa6c9`.
This is a limitation of the old helper, **not an alpha.3 installation rejection**.
The same APK installed and opened normally through the file manager.

Do not use that helper for alpha.3 or later own-package APKs. The helper's
printed `Package: com.instagram.android` field is fixed; it does not determine
the selected APK's real identity. The same package-hint limitation still
applies to alpha.5; do not repeat that helper test to investigate its separate
startup crash.

## Intended feed/settings behavior after a working startup fix

Alpha.5 closes before Settings or Home can be tested. The following describes
the implemented design and is not a workaround for its startup crash.

The new filter adds **Only accounts I follow / Yalnızca takip ettiğim hesaplar**, on by
default. It uses existing metadata in the supported Home response parser:
ordinary posts need the primary author's explicit `following = true`; injected,
non-post and unknown units are discarded. Missing relationship metadata is
rejected too, so even a followed account's post can be hidden and Home can be
short or empty. Cached/alternate render paths and the actual phone behavior are
unverified; this is not a server-side chronological-feed guarantee.

Once a fixed build can open, if Home is unexpectedly empty, open the Home **Settings / Ayarlar** button or
long-press Home, turn off that following filter, then refresh Home. Refresh
again when enabling it. Standalone Story, Direct and profile parsers are
unchanged. Home-embedded Story cards without a primary media post are dropped
too, and are not proven to be recommendations only. Top Stories-tray playback
still needs a phone test; all Story surfaces cannot be claimed preserved.
The extension creates no new API requests or credential collection.

Alpha.5 also adds neutral light/dark surfaces, restrained Story rings, an own
Home header and text navigation. **Minimal header and navigation / Sade başlık
ve gezinme** toggles runtime decorations; the compiled palette, Story-ring and
splash changes stay in the APK. **Disable filters / Filtreleri kapat** changes
content/navigation filters and leaves that separate appearance preference
unchanged. Native post/chat components remain Instagram's; some original
features or chrome can remain outside the verified surfaces. Alpha.5's local
and GitHub APK builds passed all six patches, Python 140, Java 180/32/26 and
Kotlin 26/42/35/92 checks. Independent emitted DEX/resource audits passed on the
final local APK; those audits were not repeated on the CI APK bytes. These
checks do not prove rendered phone UI or feed behavior.

## Alpha.6 source fix and validation status

Alpha.6 (version code **385806740**) restores all 47 semantic color attributes
to color-resource references, preserving the original 1,097 color IDs/values.
A new mandatory output guard rejected the actual signed broken alpha.5 APK.
Its 163 Python tests, Java/Kotlin suites and 150 actual-source checks passed.
The signed local APK passed its mandatory emitted-color guard and independent
binary resource audit. The GitHub APK build and its own mandatory theme guard
also passed. The full 1,097-original-color audit was local-only. Phone
installation/opening remains unverified. This corrects a
confirmed code contract, not a phone-verified crash outcome. There is still no
actual phone stack trace proving it is the only cause.

## Install the alpha.6 candidate and check startup

1. Download [the alpha.6 artifact 62 ZIP](https://github.com/fluffydisk/friends-only-instagram/actions/runs/39064660107/artifacts/12112951982)
   while signed into GitHub, then extract `friends-only-personal.apk`.
2. Remove only the older **Friends Only** native app because the disposable CI
   key changed. This clears its local session/settings; signing in again may be
   necessary. Do not remove official Instagram or reset the phone.
3. Open the new APK through the normal file manager and confirm installation.
   Do not use the old v2 diagnostic helper. Check opening before testing feed,
   themes, Stories or messages; report the exact version and result separately.

Alpha.6 is version code **385806740**. Its code/resource checks correct the
identified bug, but successful startup on the phone has not been confirmed. Do
not reinstall broken artifact 55. The
[alpha.3 fallback](https://github.com/fluffydisk/friends-only-instagram/actions/runs/39019337357/artifacts/12095918541)
is the only native build known to open on this phone; replacement can also
require removing only Friends Only and losing its local data.

The workflow's optional stable-signing setup is documented in the
[native README](../native-patches/README.md); no secret or paid service is
required to build an APK.

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
| Alpha.6 | Local/GitHub APK builds and mandatory emitted-theme guard passed; local independent color preservation audit passed; phone installation/opening unverified |

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
