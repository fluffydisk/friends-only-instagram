# Minimal design · 0.2.0

Friends Only puts Feed and Messages within easy reach and moves occasional
actions into Settings. The application owns the native app bar, icons, bottom
navigation, settings, loading indicator, and error states. Instagram still
supplies and operates the real feed, Stories, and chat interface inside WebView.

## Native surface

- Compact app bar with contextual back and a settings icon.
- Two bottom destinations with icons and a clear selected state.
- System light/dark colors, restrained typography, and English/Turkish labels.
- A settings sheet for appearance, independent filters, reload, and Instagram
  account settings. Reload no longer occupies the permanent app bar.
- Keyboard-aware bottom navigation on Android 11+; older versions retain normal
  resize handling. Rotation, font scaling, accessibility, and Samsung keyboard
  behavior need device verification.

## Local website treatment

`focus.css` supplies a limited Home/Direct palette, typography, and article
borders. `filters.js` activates it through reversible page markers. It does not
replace posts or messages with copied data, add a JavaScript bridge, read
credentials, or call Instagram APIs. Login/challenges, Stories, and media
viewers keep their website appearance and controls.

**Minimal appearance** controls the cosmetic changes and hiding of recognized
duplicated global navigation. A navigation group is hidden only when it matches
a coherent supported layout and necessary website controls can be preserved.
Unknown layouts remain visible. Broadly hiding every `header`, navigation-like
container, or media element would risk removing author headers, Story controls,
or Direct actions and is deliberately avoided.

Separate Reels, Explore, and extra-discovery switches target recognized global
shortcuts. These settings hide interface entries rather than block routes or
delete content. Ordinary video posts and individual shared reel links remain
available. Native Settings retains an Instagram account-settings action even
when duplicated site navigation is hidden.

The suggestion switch defaults on without a stored preference and respects a
saved off value. It uses only a narrow explicit English/Turkish heading at the
start of a single-post article, followed by a separate author header, media,
and one post permalink. Unfamiliar structures and captions containing similar
words remain visible. This rule is experimental and cannot establish following
relationships. Instagram’s own Following menu is preserved where exposed; the
app does not automate an undocumented Following route.

## Reversibility and evidence

Appearance and filters have independent settings. Turning Minimal appearance
off restores its changes; other enabled filters continue applying. The page
controller batches dynamic updates, reuses one observer, and restores markers
when settings or route change. No backend or additional collection service is
involved.

**Show original layout** turns off all five appearance/filter switches together.
The native Feed/Messages shell remains available. The Activity dismisses its
settings window when it is destroyed, including rotation/theme changes.

Offline DOM fixtures establish selector decisions, reversible toggles, route
guards, and controller behavior. They cannot establish the current Instagram
DOM or account compatibility. The earlier version’s user-reported phone success
is a useful baseline; the redesign still needs the
[device checklist](DEVICE_TESTING.md). Actual automated checks and APK results
belong in the [verification record](VERIFICATION.md).
