"""Repair one APKEditor binary-XML namespace ordering defect before patching.

Only adjacent namespace END chunks may move. Every chunk, including each
namespace declaration, otherwise retains its exact bytes. This does not decode
or recompile resources, change application components, or alter authentication.
It is a packaging correction, not evidence of the phone's installation failure.
"""
from __future__ import annotations

from collections import Counter, defaultdict, deque
import copy
import hashlib
import os
from pathlib import Path
import re
import shutil
import stat
import struct
import tempfile
import zipfile

from validate_apk import MAX_MANIFEST_BYTES, READ_CHUNK_BYTES, ValidationError, inspect_archive


XML = 0x0003
START_NAMESPACE = 0x0100
END_NAMESPACE = 0x0101
START_ELEMENT = 0x0102
END_ELEMENT = 0x0103
MAX_CHUNKS = 100_000
MAX_DEPTH = 2048


def _chunks(data: bytes) -> list[tuple[int, int, int, int]]:
    """Return (offset, type, header size, total size) after bounded framing checks."""
    if not isinstance(data, bytes) or not 8 <= len(data) <= MAX_MANIFEST_BYTES:
        raise ValidationError("The binary Android manifest has an invalid size.")
    kind, header_size, size = struct.unpack_from("<HHI", data)
    if kind != XML or header_size != 8 or size != len(data) or size % 4:
        raise ValidationError("The binary Android manifest has an invalid document header.")
    result = []
    offset = header_size
    while offset < size:
        if size - offset < 8 or len(result) >= MAX_CHUNKS:
            raise ValidationError("The binary Android manifest has an invalid chunk table.")
        kind, chunk_header, chunk_size = struct.unpack_from("<HHI", data, offset)
        if (chunk_header < 8 or chunk_header % 4 or chunk_size < chunk_header
                or chunk_size % 4 or chunk_size > size - offset):
            raise ValidationError("The binary Android manifest contains a truncated or invalid chunk.")
        if kind in (START_NAMESPACE, END_NAMESPACE, START_ELEMENT, END_ELEMENT):
            minimum_extension = 20 if kind == START_ELEMENT else 8
            if chunk_header < 16 or chunk_size - chunk_header < minimum_extension:
                raise ValidationError("The binary Android manifest contains an incomplete XML node.")
        result.append((offset, kind, chunk_header, chunk_size))
        offset += chunk_size
    return result


def normalize_namespace_order(data: bytes) -> tuple[bytes, int]:
    """Normalize adjacent namespace closings to LIFO order; reject uncertain input.

    A closing block must contain exactly the declarations at the top of the
    current stack, at the same element depth. A declaration cannot move across
    an element, text, or any other chunk. The count is the number of closing
    chunks whose position changes. Valid input is returned byte for byte.
    """
    chunks = _chunks(data)
    namespaces: list[tuple[tuple[int, int], int]] = []
    elements: list[tuple[int, int]] = []
    root_elements = 0
    output = bytearray(data)
    changed = 0
    index = 0
    while index < len(chunks):
        offset, kind, header_size, size = chunks[index]
        if kind == START_NAMESPACE:
            if len(namespaces) >= MAX_DEPTH:
                raise ValidationError("The binary Android manifest has excessive namespace depth.")
            pair = struct.unpack_from("<II", data, offset + header_size)
            namespaces.append((pair, len(elements)))
        elif kind == END_NAMESPACE:
            block = []
            end = index
            while end < len(chunks) and chunks[end][1] == END_NAMESPACE:
                start, _, header, length = chunks[end]
                pair = struct.unpack_from("<II", data, start + header)
                block.append((pair, data[start:start + length]))
                end += 1
            count = len(block)
            if count > len(namespaces):
                raise ValidationError("The binary Android manifest closes an undeclared namespace.")
            expected = list(reversed(namespaces[-count:]))
            if (any(depth != len(elements) for _, depth in expected)
                    or Counter(pair for pair, _ in block) != Counter(pair for pair, _ in expected)):
                raise ValidationError("The binary Android manifest has mismatched namespace scopes.")
            available: dict[tuple[int, int], deque] = defaultdict(deque)
            for position, (pair, raw) in enumerate(block):
                available[pair].append((position, raw))
            normalized = []
            for position, (pair, _) in enumerate(expected):
                original_position, raw = available[pair].popleft()
                changed += original_position != position
                normalized.append(raw)
            # The replacement has the same length even if legal chunk headers
            # include opaque extra bytes. No other chunk offset changes.
            output[offset:chunks[end - 1][0] + chunks[end - 1][3]] = b"".join(normalized)
            del namespaces[-count:]
            index = end
            continue
        elif kind == START_ELEMENT:
            if len(elements) >= MAX_DEPTH:
                raise ValidationError("The binary Android manifest has excessive element depth.")
            if not elements:
                root_elements += 1
                if root_elements > 1:
                    raise ValidationError("The binary Android manifest has multiple root elements.")
            elements.append(struct.unpack_from("<II", data, offset + header_size))
        elif kind == END_ELEMENT:
            pair = struct.unpack_from("<II", data, offset + header_size)
            if not elements or elements.pop() != pair:
                raise ValidationError("The binary Android manifest has mismatched element nodes.")
        index += 1
    if namespaces or elements or root_elements != 1:
        raise ValidationError("The binary Android manifest has unclosed XML scopes.")
    return (bytes(output), changed) if changed else (data, 0)


def _require_unsigned(archive: zipfile.ZipFile) -> None:
    """A caller must supply the merger's unsigned intermediate, before signing."""
    if any(re.fullmatch(r"META-INF/[^/]+\.(SF|RSA|DSA|EC)", entry.filename, re.IGNORECASE)
           for entry in archive.infolist()):
        raise ValidationError("Namespace normalization requires an unsigned intermediate APK.")
    # APK v2/v3 signatures are outside ZIP entries, immediately before the
    # central directory. Never silently invalidate one by repacking its file.
    assert archive.fp is not None
    if archive.start_dir >= 16:
        archive.fp.seek(archive.start_dir - 16)
        if archive.fp.read(16) == b"APK Sig Block 42":
            raise ValidationError("Namespace normalization requires an unsigned intermediate APK.")


def normalize_apk_manifest(apk: Path) -> dict:
    """Atomically normalize an unsigned intermediate, preserving every ZIP entry.

    All nonmanifest payloads and all compression methods are retained. This
    repack does not preserve ZIP alignment; the final pipeline must zipalign
    and sign after patching. An already-correct archive is not rewritten.
    """
    path = Path(apk)
    temporary: Path | None = None
    try:
        attributes = path.lstat()
        if not stat.S_ISREG(attributes.st_mode):
            raise ValidationError("Namespace normalization requires a regular APK file.")
        archive_report = inspect_archive(path)
        with zipfile.ZipFile(path) as source:
            _require_unsigned(source)
            manifest = source.read("AndroidManifest.xml")
            normalized, changed = normalize_namespace_order(manifest)
            report = {
                "status": "normalized" if changed else "unchanged",
                "namespace_end_chunks_reordered": changed,
                "manifest_sha256_before": hashlib.sha256(manifest).hexdigest(),
                "manifest_sha256_after": hashlib.sha256(normalized).hexdigest(),
                "entries_preserved": archive_report["entry_count"],
            }
            if not changed:
                return report
            descriptor, name = tempfile.mkstemp(prefix=".namespace-", suffix=".apk", dir=path.parent)
            os.close(descriptor)
            temporary = Path(name)
            with zipfile.ZipFile(temporary, "w", allowZip64=True) as destination:
                destination.comment = source.comment
                for entry in source.infolist():
                    metadata = copy.copy(entry)
                    with destination.open(metadata, "w", force_zip64=True) as target:
                        if entry.filename == "AndroidManifest.xml":
                            target.write(normalized)
                        else:
                            with source.open(entry) as content:
                                shutil.copyfileobj(content, target, READ_CHUNK_BYTES)
        # Read every new entry and verify its CRC before the atomic replacement.
        inspect_archive(temporary)
        os.chmod(temporary, stat.S_IMODE(attributes.st_mode))
        with temporary.open("rb") as completed:
            os.fsync(completed.fileno())
        os.replace(temporary, path)
        temporary = None
        return report
    except ValidationError:
        raise
    except (OSError, ValueError, RuntimeError, zipfile.BadZipFile, EOFError) as exc:
        raise ValidationError("The unsigned APK manifest could not be normalized safely.") from exc
    finally:
        if temporary is not None:
            temporary.unlink(missing_ok=True)
