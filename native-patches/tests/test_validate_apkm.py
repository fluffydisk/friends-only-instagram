"""Bounded synthetic APKM fixtures; Android-tool output is explicitly mocked."""

from __future__ import annotations

import hashlib
import contextlib
import io
from pathlib import Path
import stat
import sys
import tempfile
import unittest
from unittest import mock
import warnings
import zipfile

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
import validate_apkm as validator


# Synthetic only: this is not an Instagram certificate.
CERTIFICATE = "a" * 64
SIGNER = f"Signer #1 certificate SHA-256 digest: {CERTIFICATE}\n"


def apk_bytes(*, code=False, arm64=False, entries=()):
    stream = io.BytesIO()
    with warnings.catch_warnings():
        warnings.simplefilter("ignore", UserWarning)
        with zipfile.ZipFile(stream, "w") as archive:
            archive.writestr("AndroidManifest.xml", b"synthetic manifest fixture")
            if code:
                archive.writestr("classes.dex", b"synthetic code fixture")
            if arm64:
                archive.writestr("lib/arm64-v8a/libfixture.so", b"synthetic native fixture")
            for name, value in entries:
                archive.writestr(name, value)
    return stream.getvalue()


def metadata(*, split=None, feature=False, abi=False, version="451.0.0.20.80",
             code="385709029", package="com.instagram.android", dependencies=(),
             required_types="", split_types="", config_for=""):
    split_attr = f" split='{split}'" if split else ""
    badging = f"package: name='{package}' versionCode='{code}' versionName='{version}'{split_attr}\n"
    if abi:
        badging += "native-code: 'arm64-v8a'\n"
    xml = ("N: android=http://schemas.android.com/apk/res/android\n"
           "  E: manifest (line=1)\n"
           f'    A: package="{package}"\n')
    if split:
        xml += f'    A: split="{split}"\n'
    if feature:
        xml += "    A: android:isFeatureSplit(0x0101055b)=(type 0x12)0xffffffff\n"
    if required_types:
        xml += f'    A: android:requiredSplitTypes(0x0101064e)="{required_types}"\n'
    if split_types:
        xml += f'    A: android:splitTypes(0x0101064f)="{split_types}"\n'
    if config_for:
        xml += f'    A: android:configForSplit(0x0101059d)="{config_for}"\n'
    for dep in dependencies:
        xml += f'    E: uses-split (line=2)\n      A: android:name(0x01010003)="{dep}"\n'
    return badging, xml


class APKMTests(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.directory = Path(temporary.name)
        self.apkm = self.directory / "synthetic-input.apkm"
        self.output = self.directory / "extracted"
        self.parts = {
            "base.apk": apk_bytes(code=True, arm64=True),
            "split_config.xxhdpi.apk": apk_bytes(entries=[("resources.arsc", b"resources")]),
        }
        self.metadata = {
            "base.apk": metadata(abi=True, required_types="base__density"),
            "split_config.xxhdpi.apk": metadata(split="config.xxhdpi", version="", split_types="base__density"),
        }
        self.signers = {}
        self.make_bundle()

    def make_bundle(self, extra=(), *, compression=zipfile.ZIP_STORED):
        with warnings.catch_warnings():
            warnings.simplefilter("ignore", UserWarning)
            with zipfile.ZipFile(self.apkm, "w", compression=compression) as archive:
                archive.writestr("info.json", b'{"package": "untrusted advisory metadata"}')
                archive.writestr("META-INF/OUTER.SF", b"untrusted outer signature")
                for name, contents in self.parts.items():
                    archive.writestr(name, contents)
                for name, contents in extra:
                    archive.writestr(name, contents)

    def digest(self):
        return hashlib.sha256(self.apkm.read_bytes()).hexdigest()

    def tool(self, arguments, label):
        part = Path(arguments[-1] if "--print-certs" in arguments else arguments[3]).name
        if "--print-certs" in arguments:
            return self.signers.get(part, SIGNER)
        if arguments[2] == "badging":
            return self.metadata[part][0]
        return self.metadata[part][1]

    def validate(self, **overrides):
        options = dict(apkm=self.apkm, sha256=self.digest(), signer_sha256=CERTIFICATE,
                       extract_dir=self.output, aapt="/sdk/aapt", apksigner="/sdk/apksigner")
        options.update(overrides)
        with mock.patch.object(validator, "run_command", side_effect=self.tool) as tool:
            return validator.validate_apkm(**options), tool

    def test_accepts_verified_resource_split_with_inherited_base_version(self):
        report, tool = self.validate()
        self.assertEqual(report["status"], "accepted")
        self.assertEqual(report["base_file"], "base.apk")
        self.assertEqual(report["manifest"]["version_code"], "385709029")
        self.assertEqual(report["split_count"], 2)
        self.assertFalse(report["outer_metadata_trusted"])
        self.assertFalse(report["signature"]["official_distribution_independently_verified"])
        self.assertEqual(sorted(path.name for path in self.output.iterdir()), sorted(self.parts))
        resource = next(part for part in report["splits"] if part["file"] != "base.apk")
        self.assertTrue(resource["manifest"]["version_name_inherited"])
        self.assertFalse(resource["archive"]["has_classes_dex"])
        self.assertEqual(tool.call_count, 6)
        self.assertNotIn(str(self.directory), str(report))
        for part in self.parts:
            self.assertEqual((self.output / part).read_bytes(), self.parts[part])

    def test_supplied_hash_must_match_before_extraction_or_android_tools(self):
        with mock.patch.object(validator, "run_command") as tool:
            with self.assertRaisesRegex(validator.ValidationError, "SHA-256 does not match"):
                validator.validate_apkm(self.apkm, "b" * 64, CERTIFICATE, self.output)
        tool.assert_not_called()
        self.assertFalse(self.output.exists())

    def test_rejects_malformed_expected_digests(self):
        for key in ("sha256", "signer_sha256"):
            with self.subTest(key=key), self.assertRaises(validator.ValidationError):
                self.validate(**{key: "not-a-digest"})

    def test_existing_output_is_never_deleted_or_overwritten(self):
        self.output.mkdir()
        sentinel = self.output / "keep"
        sentinel.write_bytes(b"original")
        with self.assertRaisesRegex(validator.ValidationError, "must not already exist"):
            self.validate()
        self.assertEqual(sentinel.read_bytes(), b"original")

    def test_rejects_outer_traversal_and_ambiguous_paths_before_extraction(self):
        for name in ("../outside.apk", "/absolute", "C:/file", "a\\file", "a//file", "a/../file"):
            with self.subTest(name=name):
                self.make_bundle([(name, b"fixture")])
                with self.assertRaisesRegex(validator.ValidationError, "unsafe ZIP"):
                    self.validate()
                self.assertFalse(self.output.exists())

    def test_rejects_nested_apk_entries(self):
        self.make_bundle([("nested/feature.apk", apk_bytes())])
        with self.assertRaisesRegex(validator.ValidationError, "bundle root"):
            self.validate()

    def test_rejects_duplicate_outer_paths(self):
        self.make_bundle([("base.apk", self.parts["base.apk"])])
        with self.assertRaisesRegex(validator.ValidationError, "duplicate ZIP"):
            self.validate()

    def test_rejects_symlink_sidecar(self):
        entry = zipfile.ZipInfo("link")
        entry.create_system = 3
        entry.external_attr = (stat.S_IFLNK | 0o777) << 16
        self.make_bundle([(entry, b"target")])
        with self.assertRaisesRegex(validator.ValidationError, "non-regular"):
            self.validate()

    def test_rejects_compression_bomb_before_extraction(self):
        self.make_bundle([("padding", b"x" * 200_000)], compression=zipfile.ZIP_DEFLATED)
        with self.assertRaisesRegex(validator.ValidationError, "compression ratio"):
            self.validate()
        self.assertFalse(self.output.exists())

    def test_rejects_outer_entry_and_apk_count_limits(self):
        with mock.patch.object(validator, "MAX_OUTER_ENTRIES", 3):
            with self.assertRaisesRegex(validator.ValidationError, "entry count"):
                self.validate()
        with mock.patch.object(validator, "MAX_APKS", 1):
            with self.assertRaisesRegex(validator.ValidationError, "APK count"):
                self.validate()

    def test_rejects_outer_and_expanded_size_limits(self):
        for setting in ("MAX_BUNDLE_BYTES", "MAX_SPLIT_BYTES", "MAX_OUTER_EXPANDED_BYTES"):
            with self.subTest(setting=setting), mock.patch.object(validator, setting, 1):
                with self.assertRaises(validator.ValidationError):
                    self.validate()

    def test_wrong_package_version_code_or_explicit_version_name_rejected(self):
        for changes in (dict(package="com.example.fake"), dict(code="385709030"),
                        dict(version="452.0.0.1.0")):
            with self.subTest(changes=changes):
                self.metadata["split_config.xxhdpi.apk"] = metadata(split="config.xxhdpi", **changes)
                with self.assertRaises(validator.ValidationError):
                    self.validate()
                self.assertFalse(self.output.exists())

    def test_every_split_signer_must_match_expected_certificate(self):
        self.signers["split_config.xxhdpi.apk"] = SIGNER.replace(CERTIFICATE, "b" * 64)
        with self.assertRaisesRegex(validator.ValidationError, "separately trusted"):
            self.validate()
        self.assertFalse(self.output.exists())

    def test_android_verifier_failure_is_not_ignored(self):
        with mock.patch.object(validator, "run_command", side_effect=validator.ValidationError("verification rejected")):
            with self.assertRaisesRegex(validator.ValidationError, "verification rejected"):
                validator.validate_apkm(self.apkm, self.digest(), CERTIFICATE, self.output)
        self.assertFalse(self.output.exists())

    def test_rejects_missing_and_duplicate_base_identity(self):
        for base_meta in (metadata(split="unexpected", feature=True, abi=True), metadata(abi=True)):
            self.metadata["base.apk"] = base_meta
            if "split='" not in base_meta[0]:
                self.metadata["split_config.xxhdpi.apk"] = metadata()
            with self.assertRaisesRegex(validator.ValidationError, "exactly one base"):
                self.validate()
            self.assertFalse(self.output.exists())

    def test_rejects_base_without_code(self):
        self.parts["base.apk"] = apk_bytes(arm64=True)
        self.make_bundle()
        with self.assertRaisesRegex(validator.ValidationError, "application code"):
            self.validate()

    def test_arm64_can_be_in_feature_but_is_required_somewhere(self):
        self.parts["base.apk"] = apk_bytes(code=True)
        self.metadata["base.apk"] = metadata(required_types="base__density")
        self.make_bundle()
        with self.assertRaisesRegex(validator.ValidationError, "arm64-v8a"):
            self.validate()
        self.parts["feature.apk"] = apk_bytes(arm64=True)
        self.metadata["feature.apk"] = metadata(split="feature", feature=True, abi=True)
        self.make_bundle()
        report, _ = self.validate()
        self.assertEqual(report["manifest"]["native_abis"], ["arm64-v8a"])

    def test_mismatching_abi_metadata_rejected(self):
        self.metadata["base.apk"] = metadata(abi=False)
        with self.assertRaisesRegex(validator.ValidationError, "ABI metadata"):
            self.validate()

    def test_rejects_duplicate_split_identities(self):
        self.parts["second.apk"] = apk_bytes()
        self.metadata["second.apk"] = metadata(split="config.xxhdpi")
        self.make_bundle()
        with self.assertRaisesRegex(validator.ValidationError, "duplicate split identities"):
            self.validate()

    def test_rejects_unresolved_or_nonfeature_dependency(self):
        for target in ("missing", "config.xxhdpi"):
            self.metadata["base.apk"] = metadata(abi=True, dependencies=(target,))
            with self.subTest(target=target), self.assertRaisesRegex(validator.ValidationError, "feature dependency"):
                self.validate()

    def test_accepts_resolved_feature_dependency(self):
        self.parts["feature.apk"] = apk_bytes()
        self.metadata["feature.apk"] = metadata(split="feature", feature=True)
        self.metadata["base.apk"] = metadata(abi=True, dependencies=("feature",))
        self.make_bundle()
        report, _ = self.validate()
        self.assertEqual(report["split_count"], 3)

    def test_rejects_missing_required_split_type(self):
        self.metadata["base.apk"] = metadata(abi=True, required_types="base__abi")
        with self.assertRaisesRegex(validator.ValidationError, "missing split types"):
            self.validate()

    def test_rejects_config_target_missing_feature(self):
        self.metadata["split_config.xxhdpi.apk"] = metadata(
            split="config.xxhdpi", config_for="absent", split_types="base__density")
        with self.assertRaisesRegex(validator.ValidationError, "feature dependency"):
            self.validate()

    def test_rejects_cyclic_feature_dependencies(self):
        self.parts["first.apk"] = apk_bytes()
        self.parts["second.apk"] = apk_bytes()
        self.metadata["first.apk"] = metadata(split="first", feature=True, dependencies=("second",))
        self.metadata["second.apk"] = metadata(split="second", feature=True, dependencies=("first",))
        self.make_bundle()
        with self.assertRaisesRegex(validator.ValidationError, "cyclic feature"):
            self.validate()

    def test_rejects_inner_traversal_and_duplicate_manifest(self):
        for entries in (([('../escape', b'x')]), ([('AndroidManifest.xml', b'duplicate')])):
            self.parts["split_config.xxhdpi.apk"] = apk_bytes(entries=entries)
            self.make_bundle()
            with self.subTest(entries=entries), self.assertRaises(validator.ValidationError):
                self.validate()
            self.assertFalse(self.output.exists())

    def test_rejects_nested_bundle_inside_split(self):
        self.parts["split_config.xxhdpi.apk"] = apk_bytes(entries=[("embedded.apkm", b"fixture")])
        self.make_bundle()
        with self.assertRaisesRegex(validator.ValidationError, "Nested APK"):
            self.validate()

    def test_rejects_corrupted_outer_crc(self):
        encoded = self.apkm.read_bytes()
        self.apkm.write_bytes(encoded.replace(b"untrusted outer signature", b"corrupted outer signature", 1))
        with self.assertRaisesRegex(validator.ValidationError, "entry integrity"):
            self.validate()
        self.assertFalse(self.output.exists())

    def test_rejects_nul_outer_filename_before_python_truncation(self):
        self.make_bundle([("badXname", b"fixture")])
        self.apkm.write_bytes(self.apkm.read_bytes().replace(b"badXname", b"bad\x00name"))
        with self.assertRaisesRegex(validator.ValidationError, "unsafe ZIP"):
            self.validate()

    def test_rejects_encrypted_outer_entry_before_decryption(self):
        encoded = bytearray(self.apkm.read_bytes())
        with zipfile.ZipFile(self.apkm) as archive:
            central = archive.start_dir
        encoded[central + 8] |= 1
        self.apkm.write_bytes(encoded)
        with self.assertRaisesRegex(validator.ValidationError, "Encrypted ZIP"):
            self.validate()
        self.assertFalse(self.output.exists())

    def test_rejects_corrupted_inner_crc_and_cleans_extracted_files(self):
        self.parts["base.apk"] = self.parts["base.apk"].replace(
            b"synthetic code fixture", b"corrupted code fixture", 1)
        self.make_bundle()
        with self.assertRaisesRegex(validator.ValidationError, "entry integrity"):
            self.validate()
        self.assertFalse(self.output.exists())

    def test_rejects_truncated_inner_zip(self):
        self.parts["base.apk"] = self.parts["base.apk"][:-40]
        self.make_bundle()
        with self.assertRaisesRegex(validator.ValidationError, "entry integrity"):
            self.validate()

    def test_rejects_changed_split_during_tool_execution(self):
        def changing_tool(arguments, label):
            result = self.tool(arguments, label)
            if "--print-certs" in arguments:
                with Path(arguments[-1]).open("ab") as output:
                    output.write(b"unexpected mutation")
            return result
        with mock.patch.object(validator, "run_command", side_effect=changing_tool):
            with self.assertRaisesRegex(validator.ValidationError, "changed during validation"):
                validator.validate_apkm(self.apkm, self.digest(), CERTIFICATE, self.output)
        self.assertFalse(self.output.exists())

    def test_rejects_changed_outer_archive_during_validation(self):
        changed = False

        def changing_tool(arguments, label):
            nonlocal changed
            if not changed:
                with self.apkm.open("ab") as output:
                    output.write(b"unexpected mutation")
                changed = True
            return self.tool(arguments, label)
        with mock.patch.object(validator, "run_command", side_effect=changing_tool):
            with self.assertRaisesRegex(validator.ValidationError, "APKM changed during validation"):
                validator.validate_apkm(self.apkm, self.digest(), CERTIFICATE, self.output)
        self.assertFalse(self.output.exists())

    def test_unreadable_split_manifest_is_rejected(self):
        badging, _ = self.metadata["base.apk"]
        self.metadata["base.apk"] = (badging, "Not an Android manifest")
        with self.assertRaisesRegex(validator.ValidationError, "readable split Android manifest"):
            self.validate()

    def test_differing_split_identity_in_xml_and_badging_is_rejected(self):
        badging, xmltree = self.metadata["split_config.xxhdpi.apk"]
        self.metadata["split_config.xxhdpi.apk"] = (
            badging, xmltree.replace('split="config.xxhdpi"', 'split="different"'))
        with self.assertRaisesRegex(validator.ValidationError, "split identity differs"):
            self.validate()

    def test_cli_report_does_not_overwrite_input(self):
        with contextlib.redirect_stderr(io.StringIO()), self.assertRaises(SystemExit):
            validator.main(["--apkm", str(self.apkm), "--sha256", self.digest(),
                            "--signer-sha256", CERTIFICATE, "--extract-dir", str(self.output),
                            "--report", str(self.apkm)])


if __name__ == "__main__":
    unittest.main()
