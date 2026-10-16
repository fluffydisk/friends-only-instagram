#!/usr/bin/env python3
"""Validate and patch a compatible, independently trusted Instagram APK for personal use."""
from __future__ import annotations

import argparse
import json
import os
from pathlib import Path
import secrets
import shutil
import subprocess
import sys
import tempfile
import zipfile

from common import ROOT, VERSIONS, android_paths, bootstrap, java_tool, run, sha256, write_report
from build import PATCH_NAMES
from validate_apk import ValidationError, inspect_archive, inspect_manifest, run_command, validate_apk
from validate_apkm import validate_apkm
from merge_apkm import merge_verified_splits
from axml import normalize_namespace_order


def validate_patch_result(result: dict) -> None:
    """Never sign a partial or incompatible patch result, even after an exit-code zero."""
    # Morphe omits default-valued success=true; an explicit false still fails.
    if (result.get("success", True) is not True or result.get("failedPatches") or
            result.get("packageName") != VERSIONS["instagram"]["package_name"] or
            result.get("packageVersion") != VERSIONS["instagram"]["supported_version"]):
        raise ValidationError("The patch engine did not report a complete compatible result.")
    applied = result.get("appliedPatches", [])
    names = {entry.get("name") for entry in applied}
    if names != set(PATCH_NAMES) or len(applied) != len(PATCH_NAMES):
        raise ValidationError("The engine did not apply exactly the two Friends Only patches.")
    steps = result.get("patchingSteps", [])
    if (len(steps) != 2 or {step.get("step") for step in steps} != {"PATCHING", "REBUILDING"} or
            any(step.get("success") is not True for step in steps)):
        raise ValidationError("At least one required patching step did not succeed.")


def inspect_output(apk: Path, aapt: Path, apksigner: Path) -> dict:
    archive = inspect_archive(apk)
    manifest = inspect_manifest(
        run_command([str(aapt), "dump", "badging", str(apk)], "Output manifest check"),
        run_command([str(aapt), "dump", "xmltree", str(apk), "AndroidManifest.xml"], "Output manifest check"),
        archive["native_abis"],
    )
    signer_text = run_command([str(apksigner), "verify", "--verbose", "--print-certs", str(apk)], "Output signature check")
    import re
    digests = re.findall(r"^Signer #\d+ certificate SHA-256 digest:\s*([a-fA-F0-9]{64})\s*$", signer_text, re.M)
    if len(digests) != 1:
        raise ValidationError("Expected one verified personal APK signer.")
    # The merged extension descriptor must be present in some DEX. This does not
    # assert Android runtime behavior; device testing is a separate requirement.
    with zipfile.ZipFile(apk) as source:
        _, namespace_changes = normalize_namespace_order(source.read("AndroidManifest.xml"))
        if namespace_changes:
            raise ValidationError("The output manifest has non-nested namespace closing events.")
        extension_present = any(
            b"Ldev/friendsonly/patch/runtime/FocusHooks;" in source.read(entry)
            for entry in source.namelist() if re.fullmatch(r"classes(?:\d+)?\.dex", entry)
        )
    if not extension_present:
        raise ValidationError("The signed APK is missing the native extension.")
    return {"manifest": manifest, "sha256": sha256(apk), "size_bytes": apk.stat().st_size,
            "signer_sha256": digests[0].lower(), "extension_present": True,
            "manifest_namespace_order": "valid"}


def apply(args: argparse.Namespace) -> dict:
    apk, output, bundle = args.apk.resolve(), args.output.resolve(), args.patch_bundle.resolve()
    if not apk.is_file() or not bundle.is_file():
        raise ValidationError("Input APK or patch bundle is missing.")
    if output.exists() or output in (apk, bundle):
        raise ValidationError("Choose a new output path; inputs and existing files are never overwritten.")
    _, android_tools = android_paths()
    java = java_tool("java")
    cli, _ = bootstrap()
    # Only this repository's built artifact is accepted, not an upstream patched
    # APK or an arbitrary binary patch bundle with undisclosed behavior.
    report_path = ROOT / "reports/build-report.json"
    try:
        built = json.loads(report_path.read_text())
        if built["status"] != "passed" or built["bundle"]["sha256"] != sha256(bundle):
            raise ValidationError("Build this repository's patch bundle before applying it.")
    except (OSError, KeyError, json.JSONDecodeError) as error:
        raise ValidationError("A matching successful build report is required; run build.py --check.") from error
    output.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="friends-only-native-") as workspace:
        work = Path(workspace)
        # APKM can be downloaded to an arbitrary filename by CI. Detect its
        # root APK entry, then let the bounded bundle validator inspect it fully.
        with zipfile.ZipFile(apk) as uploaded:
            is_apkm = "base.apk" in uploaded.namelist() and "AndroidManifest.xml" not in uploaded.namelist()
        snapshot = work / ("original.apkm" if is_apkm else "original.apk")
        shutil.copyfile(apk, snapshot)
        if is_apkm:
            split_dir = work / "verified-splits"
            source = validate_apkm(snapshot, args.sha256, args.signer_sha256, split_dir,
                                   str(android_tools / "aapt"), str(android_tools / "apksigner"))
            merged = work / "merged-unsigned.apk"
            source["merge"] = merge_verified_splits(split_dir, merged, android_tools)
            patch_input = merged
        else:
            source = validate_apk(snapshot, args.sha256, args.signer_sha256,
                                  str(android_tools / "aapt"), str(android_tools / "apksigner"))
            patch_input = snapshot
        patch_report = work / "engine-result.json"
        unsigned, aligned, signed = [work / name for name in ("unsigned.apk", "aligned.apk", "signed.apk")]
        print("Input identity verified; applying only Friends Only UI/discovery patches...", flush=True)
        command = [java, "-Xmx4g", "-jar", cli, "patch", "--bytecode-mode=FULL", "--unsigned",
                   "--exclusive", "-p", bundle]
        for name in PATCH_NAMES:
            command.extend(["-e", name])
        command.extend(["-o", unsigned, "-r", patch_report, "-t", work / "patch-work", patch_input])
        run(command)
        try:
            engine_result = json.loads(patch_report.read_text())
            validate_patch_result(engine_result)
        except (OSError, json.JSONDecodeError) as error:
            raise ValidationError("Patch engine did not produce a readable result report.") from error
        if not unsigned.is_file():
            raise ValidationError("The engine did not produce an unsigned APK.")
        # Page alignment preserves modern Android native library loading.
        run([android_tools / "zipalign", "-f", "-P", "16", "4", unsigned, aligned])
        environment = os.environ.copy()
        key_path = environment.get("FRIENDS_ONLY_KEYSTORE_PATH")
        stable = bool(key_path)
        if key_path:
            if not Path(key_path).is_file() or not environment.get("FRIENDS_ONLY_KEYSTORE_PASSWORD"):
                raise ValidationError("The supplied personal signing key or password is missing.")
        else:
            key_path = str(work / "disposable-personal.p12")
            environment["FRIENDS_ONLY_KEYSTORE_PASSWORD"] = secrets.token_urlsafe(36)
            environment["FRIENDS_ONLY_KEY_ALIAS"] = "friends-only"
            run([java_tool("keytool"), "-genkeypair", "-noprompt", "-storetype", "PKCS12",
                 "-keystore", key_path, "-storepass:env", "FRIENDS_ONLY_KEYSTORE_PASSWORD",
                 "-alias", "friends-only", "-keyalg", "RSA", "-keysize", "3072",
                 "-validity", "3650", "-dname", "CN=Friends Only Personal Test"], env=environment)
        alias = environment.get("FRIENDS_ONLY_KEY_ALIAS", "friends-only")
        run([android_tools / "apksigner", "sign", "--ks", key_path, "--ks-key-alias", alias,
             "--ks-pass", "env:FRIENDS_ONLY_KEYSTORE_PASSWORD", "--key-pass", "env:FRIENDS_ONLY_KEYSTORE_PASSWORD",
             "--out", signed, aligned], env=environment)
        run([android_tools / "zipalign", "-c", "-P", "16", "4", signed])
        result = inspect_output(signed, android_tools / "aapt", android_tools / "apksigner")
        shutil.copyfile(signed, output)
        return {"schema_version": 1, "status": "passed", "kind": "personally_signed_instagram_apk",
                "input": source, "output": result, "patch_bundle_sha256": sha256(bundle),
                "patches": PATCH_NAMES, "signing": "user_provided_key" if stable else "disposable_key_not_preserved",
                "device_tests": "not_run", "authentication_test": "not_run",
                "official_instagram_update_compatible": False}


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apk", type=Path, required=True)
    parser.add_argument("--sha256", required=True)
    parser.add_argument("--signer-sha256", required=True)
    parser.add_argument("--patch-bundle", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--report", type=Path, required=True)
    args = parser.parse_args()
    # Guard before the failure-report path too: a typo must not destroy inputs.
    protected = {args.apk.resolve(), args.patch_bundle.resolve(), args.output.resolve()}
    key = os.environ.get("FRIENDS_ONLY_KEYSTORE_PATH")
    if key:
        protected.add(Path(key).resolve())
    if args.report.resolve() in protected:
        print("Choose a report path different from the inputs, output and signing key.", file=sys.stderr)
        return 1
    try:
        report = apply(args)
        write_report(args.report, report)
        print("PASS: personal patched APK signed and statically verified. Phone testing remains required.")
        return 0
    except (ValidationError, ValueError, RuntimeError, OSError, zipfile.BadZipFile, subprocess.CalledProcessError):
        # Never echo a failed signing invocation/environment, URL, or raw APK data.
        write_report(args.report, {"schema_version": 1, "status": "failed",
                                   "reason": "Input validation, patching, signing or output verification failed.",
                                   "device_tests": "not_run"})
        print("No APK delivered: validation, patching, signing or verification failed. See the preceding safe tool diagnostic.", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
