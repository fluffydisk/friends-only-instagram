# Friends Only · native patch prototype

This is a separate, minimal patch bundle for **Instagram 451.0.0.20.80** on
Android. It changes selected controls in Instagram’s native interface; it does
not replace the existing Friends Only WebView application. That application and
its build workflow remain separate.

**The bundle is not an installable APK.** An original APKM has now been supplied;
its 11 inner APKs passed signature and identity validation, and the split merge
passed a code/library/asset preservation audit. Both native patches were applied
successfully and a personally signed APK was produced and statically verified.
Running it on a phone remains unverified. See [VERIFICATION.md](VERIFICATION.md) for the latest recorded result;
a successful bundle build alone does not establish Instagram compatibility.

## Download the first native test APK

The [successful APK build](https://github.com/fluffydisk/friends-only-instagram/actions/runs/38962328897)
produced [friends-only-personal-test-apk-9](https://github.com/fluffydisk/friends-only-instagram/actions/runs/38962328897/artifacts/12073995063).
Open that artifact link in a browser while signed into GitHub, download the ZIP,
and extract `friends-only-personal.apk`. The artifact is retained for 7 days.
This is the native patched app, separate from the WebView workflow's APK.

Read the installation/signature notes below before replacing an installed app.
Installation, launch, login and account features have not been tested on a device.
Its launcher still uses Instagram's name/icon; Home long-press opens our settings.

## Initial scope

Two Friends Only patches are included:

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

Hiding Explore also hides the Search entry in that tab. This version does not
remove suggested posts from Home, determine who you follow, force chronological
Following, or guarantee a friends-only feed. It does not implement a full reskin:
the initial proof keeps Instagram’s launcher name/icon and identifies our local
settings as **Friends Only**. There is no clone package, updater, analytics,
credential form, or authentication/integrity bypass.

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
verification. **Do not remove a working Instagram installation while a verified
patched APK is still unavailable.** The original package name is retained:
`com.instagram.android`. Our signing key differs from Instagram’s, so Android
cannot install this over the official app or alongside it as a clone.

Once a verified build exists, choosing to replace the official installation
requires uninstalling that installation and loses its local session/settings
and other local app data. Make that choice only after reviewing the output and
the limitations. Installation then uses Android’s normal APK sideloading flow;
Samsung Auto Blocker or device policy may affect it. The launcher still says
Instagram in this initial proof. Sign in through Instagram’s own native screens;
no login-success claim is made for a re-signed build and no restriction bypass
is included. Keep the official store installation route available for rollback.

Record tests for login/challenges, Stories, ordinary photo/video posts, existing
Direct and group conversations, tab visibility, Home long-press, every toggle,
Show all, rotation/backgrounding, and offline recovery. Check that disabling a
filter restores its controls and that unfamiliar layouts remain usable.

## Evidence and next steps

| Item | Evidence for this prototype |
| --- | --- |
| Source/tool versions | Pinned in this module; upstream attribution below |
| Bundle build and policy checks | Passed in cloud on 2026-10-16: Java, D8, Kotlin, both patches loaded; 113 Java assertions and 81 Python tests |
| Original APKM validation | Supplied input: 11 APKs passed cryptographic signature/package/version checks |
| Split merge audit | Passed for 23 DEX files, 18 native libraries and 3,166 assets; resource/runtime compatibility still unverified |
| Applying fingerprints to Instagram | Passed: unique tab-bar binder and protected Tigon request entry point |
| Patched Instagram APK/signature | Produced in cloud; package/signature/extension/16 KiB alignment checks passed |
| Login, feed, Stories, Direct, groups on a phone | Not tested for this prototype |

The earlier WebView app’s phone report is not evidence about these native
patches. The next milestone is recording real installation/account tests of the
signed native output.
After that, refine the minimalist native appearance from observed layouts and
establish stable signing for upgrades. Broader feed filtering and branding
changes are outside this first proof.

See [VERIFICATION.md](VERIFICATION.md) for commands and the limits of these results.

## Türkçe kısa rehber

- **Native patches** iş akışını APK alanlarını boş bırakarak çalıştır: sonuç
  `friends-only.mpp` yama paketidir; telefona kurulacak APK değildir.
- Girdi olarak belirtilen sürümün değiştirilmemiş tek parça arm64 APK’sı veya
  desteklenen APKM paketi kullanılabilir. Dosya özeti ve bağımsız olarak
  doğrulanmış imzalayan sertifika özeti gerekir. Yüklenen APKM’nin 11 APK parçası
  doğrulandı; birleştirmede 23 DEX, 18 native kütüphane ve 3.166 varlık korundu.
  İki yama da uygulandı ve imzalanmış APK üretildi; statik çıktı kontrolleri geçti.
  Güncel sonuçlar VERIFICATION.md dosyasındadır. Telefon testleri henüz yapılmadı.
- Doğrulanmış çıktı hazır olmadan mevcut Instagram’ı kaldırma. Aynı paket adı
  ve farklı imza nedeniyle resmi uygulamanın üstüne güncelleme kurulamaz;
  kaldırma yerel verileri siler. İlk denemede başlatıcı adı/simgesi Instagram
  olarak kalır; uygun olduğunda Ana Sayfa’ya uzun basmak Friends Only ayarlarını
  açar. Bu çalışma mevcut WebView uygulamasını değiştirmez.

## License and attribution

The patch code adapts selected FeurStagram techniques at commit
[`3e02bcc99e874b5195b90e0a1748a4a218f822ba`](https://github.com/jean-voila/FeurStagram/tree/3e02bcc99e874b5195b90e0a1748a4a218f822ba).
Read [NOTICE.md](NOTICE.md) and the copied [GPLv3 license](LICENSE). That license
covers the patch work, not Instagram’s proprietary APK, trademarks, or services.
