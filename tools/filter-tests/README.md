# Filter contract and fixture tests

Run `npm ci && npm test` in this directory (Node.js 20+). These offline jsdom
fixtures verify our filtering decisions and controller lifecycle. They do not
verify Instagram's current markup, authentication, Stories, or Direct.

The Android host evaluates `app/src/main/assets/filters.js` only on trusted HTTPS
Instagram pages, then calls:

```javascript
window.FriendsOnlyFilters.configure({
  hideReels: true,
  hideExplore: true,
  hideSuggestions: false
});
```

Repeat `configure` after document loads, SPA navigation, and setting changes.
Repeated evaluation is idempotent. Omitted settings retain their previous value.
`disconnect()` stops observation and restores all changes; a later `configure`
starts it again. All hiding uses a removable CSS marker, not deleted DOM nodes.

Rules deliberately preserve ambiguous content:

- Reels/Explore: only exact HTTPS Instagram collection destinations `/reels[/]`
  and `/explore[/]`, inside a nav/role=navigation/header, and outside articles or
  dialogs. Links containing image/video/audio content are preserved. On Home,
  an exact accessible `Reels`/`Explore` label on the anchor or an SVG icon can
  qualify without a semantic nav ancestor. This fallback is a structural
  heuristic, not evidence from a live authenticated Instagram session.
- Individual `/reel/id` links, normal video posts, Stories, and content links
  inside articles/dialogs are preserved. No route blocking or media removal.
- Recommendation filtering is **off by default**, English only, and limited to
  Home `/`. It requires a leading standalone `Suggested posts` or
  `Suggested for you` heading directly inside a single-post article, followed by
  a separate author header with a profile link, media, and exactly one distinct
  post permalink. A caption containing those words, a generic suggestion
  section, a multi-post section, or an unfamiliar structure is preserved. This
  is not a followed-account detector or a guarantee of a friends-only feed.
  There is no live Instagram evidence that this narrow rule currently matches.
- Auth routes are left alone. Recommendation rules never inspect Direct,
  Stories, profiles, or auth pages. Nav rules may inspect link destinations in
  the page but do not read message text, credentials, cookies, or account state.

A single observer watches child additions/removals and only the href, role,
and aria-label attributes. Text changes are observed only on Home while the
optional recommendation filter is enabled. Its own marker/style writes are excluded. Mutations
are batched once per 120 ms; at most 32 roots are queued before a single full
scan replaces the batch. Removed elements are released. Site changes may make
filters miss content; the app's filter toggles restore the original interface.
The script has no Android bridge, network calls, persistent storage, telemetry,
or logging.
