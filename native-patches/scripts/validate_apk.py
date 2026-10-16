#!/usr/bin/env python3
"""Fail closed before patching a user-supplied, single Instagram APK.

This module neither downloads APKs nor establishes Meta's certificate identity.
The expected certificate digest must come from a separately trusted installation.
It never reads credentials, decodes messages, or modifies the input archive.
"""

from __future__ import annotations

import argparse
import hashlib
import hmac
import json
import os
from pathlib import Path
import re
import stat
import struct
import subprocess
import sys
import tempfile
import zipfile
import zlib


PACKAGE_NAME = "com.instagram.android"
SUPPORTED_VERSIONS = frozenset({"451.0.0.20.80"})
REQUIRED_ABI = "arm64-v8a"
MAX_APK_BYTES = 1024 * 1024 * 1024
MAX_ENTRY_BYTES = 512 * 1024 * 1024
MAX_MANIFEST_BYTES = 8 * 1024 * 1024
MAX_EXPANDED_BYTES = 4 * 1024 * 1024 * 1024
MAX_ENTRIES = 50_000
MAX_FILENAME_BYTES = 1024
MAX_COMPRESSION_RATIO = 200
MAX_TOOL_OUTPUT_BYTES = 2 * 1024 * 1024
TOOL_TIMEOUT_SECONDS = 120
READ_CHUNK_BYTES = 1024 * 1024


class ValidationError(ValueError):
    """A fixed, non-sensitive diagnostic suitable for a validation report."""


def normalize_digest(value: str, label: str, *, allow_colons: bool = False) -> str:
    digest = value.strip()
    if allow_colons:
        digest = digest.replace(":", "")
    if not re.fullmatch(r"[0-9a-fA-F]{64}", digest):
        raise ValidationError(f"{label} must contain exactly 64 hexadecimal digits.")
    return digest.lower()


def sdk_tool(name: str) -> str:
    """Resolve the pinned SDK tools without logging environment values."""
    for variable in ("ANDROID_HOME", "ANDROID_SDK_ROOT"):
        sdk = os.environ.get(variable)
        if sdk:
            candidate = Path(sdk) / "build-tools" / "35.0.0" / name
            if candidate.is_file():
                return str(candidate)
    return name


def run_command(arguments: list[str], label: str) -> str:
    """No shell, bounded readable output, finite runtime, and safe errors."""
    try:
        with tempfile.TemporaryFile() as output:
            completed = subprocess.run(
                arguments,
                stdin=subprocess.DEVNULL,
                stdout=output,
                stderr=subprocess.STDOUT,
                timeout=TOOL_TIMEOUT_SECONDS,
                check=False,
                shell=False,
            )
            if completed.returncode != 0:
                raise ValidationError(f"{label} rejected the APK.")
            if output.tell() > MAX_TOOL_OUTPUT_BYTES:
                raise ValidationError(f"{label} returned excessive output.")
            output.seek(0)
            return output.read(MAX_TOOL_OUTPUT_BYTES + 1).decode("utf-8", errors="strict")
    except subprocess.TimeoutExpired as exc:
        raise ValidationError(f"{label} timed out.") from exc
    except (OSError, UnicodeError) as exc:
        raise ValidationError(f"{label} could not be executed successfully.") from exc


def file_sha256(path: Path) -> str:
    digest = hashlib.sha256()
    try:
        with path.open("rb") as source:
            while chunk := source.read(READ_CHUNK_BYTES):
                digest.update(chunk)
    except OSError as exc:
        raise ValidationError("The input APK could not be read.") from exc
    return digest.hexdigest()


def _safe_entry_name(name: str) -> str:
    if (
        not name
        or len(name.encode("utf-8")) > MAX_FILENAME_BYTES
        or name.startswith("/")
        or "\\" in name
        or any(ord(character) < 32 or ord(character) == 127 for character in name)
    ):
        raise ValidationError("The APK contains an unsafe ZIP entry path.")
    canonical = name[:-1] if name.endswith("/") else name
    components = canonical.split("/")
    if any(component in ("", ".", "..") for component in components) or ":" in components[0]:
        raise ValidationError("The APK contains an unsafe ZIP entry path.")
    return canonical


def inspect_archive(path: Path) -> dict:
    """Validate metadata before any external Android parser sees the file."""
    try:
        with zipfile.ZipFile(path) as archive:
            entries = archive.infolist()
            if not entries or len(entries) > MAX_ENTRIES:
                raise ValidationError("The APK has an unsupported ZIP entry count.")
            names: set[str] = set()
            expanded_bytes = 0
            native_abis: set[str] = set()
            for entry in entries:
                # ZipInfo.filename truncates NULs; orig_filename retains them.
                canonical = _safe_entry_name(entry.orig_filename)
                if canonical in names:
                    raise ValidationError("The APK contains duplicate ZIP entry paths.")
                names.add(canonical)
                # Android APKs require classic local ZIP sizes. Forcing ZIP64
                # on small streamed entries can pass Python/apksigner checks
                # while Android's native ZIP reader rejects the header.
                assert archive.fp is not None
                archive.fp.seek(entry.header_offset)
                local = archive.fp.read(30)
                if len(local) != 30 or local[:4] != b"PK\x03\x04":
                    raise ValidationError("The APK contains an invalid local ZIP header.")
                header = struct.unpack("<4s5H3I2H", local)
                if header[7] == 0xffffffff or header[8] == 0xffffffff:
                    raise ValidationError("ZIP64 local entry sizes are not supported in Android APKs.")
                if header[2] != entry.flag_bits or header[3] != entry.compress_type:
                    raise ValidationError("The APK local and central ZIP headers differ.")
                if not (header[2] & 8) and (header[6], header[7], header[8]) != (entry.CRC, entry.compress_size, entry.file_size):
                    raise ValidationError("The APK local entry sizes or checksum differ from its directory.")
                kind = stat.S_IFMT(entry.external_attr >> 16)
                if kind not in (0, stat.S_IFREG, stat.S_IFDIR):
                    raise ValidationError("The APK contains a non-regular ZIP entry.")
                if entry.flag_bits & 1:
                    raise ValidationError("Encrypted ZIP entries are not supported.")
                if entry.compress_type not in (zipfile.ZIP_STORED, zipfile.ZIP_DEFLATED):
                    raise ValidationError("The APK uses an unsupported ZIP compression method.")
                if entry.file_size > MAX_ENTRY_BYTES:
                    raise ValidationError("An APK entry exceeds the uncompressed size limit.")
                if canonical == "AndroidManifest.xml" and entry.file_size > MAX_MANIFEST_BYTES:
                    raise ValidationError("The Android manifest exceeds the size limit.")
                expanded_bytes += entry.file_size
                if expanded_bytes > MAX_EXPANDED_BYTES:
                    raise ValidationError("The APK exceeds the total uncompressed size limit.")
                if entry.file_size and (
                    not entry.compress_size
                    or entry.file_size > entry.compress_size * MAX_COMPRESSION_RATIO
                ):
                    raise ValidationError("The APK exceeds the ZIP compression ratio limit.")
                if not entry.is_dir() and canonical.lower().endswith((".apk", ".apks", ".xapk")):
                    raise ValidationError("Nested APK or APK-bundle archives are not supported.")
                parts = canonical.split("/")
                if (
                    len(parts) >= 3
                    and parts[0] == "lib"
                    and re.fullmatch(r"[A-Za-z0-9_-]{1,32}", parts[1])
                    and not entry.is_dir()
                    and parts[-1].endswith(".so")
                ):
                    native_abis.add(parts[1])
            if "AndroidManifest.xml" not in names or "classes.dex" not in names:
                raise ValidationError("The file is not a single installable APK archive.")
            if REQUIRED_ABI not in native_abis:
                raise ValidationError("The APK does not contain arm64-v8a native libraries.")
            # Streaming reads verify CRCs, truncated entries and overlapping ZIP
            # records. Header limits above bound decompression before these reads.
            for entry in entries:
                if not entry.is_dir():
                    read_bytes = 0
                    with archive.open(entry) as source:
                        while chunk := source.read(READ_CHUNK_BYTES):
                            read_bytes += len(chunk)
                            if read_bytes > entry.file_size or read_bytes > MAX_ENTRY_BYTES:
                                raise ValidationError("An APK entry exceeds its declared size.")
            return {
                "entry_count": len(entries),
                "uncompressed_bytes": expanded_bytes,
                "native_abis": sorted(native_abis),
            }
    except ValidationError:
        raise
    except (
        OSError, ValueError, RuntimeError, zipfile.BadZipFile,
        zipfile.LargeZipFile, NotImplementedError, EOFError, zlib.error,
    ) as exc:
        raise ValidationError("The APK ZIP structure or entry integrity is invalid.") from exc


def inspect_manifest(badging: str, xmltree: str, archive_abis: list[str]) -> dict:
    packages = re.findall(r"^package:\s*(.+)$", badging, flags=re.MULTILINE)
    if len(packages) != 1:
        raise ValidationError("aapt did not return a unique APK package.")
    attributes = dict(re.findall(r"([A-Za-z][A-Za-z0-9_:-]*)='([^']*)'", packages[0]))
    if attributes.get("name") != PACKAGE_NAME:
        raise ValidationError("The APK package is not com.instagram.android.")
    if attributes.get("versionName") not in SUPPORTED_VERSIONS:
        raise ValidationError("The APK version is not in the supported version allowlist.")
    if not re.fullmatch(r"[0-9]{1,20}", attributes.get("versionCode", "")):
        raise ValidationError("The APK has an invalid version code.")
    if any("split" in name.lower() and value for name, value in attributes.items()):
        raise ValidationError("Split APKs are not supported; supply a complete standalone APK.")
    if re.search(r"^\s*uses-split:", badging, flags=re.MULTILINE):
        raise ValidationError("The APK requires split packages and is not standalone.")
    if not re.search(r"^\s*E:\s+manifest(?:\s|$)", xmltree, flags=re.MULTILINE):
        raise ValidationError("aapt did not return a readable Android manifest.")
    if re.search(r"^\s*E:\s+uses-split(?:\s|$)", xmltree, flags=re.MULTILINE):
        raise ValidationError("The APK requires split packages and is not standalone.")
    for line in xmltree.splitlines():
        if re.search(r"\bA:\s+(?:android:)?(?:split|requiredSplitTypes|splitTypes)(?:\(|=)", line):
            # A standalone manifest should not declare any split identity/types.
            if not re.search(r'=\s*""(?:\s|$)', line):
                raise ValidationError("The manifest declares a split APK identity or requirement.")
        if re.search(r"\bA:\s+android:isSplitRequired(?:\(|=)", line):
            if not re.search(r"=(?:\(type\s+0x12\))?(?:0x0+|false)(?:\s|$)", line):
                raise ValidationError("The manifest requires additional split APKs.")
    abi_lines = re.findall(r"^native-code:\s*(.*)$", badging, flags=re.MULTILINE)
    if len(abi_lines) != 1:
        raise ValidationError("aapt did not return a unique native ABI list.")
    abis = sorted(set(re.findall(r"'([A-Za-z0-9_-]{1,32})'", abi_lines[0])))
    if REQUIRED_ABI not in abis or REQUIRED_ABI not in archive_abis:
        raise ValidationError("The APK metadata does not support arm64-v8a.")
    if set(abis) != set(archive_abis):
        raise ValidationError("The APK ABI metadata does not match its native libraries.")
    return {
        "package": attributes["name"],
        "version_name": attributes["versionName"],
        "version_code": attributes["versionCode"],
        "native_abis": abis,
    }


def inspect_signer(output: str, expected_digest: str) -> dict:
    certificates = re.findall(
        r"^Signer #[0-9]+ certificate SHA-256 digest:\s*([0-9a-fA-F:]+)\s*$",
        output,
        flags=re.MULTILINE,
    )
    rotation = re.findall(
        r"^Signer \(minSdkVersion=([0-9]+), maxSdkVersion=([0-9]+)\) certificate SHA-256 digest:\s*([0-9a-fA-F:]+)\s*$",
        output, flags=re.MULTILINE,
    )
    ranges = []
    if rotation:
        if certificates:
            raise ValidationError("Ambiguous APK signer metadata.")
        for minimum, maximum, digest in rotation:
            ranges.append({"min_sdk": int(minimum), "max_sdk": int(maximum),
                           "certificate_sha256": normalize_digest(digest, "APK signer", allow_colons=True)})
        ranges.sort(key=lambda entry: entry["min_sdk"])
        if any(entry["min_sdk"] > entry["max_sdk"] for entry in ranges) or any(
            previous["max_sdk"] >= following["min_sdk"] for previous, following in zip(ranges, ranges[1:])
        ):
            raise ValidationError("Ambiguous overlapping APK signer ranges.")
        # apksigner verifies the cryptographic signing lineage before this
        # parser runs. Pin the active certificate for the newest platform range.
        actual = ranges[-1]["certificate_sha256"]
    else:
        if len(certificates) != 1:
            raise ValidationError("Exactly one APK signer certificate is required.")
        actual = normalize_digest(certificates[0], "APK signer certificate SHA-256", allow_colons=True)
    if not hmac.compare_digest(actual, expected_digest):
        raise ValidationError("The APK signer does not match the separately trusted expected certificate.")
    return {
        "verified": True,
        "certificate_sha256": actual,
        "trust_source": "user_supplied_expected_digest",
        "official_distribution_independently_verified": False,
        "platform_signers": ranges,
    }


def validate_apk(
    apk: Path,
    sha256: str,
    signer_sha256: str,
    aapt: str | None = None,
    apksigner: str | None = None,
) -> dict:
    expected_hash = normalize_digest(sha256, "Expected APK SHA-256")
    expected_signer = normalize_digest(signer_sha256, "Expected signer SHA-256", allow_colons=True)
    path = Path(apk).expanduser()
    if path.suffix.lower() != ".apk":
        raise ValidationError("Supply one .apk file, not an APK bundle or ZIP download.")
    try:
        info = path.stat()
    except OSError as exc:
        raise ValidationError("The input APK does not exist or cannot be inspected.") from exc
    if not stat.S_ISREG(info.st_mode):
        raise ValidationError("The input APK must be a regular local file.")
    if info.st_size <= 0 or info.st_size > MAX_APK_BYTES:
        raise ValidationError("The input APK exceeds the supported file size limits.")
    actual_hash = file_sha256(path)
    if not hmac.compare_digest(actual_hash, expected_hash):
        raise ValidationError("The APK SHA-256 does not match the required expected digest.")
    archive = inspect_archive(path)
    aapt_binary = aapt or sdk_tool("aapt")
    badging = run_command([aapt_binary, "dump", "badging", str(path)], "aapt badging")
    xmltree = run_command(
        [aapt_binary, "dump", "xmltree", str(path), "AndroidManifest.xml"], "aapt manifest"
    )
    manifest = inspect_manifest(badging, xmltree, archive["native_abis"])
    signer_output = run_command(
        [apksigner or sdk_tool("apksigner"), "verify", "--print-certs", str(path)],
        "apksigner verification",
    )
    signer = inspect_signer(signer_output, expected_signer)
    # Catch accidental file replacement during tool execution before reporting
    # an input digest that no longer belongs to the file about to be patched.
    if not hmac.compare_digest(file_sha256(path), actual_hash):
        raise ValidationError("The APK changed during validation.")
    return {
        "schema_version": 1,
        "status": "accepted",
        "apk": {"sha256": actual_hash, "size_bytes": info.st_size},
        "manifest": manifest,
        "signature": signer,
        "archive": archive,
    }


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apk", required=True, type=Path)
    parser.add_argument("--sha256", required=True, help="Separately obtained expected APK SHA-256.")
    parser.add_argument(
        "--signer-sha256", required=True, help="Expected certificate SHA-256 from a trusted installation."
    )
    parser.add_argument("--aapt", default=None)
    parser.add_argument("--apksigner", default=None)
    parser.add_argument("--report", type=Path)
    arguments = parser.parse_args(argv)
    if arguments.report and arguments.report.resolve() == arguments.apk.resolve():
        print(json.dumps({"status": "rejected", "error": "The report must not overwrite the APK."}))
        return 1
    try:
        report = validate_apk(
            arguments.apk, arguments.sha256, arguments.signer_sha256, arguments.aapt, arguments.apksigner
        )
        status = 0
    except ValidationError as exc:
        report = {"schema_version": 1, "status": "rejected", "error": str(exc)}
        status = 1
    encoded = json.dumps(report, indent=2, sort_keys=True) + "\n"
    if arguments.report:
        try:
            arguments.report.parent.mkdir(parents=True, exist_ok=True)
            arguments.report.write_text(encoded, encoding="utf-8")
        except OSError:
            print(json.dumps({"status": "rejected", "error": "The validation report could not be written."}))
            return 1
    print(encoded, end="")
    return status


if __name__ == "__main__":
    sys.exit(main())
