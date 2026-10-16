#!/usr/bin/env python3
"""Validate a supplied APKM and extract only its independently verified APKs.

APKM info.json, filenames, and the outer signature are advisory. Android
manifests and verified APK signing certificates determine bundle identity.
The caller must supply the expected certificate from a separate trusted source.
"""

from __future__ import annotations

import argparse
import hmac
import json
from pathlib import Path
import re
import shutil
import stat
import sys
import zipfile
import zlib

from validate_apk import (
    ValidationError, _safe_entry_name, file_sha256, inspect_signer,
    normalize_digest, run_command, sdk_tool,
)

PACKAGE_NAME = "com.instagram.android"
SUPPORTED_VERSION = "451.0.0.20.80"
SUPPORTED_VERSION_CODE = "385709029"
MAX_BUNDLE_BYTES = 1024 * 1024 * 1024
MAX_SPLIT_BYTES = 512 * 1024 * 1024
MAX_OUTER_EXPANDED_BYTES = 2 * 1024 * 1024 * 1024
MAX_INNER_EXPANDED_BYTES = 2 * 1024 * 1024 * 1024
MAX_OUTER_ENTRIES = 128
MAX_APKS = 32
MAX_INNER_ENTRIES = 50_000
MAX_MANIFEST_BYTES = 8 * 1024 * 1024
MAX_COMPRESSION_RATIO = 200
CHUNK_BYTES = 1024 * 1024
SPLIT_NAME = re.compile(r"[A-Za-z0-9_][A-Za-z0-9_.-]{0,199}\Z")
ZIP_ERRORS = (OSError, ValueError, RuntimeError, zipfile.BadZipFile,
              zipfile.LargeZipFile, NotImplementedError, EOFError, zlib.error)


def _entries(archive: zipfile.ZipFile, *, outer: bool) -> list[zipfile.ZipInfo]:
    entries = archive.infolist()
    if not entries or len(entries) > (MAX_OUTER_ENTRIES if outer else MAX_INNER_ENTRIES):
        raise ValidationError("The bundle or split has an unsupported ZIP entry count.")
    names: set[str] = set()
    expanded = 0
    for entry in entries:
        name = _safe_entry_name(entry.orig_filename)
        if name in names:
            raise ValidationError("The bundle or split contains duplicate ZIP entry paths.")
        names.add(name)
        kind = stat.S_IFMT(entry.external_attr >> 16)
        if kind not in (0, stat.S_IFREG, stat.S_IFDIR):
            raise ValidationError("The bundle or split contains a non-regular ZIP entry.")
        if entry.flag_bits & 1:
            raise ValidationError("Encrypted ZIP entries are not supported.")
        if entry.compress_type not in (zipfile.ZIP_STORED, zipfile.ZIP_DEFLATED):
            raise ValidationError("The bundle or split uses unsupported ZIP compression.")
        if entry.file_size > MAX_SPLIT_BYTES:
            raise ValidationError("A bundle or split entry exceeds the uncompressed size limit.")
        expanded += entry.file_size
        if expanded > (MAX_OUTER_EXPANDED_BYTES if outer else MAX_INNER_EXPANDED_BYTES):
            raise ValidationError("The bundle or split exceeds the total uncompressed size limit.")
        if entry.file_size and (not entry.compress_size or
                                entry.file_size > entry.compress_size * MAX_COMPRESSION_RATIO):
            raise ValidationError("The bundle or split exceeds the ZIP compression ratio limit.")
        if entry.is_dir():
            if entry.file_size:
                raise ValidationError("ZIP directory entries must not contain file data.")
            continue
        suffix = Path(name).suffix.lower()
        if outer and suffix == ".apk":
            if "/" in name:
                raise ValidationError("APK entries must be at the bundle root.")
        elif suffix in (".apk", ".apkm", ".apks", ".xapk"):
            raise ValidationError("Nested APK or bundle archives are not supported.")
        if not outer and name == "AndroidManifest.xml" and entry.file_size > MAX_MANIFEST_BYTES:
            raise ValidationError("A split Android manifest exceeds the size limit.")
    if not outer and "AndroidManifest.xml" not in names:
        raise ValidationError("A split APK has no Android manifest.")
    return entries


def _stream(archive: zipfile.ZipFile, entry: zipfile.ZipInfo, destination=None) -> None:
    count = 0
    with archive.open(entry) as source:
        while chunk := source.read(CHUNK_BYTES):
            count += len(chunk)
            if count > entry.file_size or count > MAX_SPLIT_BYTES:
                raise ValidationError("A ZIP entry exceeds its declared size.")
            if destination is not None:
                destination.write(chunk)
    if count != entry.file_size:
        raise ValidationError("A ZIP entry has an inconsistent uncompressed size.")


def _inspect_split(path: Path) -> dict:
    with zipfile.ZipFile(path) as archive:
        entries = _entries(archive, outer=False)
        abis: set[str] = set()
        has_code = False
        for entry in entries:
            if not entry.is_dir():
                _stream(archive, entry)
                if entry.filename == "classes.dex":
                    has_code = True
                match = re.fullmatch(r"lib/([A-Za-z0-9_-]{1,32})/[^/]+\.so", entry.filename)
                if match:
                    abis.add(match[1])
        return {"entry_count": len(entries), "native_abis": sorted(abis),
                "has_classes_dex": has_code,
                "uncompressed_bytes": sum(entry.file_size for entry in entries)}


def _attributes(text: str) -> dict[str, str]:
    return dict(re.findall(r"([A-Za-z][A-Za-z0-9_:-]*)='([^']*)'", text))


def _xml_manifest(xmltree: str) -> tuple[dict[str, str], set[str]]:
    """Read root attributes and direct uses-split children from aapt's tree."""
    attributes: dict[str, str] = {}
    dependencies: set[str] = set()
    root_indent = None
    current_element = None
    current_indent = None
    for line in xmltree.splitlines():
        element = re.match(r"^(\s*)E:\s+(\S+)", line)
        if element:
            indent = len(element[1])
            name = element[2]
            if name == "manifest":
                if root_indent is not None:
                    raise ValidationError("The split manifest root is ambiguous.")
                root_indent = indent
            current_element, current_indent = name, indent
            continue
        attribute = re.match(r"^\s*A:\s+([A-Za-z0-9_:]+)(?:\([^)]*\))?=(.*)$", line)
        if not attribute or root_indent is None:
            continue
        name = attribute[1].removeprefix("android:")
        raw = attribute[2].strip()
        quoted = re.match(r'"([^"\r\n]*)"', raw)
        value = quoted[1] if quoted else raw
        if current_element == "manifest":
            if name in attributes:
                raise ValidationError("A split manifest has duplicate root attributes.")
            attributes[name] = value
        elif current_element == "uses-split" and current_indent == root_indent + 2 and name == "name":
            if not quoted or not SPLIT_NAME.fullmatch(value):
                raise ValidationError("A split dependency has an invalid name.")
            dependencies.add(value)
    if root_indent is None:
        raise ValidationError("aapt did not return a readable split Android manifest.")
    return attributes, dependencies


def _types(value: str) -> set[str]:
    values = set(filter(None, re.split(r"[,\s]+", value)))
    if any(not SPLIT_NAME.fullmatch(part) for part in values):
        raise ValidationError("A split manifest has invalid split type metadata.")
    return values


def _manifest(badging: str, xmltree: str, archive: dict) -> dict:
    packages = re.findall(r"^package:\s*(.+)$", badging, re.MULTILINE)
    if len(packages) != 1:
        raise ValidationError("aapt did not return a unique split APK package.")
    attrs = _attributes(packages[0])
    root, dependencies = _xml_manifest(xmltree)
    if attrs.get("name") != PACKAGE_NAME or root.get("package") != PACKAGE_NAME:
        raise ValidationError("The split APK package is not com.instagram.android.")
    if attrs.get("versionCode") != SUPPORTED_VERSION_CODE:
        raise ValidationError("The split APK version code is not supported.")
    version_name = attrs.get("versionName", "")
    if version_name and version_name != SUPPORTED_VERSION:
        raise ValidationError("The split APK version name is not supported.")
    split = attrs.get("split", "")
    if split != root.get("split", ""):
        raise ValidationError("The split identity differs between Android metadata views.")
    if split and not SPLIT_NAME.fullmatch(split):
        raise ValidationError("The APK has an invalid split identity.")
    if not split and version_name != SUPPORTED_VERSION:
        raise ValidationError("The base APK must declare the supported version name.")
    feature = root.get("isFeatureSplit", "") in ("(type 0x12)0xffffffff", "true")
    config_for = root.get("configForSplit", "")
    if config_for and not SPLIT_NAME.fullmatch(config_for):
        raise ValidationError("A split configuration target has an invalid name.")
    for dep_line in re.findall(r"^\s*uses-split:\s*(.*)$", badging, re.MULTILINE):
        dependency = _attributes(dep_line).get("name", "")
        if not SPLIT_NAME.fullmatch(dependency):
            raise ValidationError("A split dependency has an invalid name.")
        dependencies.add(dependency)
    abi_lines = re.findall(r"^native-code:\s*(.*)$", badging, re.MULTILINE)
    if len(abi_lines) > 1:
        raise ValidationError("aapt returned ambiguous split ABI metadata.")
    abis = sorted(set(re.findall(r"'([A-Za-z0-9_-]{1,32})'", abi_lines[0]))) if abi_lines else []
    if abis != archive["native_abis"]:
        raise ValidationError("The split ABI metadata does not match its native libraries.")
    return {
        "package": PACKAGE_NAME, "version_name": version_name or SUPPORTED_VERSION,
        "version_name_inherited": not bool(version_name),
        "version_code": SUPPORTED_VERSION_CODE, "split_name": split or None,
        "is_feature_split": feature, "config_for_split": config_for or None,
        "dependencies": sorted(dependencies), "native_abis": abis,
        "split_types": sorted(_types(root.get("splitTypes", ""))),
        "required_split_types": sorted(_types(root.get("requiredSplitTypes", ""))),
    }


def validate_apkm(apkm: Path, sha256: str, signer_sha256: str, extract_dir: Path,
                  aapt: str | None = None, apksigner: str | None = None) -> dict:
    expected_hash = normalize_digest(sha256, "Expected APKM SHA-256")
    expected_signer = normalize_digest(signer_sha256, "Expected signer SHA-256", allow_colons=True)
    path = Path(apkm).expanduser()
    destination = Path(extract_dir).expanduser()
    try:
        info = path.stat()
    except OSError as exc:
        raise ValidationError("The input APKM could not be inspected.") from exc
    if path.suffix.lower() != ".apkm" or not stat.S_ISREG(info.st_mode):
        raise ValidationError("Supply one regular .apkm file.")
    if info.st_size <= 0 or info.st_size > MAX_BUNDLE_BYTES:
        raise ValidationError("The input APKM exceeds the supported file size limits.")
    actual_hash = file_sha256(path)
    if not hmac.compare_digest(expected_hash, actual_hash):
        raise ValidationError("The APKM SHA-256 does not match the required expected digest.")
    if destination.exists() or destination.is_symlink():
        raise ValidationError("The split extraction directory must not already exist.")
    created = False
    try:
        with zipfile.ZipFile(path) as archive:
            entries = _entries(archive, outer=True)
            apk_entries = [entry for entry in entries if not entry.is_dir()
                           and entry.filename.lower().endswith(".apk")]
            if not 1 <= len(apk_entries) <= MAX_APKS:
                raise ValidationError("The APKM has an unsupported APK count.")
            destination.mkdir(mode=0o700)
            created = True
            apk_names = {entry.filename for entry in apk_entries}
            for entry in entries:
                if entry.is_dir():
                    continue
                if entry.filename in apk_names:
                    with (destination / entry.filename).open("xb") as output:
                        _stream(archive, entry, output)
                else:
                    # Verify all sidecar CRCs but never extract or trust them.
                    _stream(archive, entry)
        splits = []
        expanded_splits = 0
        for entry in sorted(apk_entries, key=lambda item: item.filename):
            split_path = destination / entry.filename
            split_hash = file_sha256(split_path)
            archive_report = _inspect_split(split_path)
            expanded_splits += archive_report["uncompressed_bytes"]
            if expanded_splits > MAX_INNER_EXPANDED_BYTES:
                raise ValidationError("The split APKs exceed the total uncompressed size limit.")
            aapt_binary = aapt or sdk_tool("aapt")
            badging = run_command([aapt_binary, "dump", "badging", str(split_path)], "aapt badging")
            xmltree = run_command([aapt_binary, "dump", "xmltree", str(split_path),
                                   "AndroidManifest.xml"], "aapt manifest")
            manifest = _manifest(badging, xmltree, archive_report)
            signer = inspect_signer(run_command([
                apksigner or sdk_tool("apksigner"), "verify", "--print-certs", str(split_path),
            ], "apksigner verification"), expected_signer)
            if not hmac.compare_digest(split_hash, file_sha256(split_path)):
                raise ValidationError("A split APK changed during validation.")
            splits.append({"file": entry.filename, "sha256": split_hash,
                           "size_bytes": entry.file_size, "manifest": manifest,
                           "signature": signer, "archive": archive_report})
        base = [split for split in splits if split["manifest"]["split_name"] is None]
        if len(base) != 1:
            raise ValidationError("The APKM must contain exactly one base APK.")
        if not base[0]["archive"]["has_classes_dex"] or base[0]["manifest"]["is_feature_split"]:
            raise ValidationError("The base APK must contain application code and no feature identity.")
        by_name = {}
        for split in splits:
            name = split["manifest"]["split_name"]
            if name is not None:
                if name in by_name:
                    raise ValidationError("The APKM contains duplicate split identities.")
                by_name[name] = split["manifest"]
        available_types = {value for split in splits for value in split["manifest"]["split_types"]}
        for split in splits:
            manifest = split["manifest"]
            targets = manifest["dependencies"] + ([manifest["config_for_split"]]
                                                  if manifest["config_for_split"] else [])
            if any(target == manifest["split_name"] or target not in by_name or
                   not by_name[target]["is_feature_split"] for target in targets):
                raise ValidationError("A split requires an absent or invalid feature dependency.")
            if not set(manifest["required_split_types"]).issubset(available_types):
                raise ValidationError("A split requires missing split types.")
        visiting: set[str] = set()
        visited: set[str] = set()

        def visit(name: str) -> None:
            if name in visiting:
                raise ValidationError("The bundle contains cyclic feature dependencies.")
            if name in visited:
                return
            visiting.add(name)
            for dependency in by_name[name]["dependencies"]:
                visit(dependency)
            visiting.remove(name)
            visited.add(name)

        for name in by_name:
            visit(name)
        abis = sorted({abi for split in splits for abi in split["manifest"]["native_abis"]})
        if "arm64-v8a" not in abis:
            raise ValidationError("The bundle does not contain arm64-v8a native libraries.")
        if not hmac.compare_digest(actual_hash, file_sha256(path)):
            raise ValidationError("The APKM changed during validation.")
        return {"schema_version": 1, "status": "accepted", "format": "apkm",
                "apkm": {"sha256": actual_hash, "size_bytes": info.st_size},
                "manifest": {"package": PACKAGE_NAME, "version_name": SUPPORTED_VERSION,
                             "version_code": SUPPORTED_VERSION_CODE, "native_abis": abis},
                "base_file": base[0]["file"], "split_count": len(splits), "splits": splits,
                "signature": base[0]["signature"],
                "outer_metadata_trusted": False}
    except ValidationError:
        if created:
            shutil.rmtree(destination, ignore_errors=True)
        raise
    except ZIP_ERRORS as exc:
        if created:
            shutil.rmtree(destination, ignore_errors=True)
        raise ValidationError("The APKM or split ZIP structure or entry integrity is invalid.") from exc


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apkm", type=Path, required=True)
    parser.add_argument("--sha256", required=True)
    parser.add_argument("--signer-sha256", required=True)
    parser.add_argument("--extract-dir", type=Path, required=True)
    parser.add_argument("--aapt")
    parser.add_argument("--apksigner")
    parser.add_argument("--report", type=Path)
    args = parser.parse_args(argv)
    if args.report and (args.report.resolve() == args.apkm.resolve() or
                        args.extract_dir.resolve() in args.report.resolve().parents):
        parser.error("The report must not overwrite the input or extracted splits.")
    try:
        report = validate_apkm(args.apkm, args.sha256, args.signer_sha256,
                               args.extract_dir, args.aapt, args.apksigner)
        status = 0
    except ValidationError as exc:
        report = {"schema_version": 1, "status": "rejected", "error": str(exc)}
        status = 1
    encoded = json.dumps(report, indent=2, sort_keys=True) + "\n"
    if args.report:
        args.report.parent.mkdir(parents=True, exist_ok=True)
        args.report.write_text(encoded, encoding="utf-8")
    print(encoded, end="")
    return status


if __name__ == "__main__":
    sys.exit(main())
