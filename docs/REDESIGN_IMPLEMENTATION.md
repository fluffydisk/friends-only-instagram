# Friends Only native design — 0.4.0-alpha.1 (previous iteration)

**Current changes:** see [the screenshot-led alpha.2 implementation](REFERENCE_DESIGN.md).
The account disclosure and incomplete native-screen coverage described here still apply.
Alpha.2 replaces the palette, navigation treatment and supported feed-action text styling.

[Download the built APK](https://github.com/fluffydisk/friends-only-instagram/actions/runs/39120305766/artifacts/12133549996) · [successful GitHub run 78](https://github.com/fluffydisk/friends-only-instagram/actions/runs/39120305766)

Local and CI checks passed: **167 Python tests and 897 Java/Kotlin assertions**.
Both builds produced signed APKs and passed output guards. **No phone opening,
rendering, authentication or interaction test has been performed for this version.**


This is the first implementation of the [redesign plan](FRIENDS_ONLY_REDESIGN_PLAN.md),
not a claim that every planned screen has been replaced. It retains the supported
Instagram 451 native account, media and Direct implementation.

## Implemented in source

- An independent, scrollable first-run welcome with one **Connect with Instagram**
  action. It hands off to the original native entry and its genuine account login
  or existing session; it contains no credential field. Later launcher opens skip
  the welcome. Incoming link/notification intents pass through to the original
  entry with their action, data and extras preserved; their payload is not logged.
- Own full-page **Settings** replacing the small dialog: following-only switch and
  its limits, video control information, appearance explanation, honest account
  disclosure, explicit browser account/privacy links and opt-in technical-info
  copying. Copy contains only Friends Only build/code, device model and Android
  version, not logs, cookies, identifiers or conversation data. Browser account
  management is not logout from the native session.
- Local, OFL-licensed **Manrope** type family with warm light/dark surfaces; no
  remote font fetch. Header, text navigation, welcome/settings and the supported
  native feed/Direct/profile/Story-label layouts share the type family.
- Flat **Friends / Chats / Me** navigation, localized in Turkish and German, with
  a quiet active underline instead of a filled selected pill. The supported Home
  header uses **Friends / Arkadaşlar / Freunde** and an own settings control.
- More spacing on supported feed headers, inbox rows and profile-info rows;
  larger profile names. Exact feed like/share counters and repost/insights controls
  are hidden in supported feed scopes. Essential Like, Comment, Send, Save,
  overflow and existing native media/gesture handling remain.
- Text treatments on supported native image buttons for Like/Reply/Send. They keep
  the real native drawable state and existing listeners/accessibility. Narrow
  native button variants or font scale above 130% retain native icons instead of
  squeezing text. Unsupported variants can therefore still show native artwork.
- Exact profile follower/post popularity counters, Threads shortcut and selected
  profile upsells are hidden; the following control and safety/request/teen-account
  notices are not on the hide list. Exact composer AI-discovery/prompts listed in
  policy are hidden; message input, send, gallery and voice controls remain.
- Existing following filter, three-destination navigation, immersive Reels block,
  feed hold-to-2× and same-video loop patches remain selected.

## Source and scope

`FriendsDesignPatch.kt` verifies 18 original layout IDs and the two original
resource-ID inflater signatures, register/return/call/try-block contracts. It
also verifies the exact native feed-action holder before appending a scoped
presentation hook. Other inflater overloads and unknown layouts are untouched.
With `attachToRoot=true`, Android can return the parent; this case is deliberately
ignored rather than treating a whole screen as the new row.

`ScreenDesign` styles only supported attached row roots. Its scans stop at nested
content lists, cap depth/nodes, coalesce work and throttle each row to at most
four updates per second. It adds no Activity-wide observer, poller, new API call,
model parser, message reader or generic gesture interception. Native rows built
through unsupported factories can retain original presentation.

`DesignSystem`, `WelcomeActivity`, `FocusSettings` and `FeedActionLabel` separate
our own UI from the native compatibility hooks. Font and license hashes are
checked both when applying the patch and in the final signed-APK validator.
The prior mandatory nonzero theme-color resource guard remains enabled.

## Still outside this build

- Separate vertical Stories list and a working Posts/Stories switch. The existing
  native tray/viewer remains; no inactive placeholder switch has been added.
- A wholly new profile content list replacing Instagram's grid.
- Fully redrawn chat bubbles, media composer, group details or Story viewer.
  Their original native rendering/state remains, with scoped typography changes.
- New square avatars across every native image implementation, a global custom
  theme selector or native-session logout in our settings.
- Proven chronological/complete following-only coverage or a guarantee that no
  remaining Instagram-style element can appear. Existing data can lack following
  metadata and produce a short/empty feed. Turning the filter off permits recommendations.

These need further native component work and physical-device evidence. This APK
must not be described as the finished end-to-end design or an independent backend.
Authentication, required legal disclosures and original Instagram networking,
permissions/data processing/terms remain. Stop on a platform rejection; no bypass
is introduced.

## Device check

Install the new personal-test APK through the normal Files installer. A different
CI signing key requires removing only the previous **Friends Only** first, which
clears its local session/settings; official Instagram can remain installed. Do not
use the old fixed-package installation helper.

On the Galaxy S24+ / Android 17 / One UI 9, check:

1. Cold first open: own welcome → genuine login or existing session → Home;
   second launcher open skips welcome. Check a normal post/chat deep link too.
2. Light/dark appearance, German/Turkish labels, TalkBack, 200% font size and
   system/gesture bars. Welcome/settings must scroll without cutting off actions.
3. Home feed header, actions/liked state, saved posts and comments; photo carousel,
   inline video hold/release/cancel/background, same-video loop; no Reels swipes.
4. Stories playback/reply and inbox → individual/group thread → sending, failed
   send, keyboard, photo/voice and Back. No real private conversation is needed in
   a screenshot shared with us; redact names and messages.
5. Profile following/DM and other-person overflow for Block/Report; own-profile
   menu → our settings; refresh after changing following filter.
6. Settings Back, browser account link, Copy technical information, rotation and
   background/resume. Report which build/code you installed and the exact steps.

Automated checks/builds establish source/output contracts, not successful login,
rendering, accessibility or phone interactions. Delivery evidence is recorded in
[evidence/native-design-2026-10-18.json](evidence/native-design-2026-10-18.json).
