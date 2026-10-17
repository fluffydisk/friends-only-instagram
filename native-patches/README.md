# Friends Only · native patch prototype

This is a separate, minimal patch bundle for **Instagram 451.0.0.20.80** on
Android. It changes selected controls in Instagram’s native interface; it does
not replace the existing Friends Only WebView application. That application and
its build workflow remain separate.

**The bundle is not an installable APK.** The supplied original APKM's 11
inner APKs passed signature and identity validation; merging preserved its
original code, native libraries and assets. Alpha.3's signed APK passed
[GitHub build checks](https://github.com/fluffydisk/friends-only-instagram/actions/runs/39019337357)
and the user **installed and opened it through the phone's file manager** on a
Galaxy S24+ running Android 17 / One UI 9. Their screenshot shows a logged-in
Home feed, Stories tray and Home/Messages/Profile navigation. Story playback,
Direct sending, groups and the login/challenge flow have not been specifically
verified. The user reports the app's features look good overall.

Alpha.3 uses **`dev.friendsonly.instagram`**, launcher name **Friends Only** and
our own vector icon. This separate identity resolved the earlier same-package
installation obstacle without bypassing Android's signature checks. The older
v2 diagnostic helper hardcodes `com.instagram.android`; its package-hint error
when given alpha.3 is a helper limitation, not a rejection of the normal install.
See [VERIFICATION.md](VERIFICATION.md) and
[installation troubleshooting](../docs/INSTALLATION_TROUBLESHOOTING.md).

**Alpha.5 is ready for phone testing**, with six patches in total. It adds a
strict metadata-based Home filter and a quiet Friends Only shell/theme. Its
output version code is **385806739**. Local and
[GitHub APK builds](https://github.com/fluffydisk/friends-only-instagram/actions/runs/39059450948)
passed 140 Python tests, Java suites 180/32/26 and Kotlin suites 26/42/35/92.
Independent final local APK audits also passed; their scope is documented in
[verification](VERIFICATION.md). **Alpha.5 installation, appearance, strict
feed, Stories, Direct and authentication behavior on the phone are unverified.**
The user explicitly has not tested alpha.4; alpha.3 remains the only confirmed
native phone baseline.

## Download the alpha.5 test APK

[Download `friends-only-personal-test-apk-55`](https://github.com/fluffydisk/friends-only-instagram/actions/runs/39059450948/artifacts/12110373262)
from the successful six-patch build. Sign into GitHub in a browser, extract the
ZIP, and open `friends-only-personal.apk` through Android's normal file-manager
installation flow. The artifact is retained for 7 days. Its package is
**`dev.friendsonly.instagram`**, version code **385806739**.

The content filters and minimal header/navigation default on. Open Home
**Settings / Ayarlar**, or long-press Home, to change them. Read the strict-feed
and in-feed Story limitations below before testing.

Use the file manager; the old v2 diagnostic helper targets another package.
These CI builds use disposable signing keys. If Android rejects the update,
remove only the older **Friends Only** native app and install alpha.5; that
clears its local session/settings. Official Instagram does not need removal.

## Previous builds

The user confirmed alpha.3 installation/opening. Its
[`friends-only-personal-test-apk-35`](https://github.com/fluffydisk/friends-only-instagram/actions/runs/39019337357/artifacts/12095918541)
is a fallback baseline, version code **385806737**.
[Alpha.4 `friends-only-personal-test-apk-44`](https://github.com/fluffydisk/friends-only-instagram/actions/runs/39037320923/artifacts/12103030677)
adds the Home/profile tap guard but lacks the new strict Home filter and quiet
shell, and the user has not phone-tested it. Alpha.2's same-package artifact 17
failed on this phone and is historical evidence only.

## Initial scope

Alpha.3 included three patches, alpha.4 added the fourth, and alpha.5 adds the
following-feed and quiet-theme patches (six total):

1. **Native navigation:** hide the Reels, Explore/Search, and Create tabs by
   default using exact resource names inside the supported bottom tab bar.
   Home, Direct and Profile stay available. Long-press Home opens Friends Only
   settings, replacing Instagram's Home long-press gesture if present. Alpha.5
   also adds an explicit Home **Settings / Ayarlar** button and a scrollable
   native dialog. Normal tab click targets are retained.
2. **Recommendation requests:** suppress only a small exact allowlist of Reels
   and Explore collection requests inside Instagram’s existing request path.
   Unknown requests pass through. This request hook does not inspect response
   bodies, messages, credentials, cookies or tokens, and creates no separate
   API client or backend. Alpha.5's separate Home parser hook reads only the
   existing item/author relationship metadata described below. Intended
   preserved paths include authentication, Home feed, Stories, individual
   posts/videos, Direct and group conversations.
   Their actual behavior still needs APK/device testing.
3. **App identity:** use `dev.friendsonly.instagram`, the Friends Only launcher
   label and our own vector icon. Re-scope app-defined permissions and declared
   provider authorities, including exact code references, to avoid collisions
   with official Instagram. External package queries and permissions, component
   class names, JNI paths and native libraries retain their original identities.
   Android and Instagram authentication/integrity checks remain in force.
4. **Feed video taps (alpha.4, phone validation pending):** the
   **Friends Only feed video taps** patch intercepts the Reels/Clips launch path
   only for fourteen exact source values: six Home-feed sources and eight
   user-profile sources, including your own profile. With its independent
   setting enabled, those transitions do nothing; unknown sources and launches
   from Stories, Direct and group profiles pass through. It does not remove
   video posts or change inline video playback/network requests. Long-press Home
   and use **Akış ve profilden Reels açılmasını engelle** (block the viewer from
   feed and profiles) to change it. The setting defaults on; alpha.5's **Disable filters** also
   disables this guard.

5. **Following feed (alpha.5, phone validation pending):** the
   **Friends Only following feed** patch filters two ordinary-item array
   boundaries inside the supported Home response parser. A post is kept only
   when the primary author's existing `friendship_status.following` is exactly
   `Boolean.TRUE` and the item is not marked injected/sponsored. Non-post,
   recommendation and unknown units are dropped; absent or changed metadata is
   rejected. It starts strict even before preferences load. No private API
   client, new network request, request-header rewrite or following-list scrape
   is added. Shared media, standalone Stories, Direct and profile parsers are
   not patched. Story units embedded in the Home feed are dropped too when
   they have no primary media post; they are not proven recommendation-only.
6. **Quiet visual theme (alpha.5, phone validation pending):**
   **Friends Only quiet visual theme** supplies warm neutral light/dark
   surfaces, restrained Story rings, independent tab artwork and own OS splash
   branding. Runtime decoration gives the supported Home toolbar a Friends Only
   title and Settings button, replaces the exact supported internal startup
   logo with our icon, and covers the original bottom icons/badge counts
   with **Friends / Chats / Me** (Turkish: **Arkadaşlar / Sohbetler / Ben**).
   Existing navigation click targets and native content views remain in place.
   It uses the existing throttled bounded view updates, with no additional
   polling timer or global observer. This is not a complete new post/chat
   renderer; other Instagram chrome can remain outside these verified scopes.

## Alpha.5 settings and limits

**Only accounts I follow / Yalnızca takip ettiğim hesaplar** (`following_only`)
defaults on. Missing relationship metadata is not treated as proof of following:
Home can become partial or empty, even for accounts you follow. Disable the
setting and refresh Home to recover the original feed. Enabling it does not
purge already rendered/cached content; cache and alternate renderer behavior
still require phone tests. The patch does not change server ordering or request
a chronological feed, and cannot yet establish a fully friends-only experience.
Ordinary photo/video posts with confirmed followed authors remain eligible.
Home also supports in-feed Story units (`stories_netego`, including
`XDTStoriesInFeedItem`/`reels`/`portable_tray`, plus `tagged_edge_story` and
`explore_story`). They lack the required primary media and are dropped by strict
mode; they cannot all be classified as recommendations. Standalone Story
parsers/requests are unchanged, but top-tray playback still needs a phone test.
This filter therefore does not establish that every Story surface is preserved.

**Minimal header and navigation / Sade başlık ve gezinme** (`quiet_ui`) defaults
on. Turning it off restores our runtime header/tab decorations; the compiled
neutral palette, Story-ring styles and launch assets remain part of the APK.
Runtime colors read the native app's selected theme/background, including its
explicit light/dark selection; actual rendered appearance still needs phone tests.
**Disable filters / Filtreleri kapat** turns off content/navigation filters and
the feed guard, while preserving this separate appearance preference. Refresh
Home after changing the following filter. Settings are local; Instagram account
data is not persisted by our extension.

Hiding Explore also hides Search in that tab. Alpha.4 did not remove Home
recommendations; alpha.5 attempts that only at its verified Home parser
boundaries. Other Instagram features, controls or branding can remain. There is
no updater, added analytics, credential form, or signature/authentication/integrity
bypass.

Instagram's own networking, permissions, tracking and privacy terms remain in
effect. Our small extension adds no permissions or third-party collection;
patching the navigation does not remove Instagram's existing data collection.

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
   bundle-only build. Pushes to `main` and pull requests also build the bundle
   and run its checks; they do not fetch an Instagram APK.
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

## Phone installation, after output verification

First require a successful application report and output signature/package
verification. Alpha.3's output package is **`dev.friendsonly.instagram`**, separate
from official Instagram's **`com.instagram.android`**. You do not need to remove
the official app or delete another profile. Install the verified APK using
Android's normal file-manager flow; allow that source to request installation
when Android asks. Existing device policies still apply.

The launcher says **Friends Only** and uses our own icon. Sign in only through
Instagram's native screens. Package renaming and a valid personal signature do
not prove Instagram accepts the client: login, SSO, deep links and other
features may fail. Stop on platform rejection; no bypass is included. Official
Instagram can remain installed or be restored through Play Store.

The delivered alpha.3 key is disposable. A new key cannot update that installed
build. Before installing the new build, if Android reports an update/signature conflict,
remove only the older **Friends Only** native app, then install the new APK.
That clears Friends Only's local session/settings; official Instagram does not
need to be removed. The optional stable-signing setup above prevents repeated
key changes for subsequent builds, once the new key is in use.

Record tests for login/challenges, Stories, ordinary photo/video posts, existing
Direct and group conversations, tab visibility, Home long-press, every toggle,
Show all, rotation/backgrounding, and offline recovery. Check that disabling a
filter restores its controls and that unfamiliar layouts remain usable.

## Evidence and next steps

| Item | Evidence for this prototype |
| --- | --- |
| Source/tool versions | Pinned in this module; upstream attribution below |
| Alpha.3 bundle build and policy checks | Passed locally in cloud on 2026-10-17: three patches loaded, 138 Python tests, 113 Java policy assertions, 26 Kotlin identity assertions |
| Original APKM validation | Supplied input: 11 APKs passed cryptographic signature/package/version checks |
| Split merge audit | Passed for 23 DEX files, 18 native libraries and 3,166 assets; phone opening subsequently confirmed for alpha.3 |
| Applying fingerprints to Instagram | Passed: unique tab-bar binder and protected Tigon request entry point |
| Patched APK/signature | Local and GitHub alpha.3 signed APKs produced; all static output validators passed |
| Device installation | User confirmed alpha.3 normal file-manager installation and opening on S24+ Android 17 / One UI 9; older failures are historical |
| Hosted API 37 alpha.3 install check | Failed in emulator graphics/services; ADB broken pipe, no PackageManager verdict |
| Visible phone UI | User screenshot: logged-in Home feed, Stories tray, Home/Messages/Profile navigation |
| Story playback, Direct sending, groups, login/challenge flow | Not specifically tested/reported |
| Alpha.4 combined bundle/guard | Local and GitHub APK builds passed: 139 Python tests, 180 Java policy assertions, 26 identity + 42 guard Kotlin assertions; user explicitly has not phone-tested it |
| Alpha.5 strict feed/quiet shell | Local and GitHub signed APK builds passed all six patches; Python 140, Java 180/32/26, Kotlin 26/42/35/92; independent emitted DEX/resource audits passed on final local APK; phone verification pending |

The exact alpha.3 CI APK reached the hosted API 37 installation step, but
emulator graphics crashes killed services and ADB returned a broken pipe.
[Run 39019676627](https://github.com/fluffydisk/friends-only-instagram/actions/runs/39019676627)
failed without a PackageManager verdict; no app launch/login was attempted.
The earlier WebView app’s phone report is not evidence about these native
patches. The next milestone is validating alpha.5's strict Home filter and
native shell, then recording feature-specific phone tests. Alpha.4 remains untested on the
user's phone.
After that, refine the minimalist native appearance from observed layouts and
establish stable signing for upgrades. Coverage of additional feed/render paths
and a full native redesign remain future work.

See [VERIFICATION.md](VERIFICATION.md) for commands and the limits of these results.

## Türkçe kısa rehber

- **Native patches** iş akışını APK alanlarını boş bırakarak çalıştırmak yalnızca
  `friends-only.mpp` yama paketini üretir; bu dosya telefona kurulamaz.
- Desteklenen girdi Instagram `451.0.0.20.80` / `385709029` sürümünün
  değiştirilmemiş arm64 APK'sı veya APKM'sidir. Dosya ve bağımsız imzalayan
  sertifika özetleri doğrulanır. Yüklenen APKM'nin 11 parçası doğrulandı;
  birleştirmede 23 DEX, 18 native kütüphane ve 3.166 varlık korundu.
- Alpha.3 kendi `dev.friendsonly.instagram` paketini, **Friends Only** adını ve
  simgesini kullanır. Kullanıcı dosya yöneticisiyle kurulumun ve açılışın
  başarılı olduğunu doğruladı. Ekran görüntüsünde giriş yapılmış Ana Sayfa,
  hikâye sırası ve Ana Sayfa/Mesajlar/Profil menüsü görünüyor. Hikâye oynatma,
  mesaj gönderme, gruplar ve giriş/doğrulama akışı ayrıca test edilmiş sayılmaz.
- V2 teşhis aracı eski `com.instagram.android` paketi için hazırlanmıştır.
  Alpha.3'te verdiği paket adı uyuşmazlığı (-2), APK'nin normal kurulumunun
  başarısız olduğu anlamına gelmez. Yeni APK'ları dosya yöneticisiyle kur.
- Alpha.4, akışta ve kullanıcı profillerinde videoya dokununca Reels ekranına
  geçişi durdurmak için hazırlandı. **Akış ve profilden Reels açılmasını
  engelle** ayarı varsayılan olarak açık; akış içi videolar korunacak. Mesajlar,
  hikâyeler, grup profilleri ve tanınmayan kaynaklar bu kuralın dışında.
  Akış ve profil kapsamındaki dört yama derlendi; 139 Python testi, 180 Java
  kontrolü, 26 kimlik ve 42 guard Kotlin kontrolü geçti. Cloud ortamındaki imzalı
  APK'nin statik kontrolleri ve bağımsız guard kontrolü de geçti. GitHub APK
  derlemesi başarılıydı; kullanıcı alpha.4'ü telefonda henüz denemedi. Güncel
  alpha.5 bu engeli de içerir; yeni indirme bağlantısı üstteki artifact 55'tir.
- Alpha.5 telefon testi için hazır: Ana Sayfa'da yalnızca gönderinin asıl yazarını takip
  ettiğine dair açık bilgi varsa gönderiyi tutan katı filtre ve sade açık/koyu
  tema ekleniyor. Friends Only başlığı, **Ayarlar** düğmesi ve
  **Arkadaşlar / Sohbetler / Ben** gezinmesi var; tüm gönderi/sohbet bileşenleri
  baştan yazılmış değildir. Bilgi eksikse akış kısalabilir veya boş olabilir:
  **Yalnızca takip ettiğim hesaplar** ayarını kapatıp akışı yenile. Bağımsız
  hikâye, mesaj ve profil ayrıştırıcıları değişmez; fakat akış içine gömülü
  hikâye kartları da atılır ve hepsinin öneri olduğu doğrulanmış değildir.
  Üst hikâye sırasının oynatılması ayrıca telefonda test edilmelidir. Altı yama
  son derlemede geçti: 140 Python testi, tüm Java/Kotlin kontrolleri (92 tema
  kontrolü dahil), ayrıca 97 kaynak kontrolü ve ayrı drawable derleme kontrolü
  başarılı. Yerel ve GitHub imzalı APK derlemeleri geçti; yukarıdaki
  `friends-only-personal-test-apk-55` bağlantısından indir. Alpha.5'in kurulumu,
  görünümü, akışı ve hesap özellikleri telefonda henüz doğrulanmadı. Alpha.4 de
  henüz telefonda denenmedi.
- Alpha.3'ün imzası geçiciydi. Yeni sürüm imza nedeniyle güncellenemezse yalnızca
  eski **Friends Only** uygulamasını kaldırıp yenisini kur; yerel oturum ve
  ayarlar silinir. Resmî Instagram'ı kaldırmak veya telefonu sıfırlamak gerekmez.
- Bu çalışma tam bir arayüz tasarımı değildir. Instagram'ın mevcut native
  ekranları korunur; Ana Sayfa'ya uzun basmak Friends Only filtre ayarlarını
  açar. Video-dokunma engeli gönderileri silmez; katı Home filtresi ise akış
  içindeki hikâye birimlerini de atabilir. Tüm hikâye yüzeylerinin korunduğu
  henüz doğrulanmadı.

## License and attribution

The patch code adapts selected FeurStagram techniques at commit
[`3e02bcc99e874b5195b90e0a1748a4a218f822ba`](https://github.com/jean-voila/FeurStagram/tree/3e02bcc99e874b5195b90e0a1748a4a218f822ba).
Read [NOTICE.md](NOTICE.md) and the copied [GPLv3 license](LICENSE). That license
covers the patch work, not Instagram’s proprietary APK, trademarks, or services.
