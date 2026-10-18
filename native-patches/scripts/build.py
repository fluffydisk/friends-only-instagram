#!/usr/bin/env python3
"""Compile the Friends Only native patch bundle; does not require an Instagram APK."""
from __future__ import annotations
import argparse
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import zipfile
from common import BUILD, REPORTS, ROOT, VERSIONS, android_paths, bootstrap, java_tool, run, sha256, write_report

PATCH_NAMES = ["Friends Only native controls", "Friends Only discovery blocking", "Friends Only app identity",
               "Friends Only feed video taps", "Friends Only following feed", "Friends Only quiet visual theme",
               "Friends Only Reels navigation", "Friends Only local settings", "Friends Only feed hold speed",
               "Friends Only feed video loop", "Friends Only native design", "Friends Only reference appearance"]


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true", help="Run Java/Kotlin policy, Python and patch-bundle loading checks")
    args = parser.parse_args()
    java, javac = java_tool("java"), java_tool("javac")
    version = run([javac, "-version"], capture=True).strip()
    if not re.match(r"javac 21(?:\.|$)", version):
        raise RuntimeError("This patch toolchain requires JDK 21. Set JAVA_HOME to JDK 21.")
    android, android_tools = android_paths()
    cli, compiler = bootstrap()
    if BUILD.exists():
        shutil.rmtree(BUILD)
    BUILD.mkdir(parents=True)
    REPORTS.mkdir(parents=True, exist_ok=True)
    classes, dex, patches = [BUILD / name for name in ("runtime-classes", "runtime-dex", "patch-classes")]
    for directory in (classes, dex, patches):
        directory.mkdir()
    sources = sorted((ROOT / "runtime/src/main/java").rglob("*.java"))
    if not sources:
        raise RuntimeError("Missing native runtime sources.")
    print("Compiling native runtime against Android SDK 35...", flush=True)
    run([javac, "--release", "8", "-Xlint:all", "-classpath", android, "-d", classes, *sources])
    runtime_jar = BUILD / "runtime.jar"
    run([java_tool("jar"), "--create", "--file", runtime_jar, "-C", classes, "."])
    run([java, "-cp", android_tools / "lib/d8.jar", "com.android.tools.r8.D8",
         "--release", "--min-api", str(VERSIONS["android"]["min_sdk"]),
         "--lib", android, "--output", dex, runtime_jar])
    print("Compiling version-pinned native patches...", flush=True)
    kotlin_sources = sorted((ROOT / "patches/src/main/kotlin").rglob("*.kt"))
    run([compiler, "-jvm-target", "21", "-classpath", cli, "-d", patches, *kotlin_sources])
    manifest = ("Manifest-Version: 1.0\nName: Friends Only native patches\n"
                "Description: Minimal native Instagram interface experiment\n"
                f"Version: {VERSIONS['friends_only']['version']}\n"
                f"Patcher-Version: {VERSIONS['morphe']['patcher_version']}\n"
                "Source: https://github.com/fluffydisk/friends-only-instagram\n"
                "Author: Friends Only contributors\nLicense: GPL-3.0-only\n\n")
    bundle = BUILD / ".candidate.mpp"
    # Fixed entry timestamps/order keep this patch archive deterministic.
    with zipfile.ZipFile(bundle, "w", compression=zipfile.ZIP_DEFLATED) as output:
        def add(name: str, content: bytes) -> None:
            info = zipfile.ZipInfo(name, (2026, 1, 1, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            output.writestr(info, content)
        add("META-INF/MANIFEST.MF", manifest.replace("\n", "\r\n").encode())
        for path in sorted(patches.rglob("*")):
            if path.is_file():
                add(path.relative_to(patches).as_posix(), path.read_bytes())
        add("extensions/extension.mpe", (dex / "classes.dex").read_bytes())
        for asset in ("Manrope.ttf", "OFL-Manrope.txt", "wordmark-xhdpi.webp", "wordmark-night-xhdpi.webp", "wordmark-xxhdpi.webp", "wordmark-night-xxhdpi.webp", "login-mark-xhdpi.png", "login-mark-xxhdpi.png"):
            add("design/" + asset, (ROOT / "design" / asset).read_bytes())
        add("LICENSE", (ROOT / "LICENSE").read_bytes())
        add("NOTICE.md", (ROOT / "NOTICE.md").read_bytes())
    print("Checking bundle discovery with the pinned patch engine...", flush=True)
    listed = run([java, "-jar", cli, "list-patches", "--patches", bundle, "-p", "-v"], capture=True)
    for name in PATCH_NAMES:
        if name not in listed:
            raise RuntimeError(f"Patch missing from compiled bundle: {name}")
    if VERSIONS["instagram"]["supported_version"] not in listed:
        raise RuntimeError("Bundle did not expose the required Instagram version pin.")
    (REPORTS / "patch-list.txt").write_text(listed, encoding="utf-8")
    checks = ["javac native runtime", "D8 native runtime", "Kotlin patches", "pinned engine loads all selected patches"]
    if args.check:
        test_classes = BUILD / "test-classes"
        test_classes.mkdir()
        tests = sorted((ROOT / "runtime/src/test/java").rglob("*.java"))
        run([javac, "--release", "8", "-classpath", classes, "-d", test_classes, *tests])
        run([java, "-cp", os.pathsep.join(map(str, (classes, test_classes))),
             "dev.friendsonly.patch.runtime.FocusPolicyTest"])
        run([java, "-cp", os.pathsep.join(map(str, (classes, test_classes))),
             "dev.friendsonly.patch.runtime.FeedPolicyTest"])
        run([java, "-cp", os.pathsep.join(map(str, (classes, test_classes))),
             "dev.friendsonly.patch.runtime.QuietChromePolicyTest"])
        run([java, "-cp", os.pathsep.join(map(str, (classes, test_classes))),
             "dev.friendsonly.patch.runtime.ScreenDesignPolicyTest"])
        run([java, "-cp", os.pathsep.join(map(str, (classes, test_classes))), "dev.friendsonly.patch.runtime.RuntimeSafetyPolicyTest"])
        patch_test_sources = sorted((ROOT / "patches/src/test/kotlin").rglob("*.kt"))
        if not patch_test_sources:
            raise RuntimeError("Missing app identity policy tests.")
        patch_test_classes = BUILD / "patch-test-classes"
        patch_test_classes.mkdir()
        # Compile tests with the main sources so Kotlin's internal manifest
        # helpers stay internal; these classes are never packaged in the bundle.
        run([compiler, "-jvm-target", "21", "-classpath", cli, "-d", patch_test_classes,
             *kotlin_sources, *patch_test_sources])
        run([java, "-cp", os.pathsep.join(map(str, (patch_test_classes, cli))),
             "dev.friendsonly.patches.AppIdentityPolicyTest"])
        run([java, "-cp", os.pathsep.join(map(str, (patch_test_classes, cli))),
             "dev.friendsonly.patches.FeedVideoTapPolicyTest"])
        run([java, "-cp", os.pathsep.join(map(str, (patch_test_classes, cli))),
             "dev.friendsonly.patches.FollowingFeedPolicyTest"])
        run([java, "-cp", os.pathsep.join(map(str, (patch_test_classes, cli))),
             "dev.friendsonly.patches.QuietThemePolicyTest"])
        for test in ("ReelsNavigationPolicyTest", "SettingsRoutesPolicyTest", "FeedSpeedPolicyTest", "FeedLoopPolicyTest", "FriendsDesignPolicyTest", "ReferenceVisualPolicyTest"):
            run([java, "-cp", os.pathsep.join(map(str, (patch_test_classes, cli))),
                 "dev.friendsonly.patches." + test])
        guard_tests = BUILD / "guard-test-classes"
        guard_tests.mkdir()
        run([javac, "-cp", cli, "-d", guard_tests, ROOT / "scripts/NativeVisualDexGuard.java", ROOT / "tests/NativeVisualDexGuardTest.java"])
        run([java, "-cp", os.pathsep.join(map(str, (guard_tests, cli))), "NativeVisualDexGuardTest"])
        run([sys.executable, "-m", "unittest", "discover", "-s", ROOT / "tests", "-v"])
        checks += ["pure Java policy assertions", "Kotlin application identity policy assertions",
                   "Kotlin feed viewer fingerprint and guard assertions", "Java strict Home feed and native chrome policy checks",
                   "Kotlin Home response filter and quiet theme checks",
                   "Kotlin permanent pager, local settings, feed hold speed and loop contracts", "Python tooling unit tests"]
    final_bundle = BUILD / "friends-only.mpp"
    bundle.replace(final_bundle)
    bundle = final_bundle
    report = {
        "status": "passed", "schema_version": 1, "kind": "patch_bundle_only",
        "version": VERSIONS['friends_only']['version'], "supported_instagram_version": VERSIONS["instagram"]["supported_version"],
        "source_package": VERSIONS["instagram"]["package_name"],
        "output_package": VERSIONS["friends_only"]["package_name"],
        "output_application_label": VERSIONS["friends_only"]["application_label"],
        "android_signature_and_version_checks": "unchanged",
        "bundle": {"file": bundle.name, "sha256": sha256(bundle), "size_bytes": bundle.stat().st_size},
        "checks": checks, "jdk": version,
        "instagram_apk_patched": False, "device_tests": "not_run", "authentication_test": "not_run",
    }
    write_report(REPORTS / "build-report.json", report)
    print(f"PASS: {bundle.relative_to(ROOT)} (patch bundle; no Instagram APK included)")


if __name__ == "__main__":
    try:
        main()
    except (RuntimeError, ValueError, OSError, subprocess.CalledProcessError) as error:
        print(f"Build failed: {error}", file=sys.stderr)
        sys.exit(1)
