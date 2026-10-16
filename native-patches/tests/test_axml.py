"""Binary-framing and atomic-repack regressions; no Instagram binary is stored."""
from pathlib import Path
import struct
import sys
import tempfile
import unittest
from unittest import mock
import zipfile

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
from axml import (END_ELEMENT, END_NAMESPACE, START_ELEMENT, START_NAMESPACE,
                  normalize_apk_manifest, normalize_namespace_order)
from validate_apk import ValidationError


def chunk(kind, extension=b"", *, line=1):
    return struct.pack("<HHIII", kind, 16, 16 + len(extension), line, 0xffffffff) + extension


def namespace(kind, prefix, uri, *, line=1):
    return chunk(kind, struct.pack("<II", prefix, uri), line=line)


def element(kind, name=9):
    extension = struct.pack("<II", 0xffffffff, name)
    if kind == START_ELEMENT:
        extension += struct.pack("<HHHHHH", 20, 20, 0, 0, 0, 0)
    return chunk(kind, extension)


def document(*chunks):
    payload = b"".join(chunks)
    return struct.pack("<HHI", 3, 8, 8 + len(payload)) + payload


A = (1, 2)
B = (3, 4)
C = (5, 6)


def manifest(*, reversed_ends=False):
    pairs = (A, B) if reversed_ends else (B, A)
    return document(namespace(START_NAMESPACE, *A), namespace(START_NAMESPACE, *B),
                    element(START_ELEMENT), element(END_ELEMENT),
                    *(namespace(END_NAMESPACE, *pair) for pair in pairs))


class NamespaceTests(unittest.TestCase):
    def test_valid_input_is_identical(self):
        original = manifest()
        normalized, count = normalize_namespace_order(original)
        self.assertIs(normalized, original)
        self.assertEqual(count, 0)

    def test_only_two_adjacent_end_chunks_move(self):
        original = manifest(reversed_ends=True)
        normalized, count = normalize_namespace_order(original)
        self.assertEqual(count, 2)
        self.assertEqual(normalized, manifest())
        self.assertEqual(normalized[:-48], original[:-48])
        self.assertEqual(normalize_namespace_order(normalized), (normalized, 0))

    def test_partial_three_namespace_permutation_counts_only_changed_positions(self):
        original = document(*(namespace(START_NAMESPACE, *p) for p in (A, B, C)),
                            element(START_ELEMENT), element(END_ELEMENT),
                            *(namespace(END_NAMESPACE, *p) for p in (A, B, C)))
        normalized, count = normalize_namespace_order(original)
        self.assertEqual(count, 2)  # The middle B chunk stays in place.
        self.assertEqual(normalized[-72:], b"".join(namespace(END_NAMESPACE, *p) for p in (C, B, A)))

    def test_nested_scopes_normalized_without_crossing_elements(self):
        original = document(namespace(START_NAMESPACE, *A), element(START_ELEMENT),
                            namespace(START_NAMESPACE, *B), namespace(START_NAMESPACE, *C),
                            element(START_ELEMENT, 10), element(END_ELEMENT, 10),
                            namespace(END_NAMESPACE, *B), namespace(END_NAMESPACE, *C),
                            element(END_ELEMENT), namespace(END_NAMESPACE, *A))
        normalized, count = normalize_namespace_order(original)
        self.assertEqual(count, 2)
        self.assertEqual(normalized[:-96], original[:-96])
        self.assertEqual(normalized[-48:], original[-48:])
        self.assertEqual(normalize_namespace_order(normalized)[1], 0)

    def test_default_prefix_and_duplicate_pairs_preserved(self):
        default = (0xffffffff, 2)
        original = document(namespace(START_NAMESPACE, *default), namespace(START_NAMESPACE, *default),
                            element(START_ELEMENT), element(END_ELEMENT),
                            namespace(END_NAMESPACE, *default), namespace(END_NAMESPACE, *default))
        self.assertEqual(normalize_namespace_order(original), (original, 0))

    def test_opaque_bytes_in_moved_chunks_preserved(self):
        end_a = namespace(END_NAMESPACE, *A, line=66)
        end_b = namespace(END_NAMESPACE, *B, line=77)
        original = manifest(reversed_ends=True)[:-48] + end_a + end_b
        normalized, count = normalize_namespace_order(original)
        self.assertEqual(normalized[-48:], end_b + end_a)
        self.assertEqual(count, 2)

    def test_namespace_cannot_move_across_element_event(self):
        original = document(namespace(START_NAMESPACE, *A), namespace(START_NAMESPACE, *B),
                            element(START_ELEMENT), element(END_ELEMENT),
                            namespace(END_NAMESPACE, *A), element(START_ELEMENT, 10),
                            element(END_ELEMENT, 10), namespace(END_NAMESPACE, *B))
        with self.assertRaises(ValidationError):
            normalize_namespace_order(original)

    def test_namespace_cannot_close_inside_its_element_scope(self):
        original = document(namespace(START_NAMESPACE, *A), element(START_ELEMENT),
                            namespace(END_NAMESPACE, *A), element(END_ELEMENT))
        with self.assertRaises(ValidationError):
            normalize_namespace_order(original)

    def test_undeclared_mismatched_or_unclosed_namespace_rejected(self):
        root = (element(START_ELEMENT), element(END_ELEMENT))
        cases = [document(*root, namespace(END_NAMESPACE, *A)),
                 document(namespace(START_NAMESPACE, *A), *root, namespace(END_NAMESPACE, *B)),
                 document(namespace(START_NAMESPACE, *A), *root, namespace(END_NAMESPACE, A[0], B[1])),
                 document(namespace(START_NAMESPACE, *A), *root, namespace(END_NAMESPACE, B[0], A[1])),
                 document(namespace(START_NAMESPACE, *A), *root)]
        for data in cases:
            with self.subTest(data=data), self.assertRaises(ValidationError):
                normalize_namespace_order(data)

    def test_invalid_container_headers_rejected(self):
        valid = manifest()
        cases = [b"", b"\x00" * 7, struct.pack("<HHI", 3, 8, 8),
                 struct.pack("<HHI", 4, 8, len(valid)) + valid[8:],
                 struct.pack("<HHI", 3, 12, len(valid)) + valid[8:],
                 struct.pack("<HHI", 3, 8, len(valid) + 4) + valid[8:]]
        for data in cases:
            with self.subTest(data=data), self.assertRaises(ValidationError):
                normalize_namespace_order(data)

    def test_truncated_and_invalid_chunks_rejected(self):
        chunks = [b"\x00" * 4, struct.pack("<HHI", 1, 8, 0),
                  struct.pack("<HHI", 1, 12, 8), struct.pack("<HHI", 1, 8, 12),
                  struct.pack("<HHI", 1, 8, 9) + b"\x00" * 4,
                  chunk(START_NAMESPACE, struct.pack("<I", 1)),
                  chunk(START_ELEMENT, struct.pack("<II", 0xffffffff, 9))]
        for data in chunks:
            with self.subTest(data=data), self.assertRaises(ValidationError):
                normalize_namespace_order(document(data))

    def test_element_mismatches_and_multiple_roots_rejected(self):
        cases = [document(element(END_ELEMENT)), document(element(START_ELEMENT)),
                 document(element(START_ELEMENT), element(END_ELEMENT, 10)),
                 document(element(START_ELEMENT), element(END_ELEMENT),
                          element(START_ELEMENT, 10), element(END_ELEMENT, 10))]
        for data in cases:
            with self.subTest(data=data), self.assertRaises(ValidationError):
                normalize_namespace_order(data)

    def test_limits_reject_before_unbounded_processing(self):
        with mock.patch("axml.MAX_CHUNKS", 2), self.assertRaises(ValidationError):
            normalize_namespace_order(manifest())
        with mock.patch("axml.MAX_DEPTH", 1), self.assertRaises(ValidationError):
            normalize_namespace_order(manifest())
        with mock.patch("axml.MAX_MANIFEST_BYTES", 8), self.assertRaises(ValidationError):
            normalize_namespace_order(manifest())


class ApkRepackTests(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.directory = Path(temporary.name)
        self.apk = self.directory / "unsigned.apk"

    def create_apk(self, data=None, *, signed=False):
        with zipfile.ZipFile(self.apk, "w") as archive:
            archive.comment = b"preserved archive comment"
            archive.writestr("AndroidManifest.xml", manifest(reversed_ends=True) if data is None else data,
                             compress_type=zipfile.ZIP_DEFLATED)
            archive.writestr("classes.dex", b"fixture code", compress_type=zipfile.ZIP_STORED)
            archive.writestr("lib/arm64-v8a/libfixture.so", b"fixture library", compress_type=zipfile.ZIP_DEFLATED)
            archive.writestr("assets/test.txt", b"private fixture asset", compress_type=zipfile.ZIP_STORED)
            if signed:
                archive.writestr("META-INF/CERT.SF", b"signature fixture")
        self.apk.chmod(0o600)

    def test_repack_preserves_all_other_payloads_metadata_and_permissions(self):
        self.create_apk()
        with zipfile.ZipFile(self.apk) as archive:
            original = {i.filename: (archive.read(i), i.compress_type, i.date_time, i.external_attr)
                        for i in archive.infolist()}
        report = normalize_apk_manifest(self.apk)
        self.assertEqual(report["status"], "normalized")
        self.assertEqual(report["namespace_end_chunks_reordered"], 2)
        self.assertEqual(report["entries_preserved"], 4)
        self.assertNotEqual(report["manifest_sha256_before"], report["manifest_sha256_after"])
        with zipfile.ZipFile(self.apk) as archive:
            self.assertEqual(archive.namelist(), list(original))
            self.assertEqual(archive.comment, b"preserved archive comment")
            for entry in archive.infolist():
                payload, method, date, attributes = original[entry.filename]
                expected = manifest() if entry.filename == "AndroidManifest.xml" else payload
                self.assertEqual(archive.read(entry), expected)
                self.assertEqual((entry.compress_type, entry.date_time, entry.external_attr),
                                 (method, date, attributes))
        self.assertEqual(self.apk.stat().st_mode & 0o777, 0o600)
        self.assertEqual(list(self.directory.iterdir()), [self.apk])

    def test_valid_archive_not_rewritten(self):
        self.create_apk(manifest())
        before = self.apk.read_bytes()
        self.assertEqual(normalize_apk_manifest(self.apk)["status"], "unchanged")
        self.assertEqual(self.apk.read_bytes(), before)

    def test_invalid_manifest_leaves_original_intact(self):
        self.create_apk(b"bad manifest")
        before = self.apk.read_bytes()
        with self.assertRaises(ValidationError):
            normalize_apk_manifest(self.apk)
        self.assertEqual(self.apk.read_bytes(), before)
        self.assertEqual(list(self.directory.iterdir()), [self.apk])

    def test_v1_signed_input_is_rejected_without_mutation(self):
        self.create_apk(signed=True)
        before = self.apk.read_bytes()
        with self.assertRaisesRegex(ValidationError, "unsigned"):
            normalize_apk_manifest(self.apk)
        self.assertEqual(self.apk.read_bytes(), before)

    def test_apk_signature_block_input_is_rejected_without_mutation(self):
        self.create_apk()
        with zipfile.ZipFile(self.apk) as archive:
            central_directory = archive.start_dir
        original = self.apk.read_bytes()
        # Packaging fixture only: the standard APK-signing-block marker is
        # sufficient to reject repacking; this is not a valid cryptographic signature.
        block = struct.pack("<QQ", 24, 24) + b"APK Sig Block 42"
        modified = bytearray(original[:central_directory] + block + original[central_directory:])
        end = modified.rfind(b"PK\x05\x06")
        struct.pack_into("<I", modified, end + 16, central_directory + len(block))
        self.apk.write_bytes(modified)
        before = self.apk.read_bytes()
        with self.assertRaisesRegex(ValidationError, "unsigned"):
            normalize_apk_manifest(self.apk)
        self.assertEqual(self.apk.read_bytes(), before)

    def test_failure_before_atomic_replace_leaves_original_and_cleans_temporary(self):
        self.create_apk()
        before = self.apk.read_bytes()
        with mock.patch("axml.os.replace", side_effect=OSError("fixture failure")), \
             self.assertRaises(ValidationError):
            normalize_apk_manifest(self.apk)
        self.assertEqual(self.apk.read_bytes(), before)
        self.assertEqual(list(self.directory.iterdir()), [self.apk])

    def test_symlink_input_rejected(self):
        self.create_apk()
        link = self.directory / "link.apk"
        link.symlink_to(self.apk)
        with self.assertRaises(ValidationError):
            normalize_apk_manifest(link)


if __name__ == "__main__":
    unittest.main()
