# Friends Only · native app prototype

**Native design 0.4.0-alpha.1 (code 385806743):** own first-run welcome/full-page
settings, bundled Manrope, flat text navigation and scoped feed/Direct/profile
styling. Read the [implementation and remaining design work](../docs/REDESIGN_IMPLEMENTATION.md).
This iteration adds an eleventh selected patch and keeps the existing theme
resource-ID guard. Compilation/static checks do not establish phone rendering,
login or interaction success.


Friends Only keeps Instagram's native post, Story and chat components beneath
our own app identity and selected local interface changes. It targets the
verified original **Instagram 451.0.0.20.80** Android input. The earlier WebView
application and workflow remain separate.

**Alpha.6 now opens on the user's Galaxy S24+ / Android 17 / One UI 9.** The user
reports that swiping from Home still opens Reels; alpha.7 changes the actual
native navigation list and viewer entry point to address that defect. Opening
is user-reported evidence, not a separate verification of Stories, messages,
login/challenges or following-only filtering.

<!-- ALPHA8_DELIVERY_START -->
**[Download alpha.8 APK](https://github.com/fluffydisk/friends-only-instagram/actions/runs/39114270905/artifacts/12132185041)** — extract the ZIP and open
`friends-only-personal.apk` with the normal file manager. Version code:
**385806742**. Feed videos now use the existing inline loop instead of opening
**View in Reels / Watch again** end cards. No fullscreen viewer, timer or
synthetic replay tap is added.

Local and [GitHub APK builds](https://github.com/fluffydisk/friends-only-instagram/actions/runs/39114270905) passed all **ten patches**, **163 Python
tests and 615 Java/Kotlin assertions**, signatures/identity, 16 KiB alignment
and the emitted-theme guard. **Phone playback is still unverified.** Remove
only the older **Friends Only** app for this new CI signing key; its local
session/settings are cleared. Official Instagram may stay installed. Do not
use the legacy v2 installation helper.
<!-- ALPHA8_DELIVERY_END -->

The user's end-card report does not identify its APK/run. It supplies an
issue to reproduce, not version-specific alpha.7 success evidence.

<!-- ALPHA7_DELIVERY_START -->
**[Download alpha.7: `friends-only-personal-test-apk-67`](https://github.com/fluffydisk/friends-only-instagram/actions/runs/39081645229/artifacts/12119595829)**. Extract the
ZIP and install `friends-only-personal.apk` through the normal file manager.
Its package is **`dev.friendsonly.instagram`**, version code **385806741**.

Local and [GitHub APK builds](https://github.com/fluffydisk/friends-only-instagram/actions/runs/39081645229) passed all **nine patches**, **163 Python
tests**, **155 Java + 403 Kotlin assertions**, signatures/identity, 16 KiB
alignment and the mandatory **7/47/14** compiled-theme guard. **Alpha.7 phone
installation, opening and new behavior remain unverified.** The
[alpha.6 fallback, artifact 62](https://github.com/fluffydisk/friends-only-instagram/actions/runs/39064660107/artifacts/12112951982) is known to open, with the Home-swipe
Reels defect still present.
<!-- ALPHA7_DELIVERY_END -->

CI builds currently use disposable signing keys. Remove only the older
**Friends Only** native app before installing a new-key build; this clears its
local session/settings and may require signing in again. Official Instagram
can remain installed. Do not reset the phone or use the legacy v2 diagnostic
helper, whose fixed package hint is incompatible with our app identity.
Artifacts require GitHub sign-in and are retained for 7 days.

**Alpha.5 is withdrawn:** artifact 55 installed normally but immediately closed
on opening. It violated a confirmed native theme resource-ID contract.
Alpha.6 corrects that contract, and the user's subsequent opening report is
positive; no phone stack trace establishes the defect as the sole historical
crash cause. Alpha.3 is an older known-to-open fallback and alpha.4 remains
untested on the phone. Full historical hashes and limits are in
[VERIFICATION.md](VERIFICATION.md).

## Alpha.7 behavior and scope

These source changes still require the alpha.7 device checks below:

1. **Actual native navigation:** supply only `FEED`, `DIRECT`, `PROFILE`, in
   that order, in the shared factory used both at initial construction
   and later navigation refreshes, before native adapter/pager consumers use it.
   Default to `FEED`; unsupported native destination requests are normalized to
   Home. This removes the Reels, Explore/Search and Create pages rather than merely covering their bottom icons. Horizontal gestures
   are not globally blocked, so ordinary post carousels remain native.
2. **Reels viewer blocked:** the three supported central Clips/Reels viewer
   launchers return without opening for every source, including Home, profiles,
   Direct shares, result-returning launches and unknown sources. Old filter
   preferences cannot reopen it. Video posts are not deleted; eligible inline
   feed playback remains available.
   Reels shared in chats consequently cannot open their immersive viewer.
   This is a navigation restriction, not deletion of arbitrary native classes.
3. **Own settings:** Home's Settings button/long-press and the supported native
   own-profile hamburger and settings-action launchers open a Friends Only
   dialog before the original Meta/SSO/Threads settings menus are built. The
   other-person profile options branch remains native. The native settings-action
   hook is the fallback; no generic profile-menu view binding is installed.
   Our dialog contains only
   **Only accounts I follow / Yalnızca takip ettiğim hesaplar**, a hold-to-2×
   usage hint and Done. Reels/Explore/Create, appearance and “disable filters”
   switches are removed. Discovery blocking and the own appearance are fixed
   on, including before preferences initialize. The replacement profile entry
   no longer opens Instagram/Meta's general settings hub; this deliberately
   reduces access to account settings through that entry.
4. **Hold-to-2× in the feed:** enable only the existing native feed-video
   long-press speed path, including the middle of a playing video. The native
   matching-media/player checks and release/cancel/recycle reset remain. Its
   dedicated feed-only gate also keeps existing background cleanup enabled;
   shared/global configuration getters are unchanged. It does not add a generic touch listener, enter fullscreen/Reels, or alter
   Story/Direct video handling. The intended speed while held is 2×; actual
   phone gesture behavior still needs verification.
5. **Following feed:** filter the supported Home parser's two ordinary-item
   array boundaries using the primary author's existing
   `friendship_status.following == Boolean.TRUE`. Injected/sponsored, non-post
   and unknown units are rejected. This optional filter defaults on, even
   before settings load; no new API request, following-list scrape, response
   export or credential access is added.
6. **Friends Only appearance and identity:** retain our launcher name/icon and
   independent `dev.friendsonly.instagram` package, warm neutral light/dark
   resources, restrained Story rings, own supported Home/startup header and
   **Friends / Chats / Me** bottom labels (Turkish: **Arkadaşlar / Sohbetler /
   Ben**). Native post, Story and chat content views remain. View decoration
   reuses the existing throttled bounded updates; no additional global observer
   or polling timer is added. The bounded scan decorates only the supported
   Home header/startup logo and hides two exact promotional controls, the
   profile Threads shortcut and Direct composer Meta AI upsell, if encountered.
   It does not bind profile menus or traverse post/chat content lists.
7. **Narrow discovery request blocking:** retain the small exact known
   Reels/Explore collection-request list inside the original request path.
   Other requests pass through. It reads no request/response body or account
   secret. Protected authentication, Home, ordinary media, Stories and Direct
   requests remain outside that endpoint list.

## Following filter and platform limits

If Home becomes short or empty, open Friends Only **Settings / Ayarlar**, turn
off **Only accounts I follow**, then refresh Home. Missing following metadata
is rejected, so even followed-account posts can disappear. Refresh again after
re-enabling. The filter does not purge cached/rendered posts, change server
ordering or prove that every alternate renderer is covered. A fully
friends-only or chronological feed has not been established on a phone.

Home-embedded Story units (`stories_netego`, including `XDTStoriesInFeedItem`,
`reels` and `portable_tray`, plus `tagged_edge_story` / `explore_story`) can lack
a primary media post and be dropped too. They are not proven to be exclusively
recommendations. Standalone Story/Direct/profile parsers and requests are not
patched, but top Stories-tray playback and all Story surfaces remain unverified.

This is not a new backend or a complete replacement native renderer.
Instagram's real account infrastructure, authentication/challenges, necessary
legal disclosures, networking, permissions, tracking and terms remain in
force. We do not globally replace strings or alter user content, login/legal
text, JNI names or authentication/integrity checks. Some original branding and
features outside the exact patched layouts can remain. “Independent app
identity” does not mean independent of Instagram's service, approval by Meta,
or immunity to platform changes or rejection. Stop on a platform rejection;
there is no authentication bypass or new private API client.

The extension adds no permissions, backend, analytics, credential form or
third-party collection. Original Instagram data collection is not removed by
hiding its discovery UI.

## Theme startup regression guard

Keep alpha.6's resource-contract fix: all **47 native semantic color attributes**
are resource references, backed by **14** own palette colors. Do not replace
these attributes with inline ARGB values: native code expects a nonzero
resource ID. The mandatory final APK guard checks **7 semantic style families /
47 nonzero references / 14 exact palette values**, and rejects the actual broken
alpha.5 APK. Alpha.6's independent local audit also preserved all **1,097**
original color IDs and values. Exact scopes and distinct local/CI hashes are
recorded in [verification](VERIFICATION.md); static success alone is not a
rendered UI or device feature test.

## Build only the bundle

The build uses Python 3, **JDK 21**, Android SDK **35 / Build Tools 35.0.0**, and
the public tools pinned in [tool-versions.json](tool-versions.json): Kotlin
**2.4.21**, Morphe desktop/CLI **1.18.1**, and its patcher **1.15.1**. Downloads are
checked against pinned SHA-256 values. APKM application additionally uses pinned
**APKEditor 1.4.9** to merge validated splits. The Python build avoids authenticated
GitHub Packages and needs no package-registry token.

From the repository root, with `JAVA_HOME` and `ANDROID_HOME` configured:

```sh
python3 native-patches/scripts/build.py --check
```

Outputs live under this module:

- `build/friends-only.mpp` — patch bundle, not an APK.
- `reports/build-report.json` — build/check report; inspect its actual result.

The native extension contains local preferences and bounded view hooks. Our
patch definitions are under `patches/`; extension source and policy checks are
under `runtime/`; build/application tools are under `scripts/`.

## GitHub Actions: no local compiler needed

1. Open the repository’s **Actions → Native patches** workflow, defined in
   [native-patches.yml](../.github/workflows/native-patches.yml).
   While the workflow exists only on the feature branch, use its automatic push
   or PR run. GitHub's manual **Run workflow** button becomes available after
   the workflow is present on the default branch.
2. Choose **Run workflow** on the desired branch. Leave APK inputs empty for a
   bundle-only build. Pushes to `main` or `feature/native-apk-patches` and pull
   requests also build the bundle and run its checks; they do not fetch an Instagram APK.
3. Open a successful run and download `friends-only-native-patches-RUN_NUMBER`.
   Extract `friends-only.mpp`. Read `native-patch-reports-RUN_NUMBER` for the
   recorded evidence. A patch bundle cannot be installed on Android directly.

An optional manual run can apply the bundle to an APK or APKM when all three inputs are supplied:
`base_apk_url`, `base_sha256`, and `base_signer_sha256`. These are ordinary
workflow inputs, not secrets. In a public repository, do not put credentials,
private tokens, or credential-bearing/signed URLs into them. The runner needs
an authorized, accessible, credential-free HTTPS URL. The expected file digest
is for the whole downloaded file, including the outer container for an APKM.
The original input and its extracted splits are not uploaded as artifacts. A successful application run uploads
`friends-only-personal-test-apk-RUN_NUMBER` and an application report.
This is a statically verified test candidate. `run_install_smoke` defaults off;
when explicitly enabled, it additionally attempts a hosted emulator installation
without launching Instagram. Emulator setup or installation failures remain
visible, and do not turn into a successful installation claim.

The workflow generates a disposable personal signing key unless stable signing
secrets are configured: `FRIENDS_ONLY_KEYSTORE_B64`, `FRIENDS_ONLY_KEYSTORE_PASSWORD`,
and `FRIENDS_ONLY_KEY_ALIAS`. That is our signing identity, not Instagram’s.
Fresh generated keys do not provide a stable upgrade path between clean runs.
The delivered alpha.3 CI key was disposable and is not recoverable for future
updates. Configuring a new stable key still requires replacing that old build
once. Current GitHub integration permissions do not allow us to configure
repository secrets automatically. For future stable updates, a repository owner
can supply all three secrets above; APK compilation itself needs no secrets or
paid service. Never commit a keystore or its passwords.

## Original APK/APKM input and application

Supply an **unmodified standalone arm64-v8a APK or a supported APKM bundle** for
exactly `com.instagram.android` version `451.0.0.20.80` (version code `385709029`).
APKS and XAPK containers are not supported. Do not use a prepatched FeurStagram
release as the original. An arbitrary downloaded file is not trusted merely
because its filename looks correct. The input format is detected from the ZIP
structure, so a downloaded `.apkm` can retain its extension or use the workflow's
temporary `original.apk` filename.

Provide the file’s expected SHA-256 and the expected original signing
certificate’s SHA-256. Establish the signer expectation independently from a
trusted original distribution; copying the certificate digest from the same
untrusted APK is not independent provenance. The validator checks the supplied
expectations, package/version, signature, and APK form; it cannot establish
official provenance by itself. For APKM, the outer container signature and
`info.json` are not proof of Instagram authorship. Every inner APK must have the
expected Android package/version and a valid signature from the same expected
certificate. The base supplies the version name for configuration splits which
omit it. Split names, required dependencies, arm64 native libraries, ZIP paths
and archive limits are also checked before merging. The merged APK must retain
all original DEX payloads, native libraries and assets unchanged before any
Friends Only bytecode patch is applied. Resource merging and an unchanged code
inventory do not prove that the result will launch; that needs a phone test.

The supplied [Instagram input release](https://github.com/fluffydisk/friends-only-instagram/releases/tag/instagram-input)
contains a 224,236,889-byte APKM. Its recorded file SHA-256 is:

```text
7f1dc7549a3d970124983935e977447de6d3dee41e7334278d0821b5472089ad
```

Independent certificate evidence came from Instagram's own
[Digital Asset Links statement](https://www.instagram.com/.well-known/assetlinks.json),
which lists both a current and a legacy Android certificate. Use the matching
independently established digest as the expected signer; do not replace that
expectation with a digest copied only from an untrusted download. The source
Release was published publicly by its uploader. The build does not republish
the original input; source-code licensing does not grant redistribution rights
to Instagram's binaries.

After a bundle build, the workspace application command is:

```sh
python3 native-patches/scripts/apply.py \
  --apk /path/to/original-instagram.apkm \
  --sha256 ORIGINAL_FILE_SHA256 \
  --signer-sha256 TRUSTED_ORIGINAL_SIGNER_SHA256 \
  --patch-bundle native-patches/build/friends-only.mpp \
  --output native-patches/build/friends-only-personal.apk \
  --report native-patches/reports/apply-report.json
```

The same command accepts a standalone `.apk`. Use the actual digests in place
of the uppercase placeholders. A mismatch or
unsupported patch target must be investigated, not bypassed. No original APK
or generated patched APK belongs in this repository.

## Phone installation and feature checks

1. Download a verified, non-withdrawn APK artifact while signed into GitHub.
   Extract `friends-only-personal.apk` from its ZIP; do not install the `.mpp`
   bundle or original APKM.
2. Remove only the previous **Friends Only** native app when the CI signing
   key changes, clearing that app's local data. Official Instagram does not
   need removal. Open the APK in Files and confirm Android's normal installer.
3. Open **Friends Only** and sign in only through the genuine native account
   screen. A renamed package/personal signature does not establish that login,
   SSO or platform checks will succeed. No restriction is bypassed.
4. Check opening separately, then Home swipes, video taps/profile taps, photo
   carousels, Stories, Direct/groups, following-only filtering and 2× hold/release.
   Open Settings from both Home and the profile entry. Confirm only the one
   following filter is offered, and that changing it never restores discovery.
5. Include background/resume, rotation, video cancellation/scrolling and offline
   recovery. Account/logout settings formerly behind the replaced profile hub
   are no longer available through that entry; do not infer they were tested.

See [installation troubleshooting](../docs/INSTALLATION_TROUBLESHOOTING.md) for
historical errors and a privacy-conscious problem-report checklist. Optional
stable signing above supports later upgrades without repeat removal; the
current integration cannot configure GitHub secrets automatically.

## Evidence and next steps

| Item | Current evidence |
| --- | --- |
| Original input | 11 APKM splits cryptographically validated; supported package/version/arm64 |
| Merge | 23 DEX files, 18 native libraries and 3,166 assets preserved before patching |
| Alpha.3 | Normal phone installation/opening; screenshot shows Home/feed, Stories tray and three tabs |
| Alpha.4 | Local/GitHub APK build passed; phone untested |
| Alpha.5 | Local/GitHub static checks passed, but phone startup failed; withdrawn |
| Alpha.6 | Local/GitHub APK and mandatory 7/47/14 theme guard passed; user confirms opening, reports Home swipe reaches Reels |
| Alpha.7 | Local/GitHub signed APK and nine-patch checks passed: Python 163, Java 155, Kotlin 403; independent emitted DEX/resource audits and 149 static method analyses passed locally; phone untested |
| Stories playback, Direct sending/groups, login/challenges | Not separately verified |
| Hosted API 37 install checks | Emulator graphics/services failed; no PackageManager verdict |

The native work stays on `feature/native-apk-patches` in draft
[PR #3](https://github.com/fluffydisk/friends-only-instagram/pull/3); `main`
retains the earlier WebView app. Validate the new behavior on the phone before
promoting it. Further native redesign and additional layout/filter coverage
must follow observed behavior, not guesses about obfuscated code.

## Türkçe kısa rehber

- Alpha.6'nın telefonda açıldığı doğrulandı; Ana Sayfa'da kaydırınca Reels'e
  geçilebilmesi bilinen hatasıdır. Alpha.7 bu yolu gerçek sekme/sayfa listesi
  üzerinden kapatır; Reels görüntüleyicisine diğer geçişleri de engeller.
- Alpha.7'de **Arkadaşlar / Sohbetler / Ben** kalır. Reels, Keşfet ve Oluştur
  sayfaları veya bunları açan ayarlar bulunmaz. Tüm yatay hareketler
  engellenmez; gönderi galerileri kendi hareketlerini kullanır.
- Ana Sayfa ve kendi profilindeki native ayar yolları Friends Only ayarlarını açar:
  **Yalnızca takip ettiğim hesaplar**, 2× kullanım açıklaması ve Tamam.
  Instagram/Meta'nın genel ayar menüsü bu profil girişinden açılmaz. Başkalarının
  profilindeki takip/şikâyet/engelleme menüsü değiştirilmez.
- Akışta oynayan videoya basılı tutarken 2× hız hedeflenir; bırakma/iptal/satırın
  yeniden kullanılması mevcut native sıfırlama yolunu korur. Bu davranış
  alpha.7 için henüz telefonda doğrulanmış değildir. Yerel APK derlemesi,
  163 Python testi ve 558 Java/Kotlin kontrolü hem yerel hem GitHub derlemesinde
  geçti. Üstteki alpha.7 bağlantısından APK ZIP'ini indir.
- Akış boş veya eksikse takip filtresini kapatıp yenile. Eksik takip bilgisi
  nedeniyle gerçek arkadaş gönderileri de gizlenebilir; gömülü hikâye kartları
  da elenebilir. Üst hikâye sırasının oynatılması, mesaj gönderme ve grup
  sohbetleri ayrıca test edilmelidir.
- APK ZIP'ini çıkarıp `friends-only-personal.apk` dosyasını dosya yöneticisiyle
  kur. İmza değiştiğinde yalnızca eski Friends Only uygulamasını kaldırmak
  gerekir; yerel oturum/ayarlar silinir. Resmî Instagram'ı kaldırma, telefonu
  sıfırlama veya eski v2 teşhis aracını kullanma. Artifact 55 geri çekilmiştir.
- Gerçek hesap, giriş, zorunlu hukuki metinler ve hizmet Instagram altyapısında
  kalır. Bu çalışma Instagram'dan bağımsız bir sosyal ağ veya tamamen yeniden
  yazılmış bir native arayüz değildir; dar kapsam dışındaki bazı işaretler
  kalabilir. Yeni sunucu, hesap verisi toplama veya giriş kısıtı aşma eklenmez.

## License and attribution

The patch code adapts selected FeurStagram techniques at commit
[`3e02bcc99e874b5195b90e0a1748a4a218f822ba`](https://github.com/jean-voila/FeurStagram/tree/3e02bcc99e874b5195b90e0a1748a4a218f822ba).
Read [NOTICE.md](NOTICE.md) and the copied [GPLv3 license](LICENSE). That license
covers the patch work, not Instagram’s proprietary APK, trademarks, or services.
