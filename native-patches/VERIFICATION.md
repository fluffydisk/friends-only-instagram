# Native patch evidence · updated 2026-10-17

This module is a native-patching experiment. **[Alpha.6's test APK is available](https://github.com/fluffydisk/friends-only-instagram/actions/runs/39064660107/artifacts/12112951982)
and passed local/GitHub output checks; its phone installation/opening is
unverified. Alpha.5 is withdrawn:** it installs but immediately closes on the
user's S24+ / SDK 37. Alpha.3 is the only build known to open on that phone;
alpha.4 remains untested. Story playback, Direct sending, groups and the
login/challenge flow were not separately verified.

All patches target Instagram `451.0.0.20.80`. Static build/audit success is not
runtime validation. The color-resource fix corrects a confirmed code contract,
but no phone stack trace proves that defect was the sole alpha.5 crash cause.

## Alpha.3 full build checks actually run in cloud

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
30 seconds, with a generic not-installed message and no detailed code. Alpha.2
installation remained failed; that version never reached launch/account testing.

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
integrity bypass is introduced. The user subsequently confirmed installation
and opening through the normal file manager; coexistence with a simultaneously
installed official Instagram has not been specifically tested. The patch does
not provide a complete replacement of Instagram's native UI.

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
records the exact local file hash and audit. Those automated checks are static
evidence; the subsequent phone report below is separate evidence of installation
and opening.

## Alpha.3 real-phone success and legacy-helper mismatch

The user confirmed that **alpha.3 installs and opens through the phone's file
manager** on the Samsung SM-S926B, Android 17 / SDK 37 / One UI 9. They reported
that its features look good overall. Their screenshot shows an authenticated
Home/feed, the Stories tray, and Home/Messages/Profile navigation. These visible
screens do not prove Story playback, Direct sending, group behavior, settings
persistence or the full login/challenge flow; none is separately marked tested.

The same alpha.3 file was also selected in the **older v2 helper**, which sets a
`com.instagram.android` session package hint. Android returned legacy **-2**
because the APK's real package is **`dev.friendsonly.instagram`**. The reported
SHA-256 matches the delivered CI file,
`ab5d7814d353fb5d4f3541aa8b1655ee8ea656aeb971a24a97bcd7d046daa6c9`.
This is a package-hint limitation in the diagnostic tool. It is **not evidence
of an alpha.3 installation failure**: the independent normal installation
succeeded. Do not use that helper for alpha.3 or later own-package APKs.

## Alpha.4: Home/profile video taps into Reels

The user reports that tapping a video post on Home opens Instagram's Reels
viewer and wants that transition to do nothing. They also requested the same
behavior for user profiles. The fourth patch remains named
**Friends Only feed video taps** and covers Home plus user profiles. Output
version code is **385806738**; the original-input package/version verification remains unchanged.

The implementation guards the Clips viewer launch only when its
`ClipsViewerSource` name exactly matches one of:

```text
FEED_TIMELINE
FEED_TIMELINE_POST_TAP
FEED_TIMELINE_FOLLOWING
FEED_TIMELINE_FAVORITES
FEED_TIMELINE_ALL
FEED_TIMELINE_MIXED_UNCONNECTED
PROFILE
SELF_PROFILE
FEED_CONTEXTUAL_PROFILE
FEED_CONTEXTUAL_SELF_PROFILE
OPAL_USER_PROFILE
OPAL_USER_SELF_PROFILE
PRIME_PROFILE
PRIME_SELF_PROFILE
```

Its independent preference, `block_feed_reels_open`, defaults on. Long-press
Home to change **Akış ve profilden Reels açılmasını engelle** (block the Reels
viewer from feed and profiles); **Show all** disables it. Hiding the Reels tab
is a separate setting. Video posts are not removed, inline playback/network handling is not
changed. Unknown, Stories, Direct and group-profile launch sources pass through.
These are intended boundaries, not phone-tested behavior.

Before the profile expansion, the Home-only four-patch `build.py --check` passed
**139 Python tests, 160 Java policy assertions, 26 Kotlin identity assertions and
42 Kotlin feed-guard assertions**. Inspection of the original 23 DEX files found
**exactly one** supported method; its insertion contract passed (17 registers,
171 instructions, no try blocks). The associated manual
[Home-only CI run](https://github.com/fluffydisk/friends-only-instagram/actions/runs/39036959634)
was cancelled before delivery when the user added profiles. Its candidate is
not a delivered alpha.4 APK.

The final combined Home/profile `build.py --check` then passed: all **four
patches** loaded, **139 Python tests, 180 Java policy assertions, 26 Kotlin
identity assertions and 42 Kotlin guard assertions** succeeded. Static call-site
tracing also confirmed that `PROFILE` and `SELF_PROFILE` reach the guarded core
viewer path. This is source/bytecode evidence, not a phone navigation test.
Applying the combined patch set produced a signed local alpha.4 APK with
version code **385806738**. All output validators, signature and alignment checks
passed. Its size is **252,798,008 bytes**, SHA-256
`d7324c80fa592f0199707168b19c585c69f033f13803671236efc258a4a3b2a8`.
The independent guard audit found exactly **one injected call across 20 DEX
files**, with all 171 original instructions/branches preserved (177 instructions
after insertion). All fourteen source names exist in both Instagram's enum and
our compiled policy. [Alpha.4 evidence](evidence/native-alpha4-2026-10-17.json)
records the local output and audit; this is static evidence only.

The [GitHub APK build](https://github.com/fluffydisk/friends-only-instagram/actions/runs/39037320923)
(number 44, source `777dd17800f67648fd654e3de1ac485c124fe2f4`) **succeeded**.
Its logs confirm **139 Python tests, 180 Java policy assertions, 26 Kotlin
identity assertions and 42 Kotlin guard assertions** passed. The verified
original APKM was merged, all four patches were applied, and the rebuilt,
personally signed output passed static validation. Download
[`friends-only-personal-test-apk-44`](https://github.com/fluffydisk/friends-only-instagram/actions/runs/39037320923/artifacts/12103030677)
and extract `friends-only-personal.apk`. Its
[`native-patch-reports-44`](https://github.com/fluffydisk/friends-only-instagram/actions/runs/39037320923/artifacts/12103085505)
artifact records the CI output metadata. The CI APK is **253,154,478 bytes**,
SHA-256 `5f69a59c7c41881abb7aff9dc7124414ad2e3e3ea32f0bb0d2ea7aa723fe7800`.
Its clean-build signing key is different from the local APK's key and was not
preserved. No combined alpha.4 phone installation or tap behavior has been
verified.

Alpha.3 was signed with a disposable CI key. The next key cannot update that
installed build. If an update is rejected, remove only **Friends Only**, which
clears its local session/settings, then install alpha.4. Official Instagram does
not need removal. Stable signing is already supported through the documented
optional keystore secrets, but the current GitHub integration cannot configure
those secrets automatically (HTTP 403). No secret setup is needed to compile
an APK.

## Alpha.5: strict Home feed and quiet native shell

Alpha.5 adds two patches, **Friends Only following feed** and **Friends Only
quiet visual theme**, bringing the required set to **six**. The output remains
`dev.friendsonly.instagram`, version code **385806739**. The original input
continues to require Instagram `451.0.0.20.80 / 385709029` and verified signing
provenance. Local/GitHub builds and final local emitted-output audits passed.
The user then installed the CI APK successfully, but it immediately closed on
opening. It is withdrawn. Rendered UI, strict-feed and account behavior were not
reached or verified.

The following-feed patch targets the unique native Home response parser at its
two array boundaries (`items` and `feed_items`). It checks an ordinary post's
primary author's existing `friendship_status.following` value, requiring the
boxed Boolean `TRUE`; `followed_by` is not used. Injected/sponsored posts,
non-post wrappers and unknown units are rejected. The original null/skip guards
handle the rejected entries. The shared Media, standalone Stories, Direct and
profile parsers are not modified. There is no new API client, request/header rewrite,
following-list request, scraping, credential reading or outbound collection.

Actual Home `feed_items` also supports `stories_netego`
(`XDTStoriesInFeedItem`, with `reels`/`portable_tray`) and `tagged_edge_story` /
`explore_story` wrappers. They do not supply the required primary Media post, so
strict mode drops these **in-feed Story units** too. Their recommendation-only
status cannot be established. Regular Story parsers/requests are unchanged;
this does not prove every Story surface is unaffected. Top Stories-tray
playback remains unverified on the phone.

`following_only` defaults true, including before preferences initialize.
Reflection/metadata failure is strict: the item is rejected. That can remove
followed-account posts too when their relationship metadata is absent, making
Home short or empty. It does not purge already rendered/cached posts and does
not establish that alternate parser/render paths are covered. Neither server
ordering nor chronological Following is changed. The policy's acceptance rule
is verified; a guaranteed runtime friends-only feed is **not** verified. In a
working fixed build, disable **Only accounts I follow / Yalnızca takip ettiğim
hesaplar** and refresh Home if necessary; alpha.5 currently cannot open Settings. Refresh after either direction of that setting change.

The visual patch edits seven semantic light/dark style families plus neutral
Story-ring gradients, **57 style attributes total**, and **14 launch-theme
attributes** across default, night, Android 12+ and Android 12+ night
configurations. Fourteen palette values were written as typed inline hex colors;
no new named color resources or resource-allocation step was used. **This is now
a confirmed native semantic-color contract defect**, described below; static
XML/ARSC validity did not make those attributes runtime-compatible. The patch
supplies independent tab/splash assets instead of the source OS marks. Runtime decoration adds a Friends Only Home header and
Settings button, and **Friends / Chats / Me** labels (**Arkadaşlar / Sohbetler /
Ben** in Turkish) over the existing bottom tab controls, covering their original
icons and numeric/red badges. The exact supported internal startup-logo
ImageView in MainActivity also receives our icon through bounded name matching.
This does not alter authentication screens. Runtime color selection uses the
native background/theme, including explicit in-app light/dark selection, rather
than assuming the system theme always matches. The settings dialog uses that
same palette. The existing native post, Story and chat components remain the
content UI. Other layouts, features and Instagram chrome
outside these scopes can remain; this is not a complete new native renderer.

`quiet_ui` defaults true. It governs the runtime header/navigation decorations;
turning it off does not undo compiled palette, Story-ring or launch-resource
changes. The settings dialog is scrollable and opens from the Home Settings
button or Home long-press. **Disable filters / Filtreleri kapat** disables content
and navigation filters without changing this separate appearance preference.
The new decoration reuses the existing throttled (250 ms) bounded view update;
no new polling timer or global observer is introduced.

The final inline-palette full `build.py --check` **passed**, loading all **six
patches** with **140 Python tests**, Java Focus **180**, Feed **32**, Chrome **26**,
and Kotlin Identity **26**, Tap **42**, Following **35**, QuietTheme **92**
assertions. A separate actual-source audit passed **97 checks**, including all
four launch configurations. An isolated AAPT2 harness also compiled/linked the
new selectors and launch-background drawables against the real Android
framework, using attribute stubs and a mock icon. That harness is not a full
Instagram APK build or rendered phone test.

The earlier named-color allocation path did not produce a deliverable APK; its
[manual CI run 49](https://github.com/fluffydisk/friends-only-instagram/actions/runs/39058668640)
was cancelled after the local resource-build failure was identified. The final
implementation uses typed inline palette values.

Final source `d2f8dbb` passed the full bundle checks with the suite counts above.
Applying all six patches to the verified original produced a signed local APK:
**252,810,488 bytes**, SHA-256
`64beb265fe13359149674f11d851e8a0ce2561469679cadb719cf9b2433a67d6`,
package **`dev.friendsonly.instagram`**, version code **385806739**. Signature,
package/version and alignment/static output checks passed.

Independent read-only audits of that **exact final local APK** passed:

- Emitted DEX: all **519 original Home-parser instructions and control flow**
  retained, with **12 inserted instructions** for two filter boundaries and one
  alignment NOP (532 total); **2,776 compared shared methods** unchanged from
  alpha.4. The fourteen-source tap guard is unchanged. The new feed extension
  introduces no network, storage or logging references.
- Emitted resources: **131 checks**, covering **47 semantic color attributes,
  10 Story-ring attributes and 14 launch attributes across four configurations**; **1,583 other
  semantic attributes** were unchanged. This verifies compiled resources, not
  their appearance on a phone.

The [final GitHub APK build](https://github.com/fluffydisk/friends-only-instagram/actions/runs/39059450948)
(run **55**, source `d2f8dbb`) **succeeded**. Actual job logs and downloaded reports
confirm **140 Python tests**, Java **180/32/26**, Kotlin **26/42/35/92**, all **six
patches** applied, full rebuilding, personal signing and static output validation.
The [`friends-only-personal-test-apk-55` artifact](https://github.com/fluffydisk/friends-only-instagram/actions/runs/39059450948/artifacts/12110373262)
is retained **only as broken historical evidence; do not install it**.
Its CI output is **253,171,054 bytes**,
SHA-256 `c9294c038a6727b5686b1bff8585630094f05ef2dae3a9444fb080775d32f646`.
The [reports artifact](https://github.com/fluffydisk/friends-only-instagram/actions/runs/39059450948/artifacts/12110657364)
records its output metadata. Its clean-build signing key was disposable.

The independently audited local bundle and the CI bundle have the **same
SHA-256**, `02e677dcce7f61972ed38c2c44e6894562e892d3ff0d23a9cc8740fad2971edf`.
The emitted DEX/resource audits above apply **only to the local APK**. The CI APK
passed the workflow's application/signing/static checks; its bytes were not
independently downloaded/audited in cloud. The subsequent user phone test
confirmed installation but failed startup. See
[alpha.5 evidence](evidence/native-alpha5-2026-10-17.json) for metadata/audit scope.
No alpha.5 appearance, feed, Story, Direct or authentication success is claimed.

## Alpha.5 phone report and confirmed code-contract regression

The user removed the older Friends Only app, then installed alpha.5 normally
through the file manager on the S24+ / SDK 37. Installation **succeeded**, but
opening the app caused it to close immediately. The reported file SHA-256
`c9294c038a6727b5686b1bff8585630094f05ef2dae3a9444fb080775d32f646` and
**253,171,054-byte** size match CI artifact 55. The old v2 helper's -2 error
still comes from its hardcoded package hint; it is not the normal installation
result and does not diagnose this startup crash.

Source/bytecode investigation confirmed a theme contract bug: the 47 modified
semantic color attributes were inline ARGB values, while native
`LX/00RQ.A0b` reads `TypedValue.resourceId` without accepting an inline-color
fallback. That yields resource ID **0**, and
`InstagramMainActivity.A1y` passes it to `Context.getColor(0)`, which can throw
`Resources.NotFoundException`. This is a confirmed code defect **consistent
with the reported crash**. No actual phone crash stack trace has been captured,
so it is not proved to be the phone's sole failure cause.

Alpha.5 is withdrawn from recommended use despite its successful static checks.
Do not redownload/reinstall artifact 55 or reset the phone. The alpha.6 candidate
is now available for a new startup test; **do not use the broken artifact 55**. Alpha.3 remains the known-to-open
fallback. Returning to it may require removing only Friends Only, losing its
local session/settings and signing in again; no official Instagram removal is
needed.

## Alpha.6: resource-reference contract fix · phone validation pending

Source **`91ce031`**, version code **385806740**, changes all **47 semantic
attributes** back to `@color` references instead of inline ARGB. Fourteen own
palette colors are appended in the existing tracked `colors.xml`; all **1,097
original color resource IDs and values** remain unchanged. Native
`LX/00RQ.A0b` is not patched or bypassed, and no new requests, permissions,
backend or diagnostic helper are added.

Mandatory final output validation now runs the theme APK guard: it checks all
**seven semantic style families**, **47 nonzero color-resource references** and
**14 exact palette values**. Running the integrated `inspect_output` against the
actual signed broken alpha.5 APK **rejected it**, establishing a regression
check against the delivered defect rather than a mock alone.

Completed checks passed **163 Python tests** (140 earlier + 23 new cases), Java
Focus **180**, Feed **32**, Chrome **26**, and Kotlin Identity **26**, Tap **42**,
Following **35**, QuietTheme **145** assertions. A separate actual-source theme
audit passed **150** checks. The actual Morphe resource preflight passed all
**14 added colors / 47 semantic references**.

Full application/signing/output validation then passed on the final local APK:
version code **385806740**, package **`dev.friendsonly.instagram`**,
**252,810,488 bytes**, SHA-256
`e8ce885c9705ada059e23fb84444c71a139a307e4d9c6e9888dd30fb1868834f`.
The mandatory emitted-color guard passed all **7 styles / 47 references /
14 palette colors**. An independent binary ARSC audit on that exact SHA passed
and confirmed all **1,097 original color IDs and values** were unchanged. The
local patch-bundle SHA-256 is
`98d082bc56b308925c6fe1c0021629ba88377a6731a0c41e1d5428e608578dd4`.
The [GitHub APK build](https://github.com/fluffydisk/friends-only-instagram/actions/runs/39064660107)
(run **62**, source `91ce031`) **succeeded**. Downloaded CI reports and actual job
logs confirm **163 Python tests**, Java **180/32/26**, Kotlin **26/42/35/145**, all
**six patches**, rebuilding, signing and static output verification. The CI APK's
new **mandatory emitted-theme guard** passed all **7 styles / 47 nonzero
semantic color references / 14 exact palette values**.

Download [`friends-only-personal-test-apk-62`](https://github.com/fluffydisk/friends-only-instagram/actions/runs/39064660107/artifacts/12112951982)
and extract `friends-only-personal.apk`. The CI APK is **253,171,054 bytes**,
SHA-256 `b2bd1a1587fc1af920663d3eca4b6508f8be01a61a97ebed71f31d1fc37fb235`.
The [CI reports artifact](https://github.com/fluffydisk/friends-only-instagram/actions/runs/39064660107/artifacts/12112822382)
records its metadata. Its signing key was disposable. The local and CI patch
bundles have the identical SHA-256 shown above.

The independent **1,097-original-color ID/value preservation audit applies to
the local APK only**; the CI APK passed its own mandatory emitted-theme guard,
but its full original-color inventory was not independently audited. No
alpha.6 phone installation, opening, UI, feed, Story, Direct or authentication
success is claimed. Passing these checks is not proof of phone startup. No phone stack trace is available to establish the
identified contract bug as the sole cause of the user's crash.

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

## Remaining device checks

Alpha.5 installed normally but failed startup; its strict feed, shell, Stories
and messaging were not reached. Alpha.6's source/local/GitHub checks passed,
including the new mandatory compiled-theme guard, but **phone installation and
opening remain unverified**. Alpha.4 is untested, and alpha.3 is the only native
build known to open on this phone.

Download the [alpha.6 candidate](https://github.com/fluffydisk/friends-only-instagram/actions/runs/39064660107/artifacts/12112951982),
extract it and install through the normal file manager. Remove only the older
**Friends Only** native app first because the disposable CI signing key changed;
this clears its local session/settings and may require signing in again. Do not
use the old v2 helper, remove official Instagram or reset the phone. Do not
reinstall broken artifact 55. The
[alpha.3 fallback](https://github.com/fluffydisk/friends-only-instagram/actions/runs/39019337357/artifacts/12095918541)
remains available if needed.

Record alpha.6 startup separately before testing themes, settings, Home
filtering, in-feed/top-tray Stories, ordinary posts, the tap guard, Direct/groups
or authentication. If it still closes, a phone stack trace is needed to identify
the remaining cause; the existing record does not establish the color-contract
defect as the phone's sole cause.

When reporting issues, include APK/run identifier, Instagram version, S24+
Android/One UI version, steps and a screenshot with names/messages redacted.
Do not send passwords, two-factor codes, cookies, tokens or unredacted chats.
