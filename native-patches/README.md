# Friends Only · native patch prototype

This is a separate, minimal patch bundle for **Instagram 451.0.0.20.80** on
Android. It changes selected controls in Instagram’s native interface; it does
not replace the existing Friends Only WebView application. That application and
its build workflow remain separate.

**The bundle is not an installable APK.** The supplied original APKM's 11
inner APKs passed signature and identity validation; merging preserved its
original code, native libraries and assets. The earlier signed alpha.1 and
alpha.2 outputs passed cloud static checks but failed installation on the user's
Galaxy S24+ running Android 17 / One UI 9. The latest phone result is
`INSTALL_FAILED_UPDATE_INCOMPATIBLE` (legacy `-7`): the personally signed APK
cannot update Android's existing `com.instagram.android` signing identity.
The location of the record is unknown; no other-profile installation is assumed.

Version **0.3.0-alpha.3** adds our own app identity:
`dev.friendsonly.instagram`, launcher name **Friends Only**, and an original
vector icon. It is intended to install alongside official Instagram, without
removing it or bypassing Android's signature checks. The bundle build and a
local signed APK's static checks passed, followed by its successful
[full GitHub APK workflow](https://github.com/fluffydisk/friends-only-instagram/actions/runs/39019337357).
**No alpha.3 APK installation, launch or account behavior has been verified yet.** See [VERIFICATION.md](VERIFICATION.md) and
[installation troubleshooting](../docs/INSTALLATION_TROUBLESHOOTING.md).

## Alpha.3 test APK

[Download `friends-only-personal-test-apk-35`](https://github.com/fluffydisk/friends-only-instagram/actions/runs/39019337357/artifacts/12095918541)
from the successful alpha.3 GitHub build. Sign into GitHub in a browser, extract
the ZIP, and open `friends-only-personal.apk` through Android's normal file
installation flow. The artifact is retained for 7 days. This native
patched app is separate from the earlier WebView app and its APK workflow.

The output version code is **385806737**. The older
`friends-only-personal-test-apk-17` is alpha.2, uses `com.instagram.android`, and
is known to fail on the user's phone; it is retained only as historical evidence.
Our existing v2 diagnostic helper targets that older package. Use normal
file-manager installation for alpha.3 rather than that helper.

## Initial scope

Three Friends Only patches are included:

1. **Native navigation:** hide the Reels, Explore/Search, and Create tabs by
   default using exact resource names inside the supported bottom tab bar.
   Home, Direct, and Profile stay available. Long-press Home opens a small
   Friends Only settings dialog, replacing Instagram's existing Home long-press
   gesture if present. Normal taps are preserved. Settings offer independent
   switches and **Show all** to restore the affected controls.
2. **Recommendation requests:** suppress only a small exact allowlist of Reels
   and Explore collection requests inside Instagram’s existing request path.
   Unknown requests pass through. The extension does not read response bodies,
   messages, feed items, credentials, cookies, or tokens; it creates no separate
   API client or backend. The intended preserved paths include authentication,
   Home feed, Stories, individual posts/videos, Direct, and group conversations.
   Their actual behavior still needs APK/device testing.
3. **App identity:** use `dev.friendsonly.instagram`, the Friends Only launcher
   label and our own vector icon. Re-scope app-defined permissions and declared
   provider authorities, including exact code references, to avoid collisions
   with official Instagram. External package queries and permissions, component
   class names, JNI paths and native libraries retain their original identities.
   Android and Instagram authentication/integrity checks remain in force.

Hiding Explore also hides the Search entry in that tab. This version does not
remove suggested posts from Home, determine who you follow, force chronological
Following, or guarantee a friends-only feed. Instagram's screens remain largely
unchanged: our launcher identity and local settings are not a full reskin.
There is no updater, added analytics, credential form, or
signature/authentication/integrity bypass.

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

The workflow generates a personal signing key unless stable signing secrets
are configured: `FRIENDS_ONLY_KEYSTORE_B64`, `FRIENDS_ONLY_KEYSTORE_PASSWORD`,
and `FRIENDS_ONLY_KEY_ALIAS`. That is our signing identity, not Instagram’s.
Fresh generated keys do not provide a stable upgrade path between clean runs.
Never commit a keystore or its passwords.

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

Fresh CI builds use disposable keys unless the signing secrets described above
are configured. Future builds of our own package may therefore need a stable
key for updates, or removal of the older Friends Only native build, which
clears that app's local data. This does not require removing official Instagram.

Record tests for login/challenges, Stories, ordinary photo/video posts, existing
Direct and group conversations, tab visibility, Home long-press, every toggle,
Show all, rotation/backgrounding, and offline recovery. Check that disabling a
filter restores its controls and that unfamiliar layouts remain usable.

## Evidence and next steps

| Item | Evidence for this prototype |
| --- | --- |
| Source/tool versions | Pinned in this module; upstream attribution below |
| Bundle build and policy checks | Passed locally in cloud on 2026-10-17: three patches loaded, 138 Python tests, 113 Java policy assertions, 26 Kotlin identity assertions |
| Original APKM validation | Supplied input: 11 APKs passed cryptographic signature/package/version checks |
| Split merge audit | Passed for 23 DEX files, 18 native libraries and 3,166 assets; resource/runtime compatibility still unverified |
| Applying fingerprints to Instagram | Passed: unique tab-bar binder and protected Tigon request entry point |
| Patched APK/signature | Local and GitHub alpha.3 signed APKs produced; all static output validators passed |
| Device installation | Alpha.1 downgrade (-25); alpha.2 first session error (-110), then confirmed signature conflict (-7) with v2 helper; alpha.3 not tested on the phone |
| Hosted API 37 alpha.3 install check | Failed in emulator graphics/services; ADB broken pipe, no PackageManager verdict |
| Login, feed, Stories, Direct, groups on a phone | Not tested for this prototype |

The exact alpha.3 CI APK reached the hosted API 37 installation step, but
emulator graphics crashes killed services and ADB returned a broken pipe.
[Run 39019676627](https://github.com/fluffydisk/friends-only-instagram/actions/runs/39019676627)
failed without a PackageManager verdict; no app launch/login was attempted.
The earlier WebView app’s phone report is not evidence about these native
patches. The next milestone is recording real installation/account tests of the
signed native output.
After that, refine the minimalist native appearance from observed layouts and
establish stable signing for upgrades. Broader feed filtering and a full native redesign remain future work.

See [VERIFICATION.md](VERIFICATION.md) for commands and the limits of these results.

## Türkçe kısa rehber

- **Native patches** iş akışını APK alanlarını boş bırakarak çalıştırmak yalnızca
  `friends-only.mpp` yama paketini üretir; bu dosya telefona kurulamaz.
- Desteklenen girdi Instagram `451.0.0.20.80` / `385709029` sürümünün
  değiştirilmemiş arm64 APK'sı veya APKM'sidir. Dosya ve bağımsız imzalayan
  sertifika özetleri doğrulanır. Yüklenen APKM'nin 11 parçası doğrulandı;
  birleştirmede 23 DEX, 18 native kütüphane ve 3.166 varlık korundu.
- Önceki APK'lar `com.instagram.android` paketini kullanıyordu. V2 teşhis aracı
  telefonda aynı dosya için gerçek **imza çakışmasını (-7)** gösterdi.
  Android'in kaydı hangi konumda tuttuğu bilinmiyor; başka profil kurulu olduğu
  varsayılmıyor. Instagram'ı tekrar kaldırmak veya telefonu sıfırlamak gerekmiyor.
- Alpha.3 kendi `dev.friendsonly.instagram` paketini, **Friends Only** adını ve
  simgesini kullanır. Yama derlemesi ve cloud ortamında üretilen imzalı APK'nin
  statik kontrolleri geçti. GitHub derlemesi de başarılı: yukarıdaki
  `friends-only-personal-test-apk-35` bağlantısından APK indirilebilir.
  Kurulum, açılış, giriş, hikâyeler ve mesajlar henüz doğrulanmadı.
  APK hazır olduğunda ZIP'i çıkartıp yeni dosyayı dosya yöneticisiyle kur.
  Mevcut v2 teşhis aracı eski paket için hazırlanmıştır.
- Bu çalışma tam bir arayüz tasarımı değildir. Instagram'ın mevcut native
  ekranları korunur; Ana Sayfa'ya uzun basmak Friends Only filtre ayarlarını
  açar. Normal video gönderileri ve hikâyeler özellikle kaldırılmaz.

## License and attribution

The patch code adapts selected FeurStagram techniques at commit
[`3e02bcc99e874b5195b90e0a1748a4a218f822ba`](https://github.com/jean-voila/FeurStagram/tree/3e02bcc99e874b5195b90e0a1748a4a218f822ba).
Read [NOTICE.md](NOTICE.md) and the copied [GPLv3 license](LICENSE). That license
covers the patch work, not Instagram’s proprietary APK, trademarks, or services.
