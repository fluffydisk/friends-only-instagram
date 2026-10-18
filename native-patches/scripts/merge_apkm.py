#!/usr/bin/env python3
"""Merge already-verified split APKs and audit preserved code/media-library payloads."""
from collections import Counter
import hashlib
from pathlib import Path
import re
import zipfile

from common import TOOLS, VERSIONS, download, java_tool, run, sha256
from validate_apk import ValidationError, inspect_archive, inspect_manifest, run_command
from axml import normalize_apk_manifest


def payload_inventory(apks: list[Path]) -> tuple[Counter, dict[str, str]]:
    dex = Counter()
    payloads = {}
    for apk in apks:
        with zipfile.ZipFile(apk) as source:
            for entry in source.infolist():
                name = entry.filename
                is_dex = re.fullmatch(r"classes(?:[0-9]+)?\.dex", name)
                if entry.is_dir() or not (is_dex or name.startswith(("lib/", "assets/"))):
                    continue
                digest = hashlib.sha256()
                with source.open(entry) as content:
                    while chunk := content.read(1024 * 1024):
                        digest.update(chunk)
                value = digest.hexdigest()
                if is_dex:
                    dex[value] += 1
                else:
                    if name in payloads and payloads[name] != value:
                        raise ValidationError("Split APKs contain conflicting native-library or asset payloads.")
                    payloads[name] = value
    return dex, payloads


def audit_payloads(inputs: list[Path], merged: Path) -> dict:
    expected_dex, expected_payloads = payload_inventory(inputs)
    actual_dex, actual_payloads = payload_inventory([merged])
    if expected_dex != actual_dex:
        raise ValidationError("The split merger changed, dropped or duplicated executable DEX code.")
    if expected_payloads != actual_payloads:
        raise ValidationError("The split merger changed or dropped native libraries or assets.")
    return {"dex_files_preserved": sum(expected_dex.values()),
            "native_libraries_preserved": sum(name.startswith("lib/") for name in expected_payloads),
            "assets_preserved": sum(name.startswith("assets/") for name in expected_payloads)}


def merge_verified_splits(directory: Path, output: Path, android_tools: Path) -> dict:
    if output.exists():
        raise ValidationError("The merge output must not already exist.")
    inputs = sorted(path for path in directory.iterdir() if path.is_file() and path.suffix.lower() == ".apk")
    if not inputs:
        raise ValidationError("No validated split APKs were supplied.")
    payload_inventory(inputs)  # Detect conflicts before invoking the merger.
    tool = TOOLS / f"APKEditor-{VERSIONS['apkeditor']['version']}.jar"
    download(VERSIONS["apkeditor"]["url"], tool, VERSIONS["apkeditor"]["sha256"])
    run([java_tool("java"), "-Xmx3g", "-jar", tool, "m", "-validate-modules", "-clean-meta",
         "-i", directory, "-o", output])
    namespace_report = normalize_apk_manifest(output)
    archive = inspect_archive(output)
    metadata = inspect_manifest(
        run_command([str(android_tools / "aapt"), "dump", "badging", str(output)], "Merged manifest"),
        run_command([str(android_tools / "aapt"), "dump", "xmltree", str(output), "AndroidManifest.xml"], "Merged manifest"),
        archive["native_abis"],
    )
    return {"status": "merged_unsigned", "sha256": sha256(output), "manifest": metadata,
            "payload_audit": audit_payloads(inputs, output), "tool_version": VERSIONS["apkeditor"]["version"],
            "manifest_namespace_normalization": namespace_report}
