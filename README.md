# Friends Only

**Current direction: native Instagram APK patches (0.3.0 alpha).** The new
[native-patches project](native-patches/README.md) compiles local native UI and
discovery filters into a patch bundle. It preserves Instagram's native client;
it requires a compatible original APK and independent signer verification.
No patched Instagram APK or phone compatibility is claimed without that input
and testing. The cloud workflow builds the patch bundle without an APK.

The WebView implementation below is retained as an earlier working approach
and fallback. Its APK is a separate product from the native patch experiment.

## Earlier WebView app · Android MVP 0.1.0

A small native Android shell around Instagram’s own website, with Home and
Messages navigation and conservative local interface filters. No backend, paid
service, recreated feed, dummy conversations, or private Instagram API.

**An APK has been compiled in Codex Cloud. Instagram account functionality has
not been tested on Android.** This is an installable MVP for compatibility
testing, not a promise that Instagram permits WebView login or that the feed
contains only followed accounts. See [verification](docs/VERIFICATION.md) for
the exact evidence and [phone checklist](docs/DEVICE_TESTING.md) before relying
on the app.

## What is implemented

- **Home** opens `https://www.instagram.com/`. Instagram supplies the real feed,
  Stories, interactions, login, and account UI.
- **Messages** opens `https://www.instagram.com/direct/inbox/`. Instagram supplies
  existing individual and group conversations and its message composer when
  supported by the website.
- Exact Reels and Explore navigation destinations are hidden by default, using
  structural rules. Ordinary video posts, individual reel permalinks, and
  Stories are preserved. Filters do not block those URLs if opened another way.
- **Filters** provides reversible Reels/Explore toggles and an experimental
  explicit-suggestion filter, **off by default**. That filter uses a narrow
  English heading/article signature only on Home. Unknown structures remain
  visible. It cannot determine who you follow.
- Persistent first-party cookies and website storage, back navigation,
  reload/retry, progress indicator, main-page error banners, renderer recovery,
  Activity state restoration, system insets, and native light/dark themes.
- Instagram HTTPS pages stay inside the app; external web links open an Android
  browser/compatible handler. Unsupported schemes are rejected. Cross-domain
  authentication may consequently require a browser and may not return a
  session to this WebView. Restrictions are not bypassed.

The app does not force an undocumented chronological-feed route. Select
**Following** through Instagram’s own Home menu if that option is exposed to
your account. Its availability and ordering are unverified. An app called
Friends Only does not make Instagram’s default feed followed-accounts-only.

## Start from zero: cloud build and phone installation

No local Android Studio, JDK, SDK, or computer build is required.

1. Open [fluffydisk/friends-only-instagram](https://github.com/fluffydisk/friends-only-instagram),
   the source repository for this project. For future cloud development,
   connect GitHub in Codex Cloud and select this repository. Use the platform’s
   authorization flow; do not paste access tokens into chat or source files.
2. On GitHub, open **Actions → Android APK**. Enable Actions if prompted. A push
   to `main` triggers the workflow. You can also choose **Run workflow** on main.
   Pull requests run the checks too. No repository secrets are needed to build.
3. Open a successful run. Under **Artifacts**, download
   `friends-only-debug-RUN_NUMBER` and extract `app-debug.apk` from the ZIP.
   GitHub requires signing in to download workflow artifacts. Artifacts are
   retained for 30 days; run the workflow again if one expires.
4. Transfer/download that APK to your Samsung Galaxy S24+. Open it from Files
   and allow **Install unknown apps** for that browser/file manager when Android
   asks. Samsung Auto Blocker or a managed-device policy may prevent sideloading;
   follow the device’s installation controls. Restore your preferred install
   permissions after installation.
5. Launch **Friends Only**, sign in **only on Instagram’s actual web page**, and
   run the [device checklist](docs/DEVICE_TESTING.md). Update Android System
   WebView/Chrome on the phone first. If Instagram refuses WebView login, stop
   and use its supported browser/app; this client offers no bypass.

If you already received `friends-only-0.1.0-debug.apk` with this delivery, you can
install it directly using steps 4–5.

Debug APKs are for personal testing. Separate clean build environments generate
different debug signing keys. An update may require uninstalling the previous
build, which clears the app’s local Instagram session and settings. Version
0.1.0 does not set up a stable release signing key or Play Store publishing.

## Build and check from any prepared cloud environment

Pinned compatible toolchain:

| Component | Version |
| --- | --- |
| JDK | 17 |
| Gradle Wrapper | 8.11.1, official distribution SHA-256 pinned |
| Android Gradle Plugin | 8.9.2 |
| Kotlin Android plugin | 2.1.20 |
| Compile / target SDK | 35 / 35 |
| Android Build Tools | 35.0.0 |
| Minimum Android | API 26 / Android 8.0 |
| Filter-test Node.js | 20+ (CI uses 22) |

Install Android command-line tools and accept their licenses, then install
`platforms;android-35` and `build-tools;35.0.0`. Set `JAVA_HOME` to JDK 17 and
`ANDROID_HOME` to the SDK, or set `sdk.dir` in an untracked `local.properties`.
The GitHub workflow performs this setup automatically.

```sh
npm --prefix tools/filter-tests ci --ignore-scripts
npm --prefix tools/filter-tests test
./gradlew --no-daemon testDebugUnitTest lintDebug assembleDebug
```

The APK is `app/build/outputs/apk/debug/app-debug.apk`. JUnit and lint reports
are under `app/build/reports/`; Actions also uploads those reports. The cloud
session used for this delivery has temporary build-tool setup described in
[verification](docs/VERIFICATION.md); it is not required by the repository.

## Structure and filter maintenance

```text
app/src/main/java/dev/friendsonly/android/
  MainActivity.kt                  Native UI, Android lifecycle, back handling
  navigation/NavigationPolicy.kt  URL classification and destinations
  web/                            WebView settings and website callbacks
  filter/                         Local preferences and asset injection
app/src/main/assets/filters.js     Idempotent, reversible DOM filtering
app/src/test/                      JVM navigation/security tests
tools/filter-tests/                Offline DOM fixtures and filter contract
.github/workflows/android.yml      APK build + checks + downloadable artifacts
docs/                             Actual verification and physical-device checks
```

The controller uses one debounced observer and bounded mutation queues. It
does not remove page nodes, scan remote endpoints, query following lists, or
extract account data. Android reinjects after load, history changes, resume,
and settings updates. See the precise
[filter contract and limits](tools/filter-tests/README.md) before changing
selectors. Fixture coverage is evidence about our rules, not Instagram’s
current rendered DOM. Disable filters if Instagram changes its interface.

## Privacy and security

- The only manifest permission is `INTERNET`. No analytics SDK, advertising,
  backend, third-party collection endpoint, credential form, JavaScript bridge,
  request-body interception, or cookie/token/message export is implemented.
- Instagram receives your login and usage through its own website and applies
  its own tracking, cookies, privacy terms, and content policies. The app’s
  filtering does not disable Instagram’s tracking or recommendation generation.
- Website cookies/storage remain in Android’s WebView sandbox. Android backups
  and device-transfer backups are disabled/excluded. Logout through Instagram;
  Android Settings → Apps → Friends Only → Storage → Clear data resets all
  local website data/settings if needed.
- First-party cookies and DOM storage are enabled. Third-party cookies are
  disabled. No user-agent spoofing, authentication bypass, TLS-error bypass,
  unsafe file access, or cleartext Instagram traffic is allowed. WebView
  debugging is disabled even in this debug APK. Android debug builds themselves
  are debuggable, so use trusted devices and computers for personal testing.
- Camera, microphone, location, file picking/upload, downloads, and push
  notifications are not implemented. Text composing is delegated to Instagram.
  No runtime permissions are requested. Media playback requires a user gesture.

## Known limitations and Instagram compatibility

- Authentication, session persistence across process/device restart, Stories,
  feed interactions, text sending, group chats, and S24+ usability remain
  **unverified** until tested on a real device with your account.
- Instagram may refuse WebView authentication, present challenges, rate-limit,
  change its DOM, or omit mobile-web features. External/Facebook authentication
  and cross-site challenges may fail. The website may require opening content
  in the official app. No bypass or private API fallback is included.
- Reels/Explore selectors are conservative heuristics. They can miss entries
  with unfamiliar markup. Content shared by friends can include reel posts;
  those are intentionally retained. Discovery content can still be reached.
- The optional suggestions filter has no authenticated live-DOM validation and
  may match nothing. English labels only; there is no verified friends-only
  feed, following relationship lookup, or guarantee against recommendations.
- Native chrome follows Android’s light/dark mode; Instagram controls its own
  page theme and layout. The keyboard, rotation, media, links, and performance
  still require device testing. Debug signing and artifact expiry are covered
  above. See the repository’s Actions page for the current cloud build status;
  local verification does not establish that a GitHub workflow succeeded.
- This project is independent of Instagram/Meta and uses its own name/icon.
  Instagram is a trademark of its owner. Website access and interface changes
  are subject to Instagram’s terms and policies. This project makes no claim
  of approval, continued compatibility, or official affiliation.

## v0.2.0 roadmap

1. Record real Galaxy S24+ login, Stories, feed, Direct/group, lifecycle, and
   accessibility results; fix issues supported by device evidence.
2. Validate current navigation/suggestion signatures against permitted manual
   browsing and add regression fixtures, while preserving ambiguous posts.
3. Explore Instagram’s officially exposed Following control if available;
   avoid guessed routes and unsupported feed guarantees.
4. Add stable release signing and versioned upgrade documentation. Consider
   user-selected attachment uploads only if the official website supports them.

Push notifications remain outside this MVP.
