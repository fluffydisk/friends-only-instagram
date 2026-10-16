"""Give a personally rebuilt unsigned APK an explicit Android version code.

This changes the root manifest's integer versionCode only. It does not change
the source APK, Android's downgrade rules, signing identity, or account logic.
"""
from __future__ import annotations

import copy
import hashlib
import os
from pathlib import Path
import shutil
import stat
import struct
import tempfile
import zipfile

from axml import START_ELEMENT, _chunks, _require_unsigned, normalize_namespace_order
from validate_apk import READ_CHUNK_BYTES, ValidationError, inspect_archive


ANDROID_NAMESPACE = "http://schemas.android.com/apk/res/android"
VERSION_CODE = 0x0101021B
VERSION_CODE_MAJOR = 0x01010576
MAX_VERSION_CODE = 2_100_000_000


def _string_reader(data: bytes, pool: tuple[int, int, int, int]):
    offset, _, header, size = pool
    if header < 28:
        raise ValidationError("The manifest string pool has an incomplete header.")
    count, styles, flags, strings_start, styles_start = struct.unpack_from("<IIIII", data, offset + 8)
    if (count > 100_000 or styles > count or header + 4 * (count + styles) > size
            or strings_start < header + 4 * (count + styles) or strings_start > size
            or (styles_start and not strings_start <= styles_start <= size)):
        raise ValidationError("The manifest string pool has invalid bounds.")
    end = offset + (styles_start or size)

    def read_length(position: int, *, utf8: bool) -> tuple[int, int]:
        width, marker, mask = (1, 0x80, 0x7f) if utf8 else (2, 0x8000, 0x7fff)
        if position + width > end:
            raise ValidationError("The manifest string pool contains a truncated length.")
        value = int.from_bytes(data[position:position + width], "little")
        position += width
        if value & marker:
            if position + width > end:
                raise ValidationError("The manifest string pool contains a truncated length.")
            value = ((value & mask) << (8 if utf8 else 16)) | int.from_bytes(data[position:position + width], "little")
            position += width
        return value, position

    def read(index: int) -> str:
        if index >= count:
            raise ValidationError("A manifest attribute references an invalid string.")
        relative = struct.unpack_from("<I", data, offset + header + index * 4)[0]
        position = offset + strings_start + relative
        if not offset + strings_start <= position < end:
            raise ValidationError("A manifest string has an invalid offset.")
        utf8 = bool(flags & 0x100)
        length, position = read_length(position, utf8=utf8)
        if utf8:
            length, position = read_length(position, utf8=True)
        else:
            length *= 2
        terminator = 1 if utf8 else 2
        if position + length + terminator > end or any(data[position + length:position + length + terminator]):
            raise ValidationError("The manifest string pool contains a truncated string.")
        try:
            return data[position:position + length].decode("utf-8" if utf8 else "utf-16-le", errors="strict")
        except UnicodeError as exc:
            raise ValidationError("The manifest string pool contains invalid text.") from exc
    return read


def _version_attribute(data: bytes) -> tuple[int, int]:
    chunks = _chunks(data)
    # Reuse scope validation without taking its optional namespace reorder.
    # This operation must change only the version integer's four bytes.
    normalize_namespace_order(data)
    pools = [chunk for chunk in chunks if chunk[1] == 0x0001]
    maps = [chunk for chunk in chunks if chunk[1] == 0x0180]
    if len(pools) != 1 or len(maps) != 1:
        raise ValidationError("The manifest must have one string pool and resource map.")
    read_string = _string_reader(data, pools[0])
    map_offset, _, map_header, map_size = maps[0]
    ids = struct.unpack_from("<" + "I" * ((map_size - map_header) // 4), data, map_offset + map_header)
    root = next(chunk for chunk in chunks if chunk[1] == START_ELEMENT)
    offset, _, header, size = root
    extension = offset + header
    namespace, name, attributes_start, attribute_size, count = struct.unpack_from("<IIHHH", data, extension)
    if namespace != 0xffffffff or read_string(name) != "manifest":
        raise ValidationError("The version update requires the Android manifest root element.")
    if (attributes_start < 20 or attributes_start % 4 or attribute_size < 20 or attribute_size % 4
            or header + attributes_start + attribute_size * count > size):
        raise ValidationError("The manifest root has invalid attribute bounds.")
    found = []
    majors = 0
    for index in range(count):
        attribute = extension + attributes_start + index * attribute_size
        namespace, name, raw, value_size, reserved, kind, value = struct.unpack_from("<IIIHBBI", data, attribute)
        resource_id = ids[name] if name < len(ids) else None
        if resource_id not in (VERSION_CODE, VERSION_CODE_MAJOR):
            # An Android-namespaced version field with the wrong resource ID
            # must not quietly become an unrelated attribute.
            if (namespace != 0xffffffff and read_string(namespace) == ANDROID_NAMESPACE
                    and read_string(name) in ("versionCode", "versionCodeMajor")):
                raise ValidationError("The manifest version attribute has an invalid resource mapping.")
            continue
        expected_name = "versionCode" if resource_id == VERSION_CODE else "versionCodeMajor"
        if (namespace == 0xffffffff or read_string(namespace) != ANDROID_NAMESPACE
                or read_string(name) != expected_name or value_size != 8 or reserved
                or kind not in (0x10, 0x11) or raw != 0xffffffff):
            raise ValidationError("The manifest version must use an unambiguous typed Android integer.")
        if resource_id == VERSION_CODE_MAJOR:
            majors += 1
            if majors > 1 or value != 0:
                raise ValidationError("A nonzero or duplicate versionCodeMajor is unsupported.")
        else:
            found.append((attribute + 16, value))
    if len(found) != 1 or not 1 <= found[0][1] <= MAX_VERSION_CODE:
        raise ValidationError("The manifest must contain one supported positive versionCode.")
    return found[0]


def set_manifest_version_code(data: bytes, version: int) -> bytes:
    """Replace exactly one typed root versionCode integer, preserving other bytes."""
    if type(version) is not int or not 1 <= version <= MAX_VERSION_CODE:
        raise ValidationError("The output version code must be an integer from 1 to 2100000000.")
    offset, previous = _version_attribute(data)
    if previous == version:
        return data
    result = bytearray(data)
    struct.pack_into("<I", result, offset, version)
    return bytes(result)


def set_apk_version_code(unsigned_apk: Path, version: int) -> dict:
    """Atomically update an unsigned intermediate, before final zipalign/signing."""
    path = Path(unsigned_apk)
    temporary: Path | None = None
    try:
        attributes = path.lstat()
        if not stat.S_ISREG(attributes.st_mode):
            raise ValidationError("The version update requires a regular unsigned APK file.")
        archive_report = inspect_archive(path)
        with zipfile.ZipFile(path) as source:
            _require_unsigned(source)
            manifest = source.read("AndroidManifest.xml")
            updated = set_manifest_version_code(manifest, version)
            _, previous = _version_attribute(manifest)
            report = {"status": "unchanged" if previous == version else "updated",
                      "version_code_before": previous, "version_code_after": version,
                      "manifest_sha256_before": hashlib.sha256(manifest).hexdigest(),
                      "manifest_sha256_after": hashlib.sha256(updated).hexdigest(),
                      "entries_preserved": archive_report["entry_count"]}
            if previous == version:
                return report
            descriptor, name = tempfile.mkstemp(prefix=".version-", suffix=".apk", dir=path.parent)
            os.close(descriptor)
            temporary = Path(name)
            with zipfile.ZipFile(temporary, "w", allowZip64=False) as destination:
                destination.comment = source.comment
                for entry in source.infolist():
                    with destination.open(copy.copy(entry), "w", force_zip64=False) as target:
                        if entry.filename == "AndroidManifest.xml":
                            target.write(updated)
                        else:
                            with source.open(entry) as content:
                                shutil.copyfileobj(content, target, READ_CHUNK_BYTES)
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
        raise ValidationError("The unsigned APK version could not be updated safely.") from exc
    finally:
        if temporary is not None:
            temporary.unlink(missing_ok=True)
