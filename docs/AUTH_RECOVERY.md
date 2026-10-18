# Sign-in appearance and crash correction · 0.4.0-alpha.3

[Download APK · package 86](https://github.com/fluffydisk/friends-only-instagram/actions/runs/39139968972/artifacts/12141631238) · [successful CI run](https://github.com/fluffydisk/friends-only-instagram/actions/runs/39139968972)

Local and CI builds passed **173 Python tests and 996 Java/Kotlin assertions**,
all 12 patches and mandatory signed-output checks. Phone tests are pending.

The user confirmed installing personal-test artifact **81** (0.4.0-alpha.2) on
Galaxy S24+ / Android 17. The genuine login still had the colored Instagram logo,
blue controls and blue-gray canvas. After submitting their credentials, the app
closed and subsequent opens produced Samsung's repeated-crash dialog. This is
not evidence that authentication completed successfully. **Artifact 81 is withdrawn.**

## Concrete defect and correction

In alpha.2's emitted `CircularImageView.onDraw`, the new Story fallback branch at
code address **18** targeted **10**, which is inside the operand of the native
instruction at 9. The method fails static Dexlib analysis. This is a confirmed
invalid-bytecode defect consistent with a crash when Home avatars load after
sign-in. No phone stack trace has established it as the only crash cause.

The inserted branch now uses an external label anchored to the real final
`RETURN_VOID` at address **27**. Original native image drawing and callbacks run
first; registers, Stories behavior and the supported rounded-square image overlay
remain. Tests check both native/fallback branch targets. A new independent
**final signed-APK** guard checks instruction boundaries and statically analyzes
all methods of the six native presentation classes plus our runtime classes
against the pinned Android SDK. Failed analysis blocks APK delivery. This goes
beyond the prior patch-source fixtures, signatures and resource checks, which
passed without detecting the malformed emitted branch. It is still not ART or
physical-device execution.

## Genuine native sign-in, new presentation

The separate `ig_logo` drawable (two density variants, ID `0x7f0820ec`) now uses
our own neutral FO mark at the original dimensions. The prior four wordmark
replacements remain. Signed-output guards verify every replacement's bytes.

A presentation-only decorator is restricted to four exact native signed-out
Activity classes. It applies Manrope, a neutral canvas and muted primary controls
to supported native widgets. It never reads field values, hints, labels, route
bundles, credentials or account state. Focus, password transformation, autofill,
validation, native callbacks, keyboard/insets, 2FA, recovery and legal/provider
notices remain native. It adds no recreated login form or authentication bypass.
A signed-out-only layout listener coalesces changes to at most twice a second,
caps the walk at 256 views, and is removed when the Activity pauses/dies. Unknown
hosts/renderers retain genuine appearance. Server-selected Bloks widgets may not
expose conventional Android fields/backgrounds, so complete visual coverage is
not claimed without a phone test. Instagram remains the underlying account service.

## Local crash report, voluntary copying

The new launcher installs a local uncaught-exception wrapper and retains the
original handler. After a captured Java failure, the next launcher open offers
**Copy technical report** and **Try again**, without automatically reopening a
crashing screen or deleting account/session data.

The private report contains only app version, Android SDK, exception class names
and bounded class/method/line stack entries. It excludes exception messages,
filenames/paths, usernames, credentials, messages, cookies, logs and intent data.
Nothing is uploaded. Copying to the Android clipboard requires an explicit tap.
Report formatting/identifier filtering, size bounds and sensitive-message/path
exclusion have host tests. Retrying clears only that report, not the account.

This cannot retrospectively capture artifact 81's crash. Native signals,
process kills, failures before the custom launcher starts, and cold deep links
that bypass it may not produce this report. Absence of a report is not evidence
that the app did not crash.

## Phone test

1. Download the newest artifact linked in README, extract the ZIP, and install
   `friends-only-personal.apk`. CI uses a new signing key, so remove only old
   Friends Only first; local session/settings are cleared. Official Instagram
   may remain installed. Never use the old fixed-package installation helper.
2. The build is **0.4.0-alpha.3 / code 385806745**. Check the real login's neutral
   logo/canvas/controls and readable fields/keyboard. Complete normal challenges
   if offered; do not share credentials, screenshots of account data or codes.
3. Check Home loads after login, then reopen the launcher twice. Check Stories,
   carousels, Direct/group sending and the existing video controls separately.
4. If it closes, reopen through the launcher. Use **Copy technical report** on
   the recovery screen and paste that text into this chat. Try again is optional.
   If no recovery screen appears, report that and the exact last action; do not
   repeatedly enter your password or reset the phone.
5. A remaining original-looking login variant is a separate visual issue from
   crashing. Share a redacted screenshot of its chrome if needed.

See the [evidence record](evidence/native-auth-recovery-2026-10-18.json) for actual
checks/builds. This version's installation, login success, post-login opening,
rendering, clipboard recovery, Stories and Direct remain unverified on the phone.
