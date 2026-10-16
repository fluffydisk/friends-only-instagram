# Galaxy S24+ / account compatibility checklist

**Not executed in this cloud environment.** Record the date, app version, APK
SHA-256, Android/One UI version, Android System WebView version, website
language, and enabled filters when testing. Avoid putting credentials, private
screenshots, chat text, cookies, or account identifiers in issue reports.

1. Install the APK and launch it online. Confirm the native Home/Messages bar
   and Instagram website load. Check status/navigation bar and keyboard insets
   in portrait and landscape, plus system light/dark mode.
2. Sign in only through Instagram’s web page. Complete challenges normally if
   offered; stop on a WebView restriction rather than attempting a workaround.
   Check that the website is Instagram’s real page. A failed login is a
   compatibility finding, not grounds to capture sessions from another app.
3. Home: open a friend’s Story, advance/back, react if desired, and return.
   Open ordinary photo and video posts from followed accounts. Check play,
   scrolling, likes, comments, and saved state. Confirm normal videos remain.
4. Verify Reels/Explore navigation is hidden. Disable each toggle and confirm
   the original entry returns; re-enable it. Confirm individual shared reel
   posts and Stories still open. If the page has unfamiliar markup, record
   that filtering missed it rather than assuming success.
5. Leave experimental suggestions off initially. If you enable it, check exact
   labeled recommendations against visible content with filtering disabled.
   Verify friends’ ordinary posts stay visible. Disable it on ambiguity. Do
   not claim following-only content based on these filters.
6. If Instagram presents a Following feed through its own menu, select it and
   document what was actually observed. Do not assume chronological ordering
   or exclusivity without evidence.
7. Messages: open an existing one-to-one conversation and an existing group.
   Send a text to a consenting friend/test group, verify receipt from the
   normal Instagram client, and receive a reply. Check typing, keyboard
   visibility, scrolling, unread state, and back navigation. Uploads, calls,
   microphone/camera, and notifications are outside this MVP.
8. Check Android back inside a Story, post, conversation, and initial page.
   Rotate the phone and switch themes. Background/resume the app; ensure media
   pauses appropriately and navigation still works.
9. Force stop, reopen, and reboot the phone. Record whether the Instagram
   session persists; Instagram may expire/revoke it independently. Logging out
   and clearing Android app storage should remove the local session.
10. Turn off networking, reload, and confirm an error/retry path. Restore the
    network and retry. Check external HTTPS links open outside this WebView
    without exporting session data. Test slow connections and a long scroll
    for filter delays or excessive resource use.

Mark each item PASS / FAIL / BLOCKED, with reproduction steps. Do not mark a
feature tested merely because its navigation button opens a URL. If login is
blocked, dependent Stories/feed/Direct checks remain BLOCKED or UNVERIFIED.
