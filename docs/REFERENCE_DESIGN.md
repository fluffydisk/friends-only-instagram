# Screenshot-led native design · 0.4.0-alpha.2

[Download APK · artifact 81](https://github.com/fluffydisk/friends-only-instagram/actions/runs/39133513593/artifacts/12139515444) · [successful GitHub build](https://github.com/fluffydisk/friends-only-instagram/actions/runs/39133513593)

Local and CI builds passed **170 Python tests and 951 Java/Kotlin assertions**,
all 12 patches and signed-output guards. **Phone rendering and interaction tests
remain unverified.**

The first user screenshot is a visual reference, not a request for its Search,
Explore, Notifications or Create destinations. The supported navigation remains
**Friends / Chats / Me**; existing posts, Stories, Direct and account behavior stay
native. The application name remains **Friends Only**, not the reference's Mono.

## Implemented

- Neutral charcoal canvas, subdued gray surfaces and local Manrope typography,
  with corresponding system light-theme colors. No new animations or requests.
- Left-aligned, spaced Friends Only Home title. The supported toolbar restyles
  existing native actions and forwards their clicks; it does not invent actions.
  Unrecognized/unlabelled variants retain their genuine controls.
- Rounded feed surfaces with side gutters, inset media and native post-action
  icons. The earlier wide text treatments are no longer applied to those icons.
  The actual photograph/video, caption, native playback and gesture listeners are
  retained; text burned into a video is part of the video and cannot be restyled.
- Rounded-square Story photographs for the supported bitmap-backed avatar inside
  the pinned Story-tray container. The original native image draw and callbacks
  still run before the overlay. Shader-backed ring colors become neutral gray;
  native ring geometry, progress, error states and Story interactions remain.
  Other avatar renderers or not-yet-loaded images fall back to native drawing.
- Rounded navigation surface with custom Home/chat/person glyphs, existing labels
  and a quiet selected underline. Large-font/narrow variants retain readable
  labels. Native click targets, prior badge policy and system-bottom insets are retained.
- The actual native Instagram wordmark drawable's four light/dark/density variants
  are replaced with Friends Only rasters at the original resource ID/dimensions.
  This reaches native screens that use that resource, including login variants.
  Welcome's main action says **Continue with your account** in supported locales.

## Account and branding boundary

This is still an Instagram-backed native prototype. The genuine account fields,
password handling, 2FA, challenges, native networking and required provider/legal
notices are preserved. There is no application-controlled credential form or
independent social backend. Usernames, posts, private messages and legal text are
not globally searched/replaced. Server-selected/Bloks login variants, settings or
other unsupported surfaces may still show Instagram branding. Complete removal
of all provider references and a wholly custom login are not claimed.

## Compatibility and checks

The new patch pins the exact six native methods for the verified 451 input:
Home toolbar and Story-container constructors, media-holder factory, managed row
inflater, CircularImageView draw and GradientSpinner draw. Unsupported bytecode
fails patching rather than guessing. The draw hook preserves native image-loaded
callbacks and register layout; its original branch is tested to enter the
post-draw hook. Managed inflation covers the factory missed by the older ordinary
Android inflater hooks. The old mandatory compiled theme-resource guard remains.

Work is coalesced on existing attach/layout hooks (at most four row/header updates
per second). No global touch interception, new model/message parser, font fetch,
window observer or polling loop is introduced. The existing following filter,
Reels blocks, hold-to-2× and inline loop patches remain selected unchanged.

Automated compilation and signed-output checks are not phone rendering evidence.
See [the evidence record](evidence/native-reference-2026-10-18.json) for actual
build results. Installation, pixel appearance, accessibility, login, Stories,
Direct sending and playback on Samsung remain unverified for this version.

## Phone check

1. Download the newest linked APK artifact from the repository README while signed
   into GitHub, extract the ZIP and open `friends-only-personal.apk` through Files.
   If a CI signing-key conflict occurs, remove only the older Friends Only; that
   clears its local session/settings. Official Instagram may remain installed.
   Do not use the old fixed-package installation diagnostic helper.
2. Check Settings' technical information says **0.4.0-alpha.2 / 385806744**.
3. Check first-run welcome and the genuine account login: title, readable fields,
   keyboard and 2FA. Never share credentials or account-recovery details.
4. Home: Friends Only wordmark, toolbar actions, neutral Story rings, rounded-square
   photos, feed gutters/cards and rounded three-destination navigation. Compare
   both themes and large text. Check selected-state readability.
5. Open/close a Story, swipe a photo carousel, like/comment/save a post, hold a
   playing video for 2×, release/cancel, background/resume and watch a video repeat.
   Home/profile taps and horizontal swipes must still respect the Reels blocks.
6. Check Direct, an existing group, sending/receiving, keyboard/Back, profile and
   Settings. Recommendations must still respect the existing following filter.
7. Report PASS/FAIL with the build code and exact taps. A screenshot of the chrome
   is useful; redact private names, messages and account data. Do not infer the
   cause of a crash from Android's generic error dialog alone.
