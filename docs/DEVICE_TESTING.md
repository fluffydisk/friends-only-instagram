# Galaxy S24+ / account compatibility checklist

The user reported the earlier MVP working on their phone; no feature-level
results were supplied. **This 0.2.0 checklist has not been executed in the cloud.**
Record the date, app version/APK SHA-256, Android/One UI version, Android System
WebView version, system theme/language, website language, and enabled settings.
Keep credentials, private screenshots/messages, cookies, and account identifiers
out of issue reports.

1. Install the APK and launch it online. Confirm the small app bar, settings
   icon, Feed/Messages destinations, and real Instagram content. Reload is in
   Settings. Open Instagram settings from the settings sheet and check that
   account controls remain available. Test system light/dark, English/Turkish,
   large fonts, TalkBack, portrait/landscape, and gesture/three-button navigation.
   Check contrast, selected tabs, touch targets, and status/navigation insets.
2. Sign in only through Instagram’s web page. Complete challenges normally if
   offered; stop on a WebView restriction rather than attempting a workaround.
   Check that the website is Instagram’s real page. With minimal appearance on,
   login, challenge, and account-page controls should remain unchanged. A failed
   login is a compatibility finding, not grounds to capture another app’s session.
3. Feed: open a friend’s Story, advance/back, react if desired, and return.
   Open ordinary photo and video posts from followed accounts. Check play,
   scrolling, likes, comments, and saved state. Confirm normal videos remain;
   Story/media viewers, close controls, and dialogs must remain usable.
4. Verify Reels/Explore navigation is hidden. Disable each toggle and confirm
   the original entry returns; re-enable it. Confirm individual shared reel
   posts and Stories still open. If the page has unfamiliar markup, record
   that filtering missed it rather than assuming success. Compare the extra
   discovery switch on/off separately; check global shortcuts disappear while
   post actions, feed menus, Direct controls, and account access remain.
5. Compare experimental suggestions off/on against visible labeled content.
   The default is on without a stored preference; verify a saved off value
   persists across reopening/upgrading. Only narrow English/Turkish leading
   headings with an author header/media/permalink qualify. Captions with similar
   words and friends’ ordinary posts must remain visible. Disable on ambiguity.
   Do not claim following-only content based on these filters.
6. If Instagram presents a Following feed through its own menu, select it and
   confirm its menu remains accessible and document what was observed. Do not
   assume chronological ordering or exclusivity without evidence, or use guessed
   routes to force it.
7. Messages: open an existing one-to-one conversation and an existing group.
   Send a text to a consenting friend/test group, verify receipt from the
   normal Instagram client, and receive a reply. Check typing, keyboard
   visibility, scrolling, unread state, and back navigation with minimal
   appearance on/off. On Android 11+, native bottom navigation should hide while
   typing and return when the keyboard closes. Ensure the composer and last
   message are not obscured or separated by excessive blank space. Older
   Android versions need a normal-resize check. Uploads, calls,
   microphone/camera, and notifications are outside this version.
8. Check Android back inside a Story, post, conversation, and initial page.
   Rotate the phone and switch themes. Background/resume the app; ensure media
   pauses appropriately and navigation still works.
9. Force stop, reopen, and reboot the phone. Record whether the Instagram
   session persists; Instagram may expire/revoke it independently. Logging out
   and clearing Android app storage should remove the local session.
10. Turn off networking, reload, and confirm an error/retry path. Restore the
    network and retry. Check external HTTPS links open outside this WebView
    without exporting session data. Test slow connections and a long scroll
    for filter delays or excessive resource use. If a renderer crash occurs
    naturally, check its recovery message/retry; otherwise leave that path
    unverified.
11. Compare Minimal appearance on/off on Feed and Messages. Check colors,
    typography, borders, and unchanged media. Recognized duplicated global
    navigation may disappear; unfamiliar layouts should remain usable. Turning
    the option off must restore its styling/navigation changes while independent
    filters keep their selected settings. Check menus and Direct’s composer.
12. In Settings, choose **Show original layout**. Confirm all five switches turn
    off and the website styling/navigation returns. Re-enable the desired
    switches. Rotate the phone or change the system theme while Settings is
    open; confirm it closes safely and reopens with saved preferences.

Mark each item PASS / FAIL / BLOCKED / UNVERIFIED, with reproduction steps.
Do not mark a feature tested merely because its navigation button opens a URL. If login is
blocked, dependent Stories/feed/Direct checks remain BLOCKED or UNVERIFIED.

For a regression, include the route category (Feed, Story, Direct, or account),
language, toggles, and steps. Disable Minimal appearance and the relevant filter
separately to locate the cause without sharing private content. Fixture coverage
alone does not establish that Instagram’s live interface works.
