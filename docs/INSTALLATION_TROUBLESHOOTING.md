# Native APK installation results

**Alpha.3 installs and opens on the user's Galaxy S24+ (SM-S926B), Android
17 / SDK 37 / One UI 9, through the normal file manager.** Their screenshot
shows a logged-in Home feed, Stories tray and Home/Messages/Profile navigation.
Story playback, message sending, groups and the login/challenge flow have not
been separately confirmed. The earlier alpha.1/alpha.2 installation errors
remain recorded below as history. The user explicitly has **not tested
alpha.4** yet. Alpha.5 is being implemented; its APK is not available yet.

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
the selected APK's real identity.

## Alpha.5: a short or empty Home feed

Alpha.5 adds **Only accounts I follow / Yalnızca takip ettiğim hesaplar**, on by
default. It uses existing metadata in the supported Home response parser:
ordinary posts need the primary author's explicit `following = true`; injected,
non-post and unknown units are discarded. Missing relationship metadata is
rejected too, so even a followed account's post can be hidden and Home can be
short or empty. Cached/alternate render paths and the actual phone behavior are
unverified; this is not a server-side chronological-feed guarantee.

If Home is unexpectedly empty, open the Home **Settings / Ayarlar** button or
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
features or chrome can remain outside the verified surfaces. All new alpha.5
APK and phone behavior is still pending validation. The final palette fix
passed its full six-patch bundle checks (Python 140; Java 180/32/26; Kotlin
26/42/35/92), 97 actual-source audit checks and a separate drawable compile/link
harness. These checks do not prove a full APK build, rendered UI or feed behavior.

## Install the alpha.4 test APK

Alpha.4 uses **`dev.friendsonly.instagram`**, the **Friends Only** launcher name,
our own icon and version code **385806738**. It uses Android's normal
separate-package installation flow. No signature, authentication or integrity
bypass is included.

1. Download [the alpha.4 `friends-only-personal-test-apk-44` ZIP](https://github.com/fluffydisk/friends-only-instagram/actions/runs/39037320923/artifacts/12103030677)
   while signed into GitHub, and extract `friends-only-personal.apk`.
2. Open the APK through the phone's file manager and complete Android's normal
   confirmation. Allow the selected source to request installation if asked.
3. Open **Friends Only**. Test specific features separately and report the
   version/artifact used rather than assuming all account behavior is verified.

Alpha.4's new **Akış ve profilden Reels açılmasını engelle** setting defaults on
and is intended to stop known video-post taps on Home and user profiles from
opening the Reels viewer while retaining inline video. Its combined four-patch bundle passed 139 Python tests,
180 Java policy assertions, 26 Kotlin identity assertions and 42 Kotlin guard
assertions. A signed local APK and independent static guard audit also passed.
The [GitHub APK build](https://github.com/fluffydisk/friends-only-instagram/actions/runs/39037320923)
also passed the same automated checks and produced the downloadable APK above.
Its phone installation and tap behavior remain unverified.

**Alpha.3's signing key was disposable.** A new key cannot update that installed
build. If Android rejects the alpha.4 update, uninstall only the older **Friends
Only** native app, then install the new APK; that clears Friends Only's local
session/settings. **Do not remove official Instagram, delete profiles or reset
the phone.** Official Instagram can remain installed or be restored from Play
Store. Its local data is separate. The workflow supports stable signing with
three optional repository secrets, documented in the
[native README](../native-patches/README.md); current integration permissions
cannot set them automatically. No secrets or paid services are required to
compile APKs. Successful installation and a visible authenticated Home do not
establish that Instagram accepts every login/challenge or feature.

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
| Alpha.5 | Full inline-palette six-patch bundle checks passed; actual APK and phone verification pending |

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
