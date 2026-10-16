"""Version-code packaging tests use small synthetic binary-XML/ZIP fixtures."""
from pathlib import Path
import shutil
import struct
import sys
import tempfile
import unittest
from unittest import mock
import zipfile

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
from validate_apk import ValidationError
from version_code import (ANDROID_NAMESPACE, MAX_VERSION_CODE, VERSION_CODE, VERSION_CODE_MAJOR,
                          _version_attribute, set_apk_version_code, set_manifest_version_code)


OLD = 385709029
NEW = 385806736


def node(kind, extension):
    return struct.pack("<HHIII", kind, 16, 16 + len(extension), 1, 0xffffffff) + extension


def attribute(name=2, value=OLD, *, kind=0x10, raw=0xffffffff, namespace=1):
    return struct.pack("<IIIHBBI", namespace, name, raw, 8, 0, kind, value)


def document(attributes=None, *, utf8=True, resource_id=VERSION_CODE):
    strings = ["manifest", ANDROID_NAMESPACE, "versionCode", "versionCodeMajor", "package", "com.instagram.android"]
    offsets = []
    payload = bytearray()
    for string in strings:
        offsets.append(len(payload))
        if utf8:
            raw = string.encode("utf-8")
            payload += bytes([len(string), len(raw)]) + raw + b"\x00"
        else:
            payload += struct.pack("<H", len(string)) + string.encode("utf-16-le") + b"\x00\x00"
    payload += b"\x00" * (-len(payload) % 4)
    start = 28 + 4 * len(strings)
    pool = (struct.pack("<HHIIIIII", 1, 28, start + len(payload), len(strings), 0,
                        0x100 if utf8 else 0, start, 0)
            + struct.pack("<" + "I" * len(offsets), *offsets) + payload)
    resources = [0, 0, resource_id, VERSION_CODE_MAJOR, 0, 0]
    resource_map = struct.pack("<HHI", 0x180, 8, 8 + 4 * len(resources)) + struct.pack("<6I", *resources)
    attrs = [attribute()] if attributes is None else attributes
    extension = struct.pack("<IIHHHHHH", 0xffffffff, 0, 20, 20, len(attrs), 0, 0, 0) + b"".join(attrs)
    root = node(0x102, extension) + node(0x103, struct.pack("<II", 0xffffffff, 0))
    content = pool + resource_map + root
    return struct.pack("<HHI", 3, 8, 8 + len(content)) + content


class VersionManifestTests(unittest.TestCase):
    def test_changes_only_four_data_bytes_and_preserves_input(self):
        original = document()
        offset, before = _version_attribute(original)
        updated = set_manifest_version_code(original, NEW)
        self.assertEqual(before, OLD)
        self.assertEqual(_version_attribute(updated), (offset, NEW))
        self.assertEqual(updated[:offset], original[:offset])
        self.assertEqual(updated[offset + 4:], original[offset + 4:])
        self.assertEqual(_version_attribute(original)[1], OLD)

    def test_utf16_string_pool_and_hex_integer_supported(self):
        original = document([attribute(kind=0x11)], utf8=False)
        self.assertEqual(_version_attribute(set_manifest_version_code(original, NEW))[1], NEW)

    def test_same_code_returns_original_object(self):
        original = document()
        self.assertIs(set_manifest_version_code(original, OLD), original)

    def test_version_bounds_and_types(self):
        for value in [0, -1, MAX_VERSION_CODE + 1, True, False, "385806736", 1.0, None]:
            with self.subTest(value=value), self.assertRaises(ValidationError):
                set_manifest_version_code(document(), value)
        for value in [1, MAX_VERSION_CODE]:
            self.assertEqual(_version_attribute(set_manifest_version_code(document(), value))[1], value)

    def test_missing_or_duplicate_version_code_rejected(self):
        for attrs in [[], [attribute(), attribute()], [attribute(name=3, value=0)]]:
            with self.subTest(attrs=attrs), self.assertRaises(ValidationError):
                set_manifest_version_code(document(attrs), NEW)

    def test_nonzero_or_duplicate_major_rejected_zero_major_preserved(self):
        valid = document([attribute(), attribute(name=3, value=0)])
        offset, _ = _version_attribute(valid)
        updated = set_manifest_version_code(valid, NEW)
        self.assertEqual(updated[offset + 4:], valid[offset + 4:])
        for attrs in [[attribute(), attribute(name=3, value=1)],
                      [attribute(), attribute(name=3, value=0), attribute(name=3, value=0)]]:
            with self.subTest(attrs=attrs), self.assertRaises(ValidationError):
                set_manifest_version_code(document(attrs), NEW)

    def test_ambiguous_typed_values_or_wrong_namespace_rejected(self):
        for attrs in [[attribute(kind=3)], [attribute(raw=2)], [attribute(namespace=0xffffffff)],
                      [attribute(value=0)], [attribute(value=0xffffffff)]]:
            with self.subTest(attrs=attrs), self.assertRaises(ValidationError):
                set_manifest_version_code(document(attrs), NEW)
        with self.assertRaises(ValidationError):
            set_manifest_version_code(document(resource_id=0), NEW)

    def test_truncated_input_and_invalid_attribute_bounds_rejected(self):
        original = document()
        for length in [0, 7, 20, len(original) - 1]:
            with self.subTest(length=length), self.assertRaises(ValidationError):
                set_manifest_version_code(original[:length], NEW)
        from axml import _chunks
        root = next(chunk for chunk in _chunks(original) if chunk[1] == 0x102)
        malformed = bytearray(original)
        struct.pack_into("<H", malformed, root[0] + root[2] + 12, 65535)
        with self.assertRaises(ValidationError):
            set_manifest_version_code(bytes(malformed), NEW)

    def test_invalid_string_reference_rejected(self):
        original = document()
        offset, _ = _version_attribute(original)
        malformed = bytearray(original)
        struct.pack_into("<I", malformed, offset - 16, 9999)
        with self.assertRaises(ValidationError):
            set_manifest_version_code(bytes(malformed), NEW)


class VersionApkTests(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.directory = Path(temporary.name)
        self.apk = self.directory / "unsigned.apk"
        with zipfile.ZipFile(self.apk, "w") as archive:
            archive.comment = b"comment retained"
            archive.writestr("AndroidManifest.xml", document(), compress_type=zipfile.ZIP_DEFLATED)
            archive.writestr("classes.dex", b"code fixture", compress_type=zipfile.ZIP_STORED)
            archive.writestr("lib/arm64-v8a/libfixture.so", b"native fixture", compress_type=zipfile.ZIP_DEFLATED)
            archive.writestr("assets/data", b"asset fixture", compress_type=zipfile.ZIP_STORED)
        self.apk.chmod(0o600)

    def test_atomic_update_preserves_payloads_compression_order_and_original_source(self):
        source = self.directory / "original.apk"
        shutil.copyfile(self.apk, source)
        before = source.read_bytes()
        with zipfile.ZipFile(source) as archive:
            expected = [(i.filename, archive.read(i), i.compress_type, i.date_time, i.external_attr)
                        for i in archive.infolist()]
        report = set_apk_version_code(self.apk, NEW)
        self.assertEqual(report["version_code_before"], OLD)
        self.assertEqual(report["version_code_after"], NEW)
        self.assertEqual(report["entries_preserved"], 4)
        self.assertEqual(report["status"], "updated")
        with zipfile.ZipFile(self.apk) as archive:
            self.assertEqual(archive.namelist(), [item[0] for item in expected])
            self.assertEqual(archive.comment, b"comment retained")
            for name, payload, method, date, attributes in expected:
                entry = archive.getinfo(name)
                if name == "AndroidManifest.xml":
                    self.assertEqual(archive.read(entry), set_manifest_version_code(payload, NEW))
                else:
                    self.assertEqual(archive.read(entry), payload)
                self.assertEqual((entry.compress_type, entry.date_time, entry.external_attr), (method, date, attributes))
        self.assertEqual(self.apk.stat().st_mode & 0o777, 0o600)
        self.assertEqual(source.read_bytes(), before)

    def test_same_version_does_not_rewrite_archive(self):
        before = self.apk.read_bytes()
        self.assertEqual(set_apk_version_code(self.apk, OLD)["status"], "unchanged")
        self.assertEqual(self.apk.read_bytes(), before)

    def test_signed_apk_rejected_without_modification(self):
        with zipfile.ZipFile(self.apk, "a") as archive:
            archive.writestr("META-INF/CERT.RSA", b"signature fixture")
        before = self.apk.read_bytes()
        with self.assertRaisesRegex(ValidationError, "unsigned"):
            set_apk_version_code(self.apk, NEW)
        self.assertEqual(self.apk.read_bytes(), before)

    def test_atomic_failure_keeps_original_and_cleans_temporary(self):
        before = self.apk.read_bytes()
        with mock.patch("version_code.os.replace", side_effect=OSError("fixture failure")), \
             self.assertRaises(ValidationError):
            set_apk_version_code(self.apk, NEW)
        self.assertEqual(self.apk.read_bytes(), before)
        self.assertEqual(list(self.directory.iterdir()), [self.apk])


if __name__ == "__main__":
    unittest.main()
