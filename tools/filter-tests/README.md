# Filter and appearance contract

Run `npm ci && npm test` here with Node.js 20+. The 40 offline jsdom fixtures
check filtering decisions, CSS application, and controller lifecycle. They do
**not** validate Instagram's current authenticated markup, login, Stories, Direct,
or the physical Android rendering and keyboard behavior.

The Android host evaluates `app/src/main/assets/filters.js` only on trusted HTTPS
Instagram pages, then supplies the bundled `focus.css` as a raw string:

```javascript
window.FriendsOnlyFilters.configure({
  hideReels: true,
  hideExplore: true,
  hideSuggestions: true,
  hideDistractions: true,
  minimalAppearance: true,
  darkMode: false,
  styleSheet: "/* contents of focus.css, quoted by the Android host */"
});
```

These are the defaults except `styleSheet`, which defaults to an empty string.
Omitted options retain their previous value. `styleSheet` contains CSS text,
not a path or URL. The host JSON-quotes it; this script never fetches a stylesheet.
The host must call `configure` after document loads, SPA navigation (including
`doUpdateVisitedHistory`), setting changes, and system-theme changes. The observer
also detects route changes during DOM updates; popstate/hashchange are observed.
There is no history patch or polling for pushState without DOM mutations.

Repeated evaluation/configuration is idempotent and uses one observer.
`disconnect()` stops observation/listeners and restores all app-owned markers
and styles. A later `configure` restarts it. Existing inline styles, elements,
and preexisting marker attributes are preserved. Disabling all five presentation
and filtering switches removes both app styles and restores every app change.

## Appearance and duplicate navigation

On **Home `/` and Direct `/direct` or `/direct/...` only**, minimal appearance
sets `html[data-friends-only-appearance="light|dark"]` and adds a single
`#friends-only-appearance-style`. The CSS changes existing Instagram RGB color
variables, system typography, and feed article backgrounds, borders, and corners.
It does not set media dimensions, overflow, fixed positioning, or composer sizes;
it does not recolor individual SVGs/buttons or invent content.

Account/login/challenge/checkpoint/oauth routes receive **no app style or marker**.
Stories, individual post/reel viewers, and profiles keep the site's presentation.
On an SPA route change, the theme is removed before applying new route rules.

Duplicate navigation is recognized only inside a semantic `nav`/`role=navigation`
with at most 20 links and three independent exact Instagram destination signals:
Home `/`, Messages `/direct[/]` or `/direct/inbox[/]`, and Reels `/reels[/]` or
Explore `/explore[/]`. Home/Messages with query strings or hashes are preserved,
including `/?variant=following`. Within a recognized group, minimal appearance
hides duplicate Home/Messages links and explicitly labelled own-profile entries
(`Profile`, `Your profile`, `Profil`, `Profilin`); native account access remains
available in the host. Reels/Explore/other links obey their separate switches.
The entire nav wrapper disappears only when every link is identified and no
independent controls, articles, dialogs, or unowned image/media would disappear.
Following menus, conversation headers, profile controls, and unknown markup stay.

## Content rules

- Reels/Explore: only exact HTTPS Instagram collection destinations `/reels[/]`
  and `/explore[/]`, inside a nav/role=navigation/header, and outside articles or
  dialogs. Links containing image/video/audio content are preserved. On Home,
  an exact accessible `Reels`/`Explore` label on the anchor or an SVG icon can
  qualify without a semantic nav ancestor. This is a structural heuristic,
  not evidence from a live authenticated session.
- Extra distractions: only exact Instagram `/create/select[/]`,
  `/accounts/activity[/]`, `/shop[/]`, `/shopping[/]`, `/explore/people[/]` links
  in nav/role=navigation/header, outside articles/dialogs and without media.
  No removal based on arbitrary message text, Threads, or Meta AI labels.
- Individual `/reel/id` links, ordinary videos, Stories, shared media, post
  actions, chat/group controls, and composers remain. No route blocking,
  deleted nodes, account relationship lookup, or media removal is performed.
- Recommendations: enabled by default but independently switchable, limited to
  Home `/`, and deliberately narrow. A leading standalone English heading
  `Suggested posts`/`Suggested for you` or Turkish `Önerilen gönderiler`/
  `Senin için önerilenler`/`Senin için öneriler` must sit directly inside a
  single-post article, followed by a separate author header containing a profile
  link. Media and exactly one distinct post permalink are required. Captions,
  generic suggestion sections, multi-post sections, unknown labels/structures,
  and recommendation-like text in Direct are preserved. This is **not** a
  followed-account detector or a guarantee of a friends-only feed. No live
  Instagram evidence confirms that these narrow rules currently match.

One observer watches child additions/removals plus href, role, and aria-label.
Text changes are observed only on Home with recommendation filtering enabled.
Its own marker/style changes do not feed back into observation. Mutations are
batched once per 120 ms; at most 32 roots are queued before a single full scan
replaces the batch. Small nav groups are rechecked when their signature changes.
Detached elements are released. Site changes can make filters miss content;
settings restore the original interface when needed.

There is no Android bridge, network call, persistent storage, telemetry, logging,
credential/cookie access, or message collection in these assets.
