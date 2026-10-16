"""Synthetic ZIP fixtures + mocked Android-tool metadata; no Instagram APKs."""

from __future__ import annotations

import contextlib
import hashlib
import importlib.util
import io
import json
from pathlib import Path
import stat
import subprocess
import tempfile
import types
import unittest
from unittest import mock
import warnings
import zipfile


SPEC = importlib.util.spec_from_file_location(
    "validate_apk_under_test", Path(__file__).resolve().parents[1] / "scripts" / "validate_apk.py"
)
validator = importlib.util.module_from_spec(SPEC)
assert SPEC and SPEC.loader
SPEC.loader.exec_module(validator)

# Deliberately synthetic certificate digest. It is NOT a Meta certificate.
TEST_CERTIFICATE = "a" * 64
BADGING = (
    "package: name='com.instagram.android' versionCode='451000020' "
    "versionName='451.0.0.20.80' platformBuildVersionCode='35'\n"
    "sdkVersion:'28'\n"
    "native-code: 'arm64-v8a'\n"
)
XMLTREE = "N: android=http://schemas.android.com/apk/res/android\n  E: manifest (line=1)\n"
SIGNER = f"Signer #1 certificate SHA-256 digest: {TEST_CERTIFICATE}\n"


class ValidatorTests(unittest.TestCase):
    def test_forced_zip64_local_headers_rejected(self):
        with zipfile.ZipFile(self.apk, "w") as archive:
            for name, data in [('AndroidManifest.xml', b'manifest'), ('classes.dex', b'dex'),
                               ('lib/arm64-v8a/a.so', b'library')]:
                with archive.open(name, 'w', force_zip64=True) as output:
                    output.write(data)
        with self.assertRaisesRegex(validator.ValidationError, 'ZIP64'):
            validator.inspect_archive(self.apk)
    def test_verified_rotated_signer_uses_current_platform_certificate(self):
        output = (f"Signer (minSdkVersion=24, maxSdkVersion=32) certificate SHA-256 digest: {'b' * 64}\n"
                  f"Signer (minSdkVersion=33, maxSdkVersion=2147483647) certificate SHA-256 digest: {TEST_CERTIFICATE}\n"
                  f"Source Stamp Signer certificate SHA-256 digest: {'c' * 64}\n")
        report = validator.inspect_signer(output, TEST_CERTIFICATE)
        self.assertTrue(report['verified'])
        self.assertEqual(len(report['platform_signers']), 2)
        with self.assertRaises(validator.ValidationError):
            validator.inspect_signer(output, 'b' * 64)

    def test_overlapping_rotated_signers_rejected(self):
        output = (f"Signer (minSdkVersion=24, maxSdkVersion=34) certificate SHA-256 digest: {TEST_CERTIFICATE}\n"
                  f"Signer (minSdkVersion=33, maxSdkVersion=2147483647) certificate SHA-256 digest: {TEST_CERTIFICATE}\n")
        with self.assertRaises(validator.ValidationError):
            validator.inspect_signer(output, TEST_CERTIFICATE)

    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.directory = Path(self.temporary.name)
        self.apk = self.directory / "synthetic-test-fixture.apk"
        self.make_archive()

    def make_archive(self, extra=None, *, arm64=True, compression=zipfile.ZIP_STORED):
        with warnings.catch_warnings():
            warnings.simplefilter("ignore", UserWarning)
            with zipfile.ZipFile(self.apk, "w", compression=compression) as archive:
                archive.writestr("AndroidManifest.xml", b"manifest fixture")
                archive.writestr("classes.dex", b"dex fixture")
                if arm64:
                    archive.writestr("lib/arm64-v8a/libfixture.so", b"native fixture")
                for name, data in extra or []:
                    archive.writestr(name, data)

    def digest(self):
        return hashlib.sha256(self.apk.read_bytes()).hexdigest()

    def validate(self, *, badging=BADGING, xmltree=XMLTREE, signer=SIGNER, **overrides):
        options = {
            "apk": self.apk,
            "sha256": self.digest(),
            "signer_sha256": TEST_CERTIFICATE,
            "aapt": "/trusted-sdk/aapt",
            "apksigner": "/trusted-sdk/apksigner",
        }
        options.update(overrides)
        with mock.patch.object(validator, "run_command", side_effect=[badging, xmltree, signer]) as tool:
            report = validator.validate_apk(**options)
        return report, tool

    def test_accepts_only_after_hash_archive_manifest_and_independent_signer_match(self):
        original = self.apk.read_bytes()
        report, tool = self.validate()
        self.assertEqual(report["status"], "accepted")
        self.assertEqual(report["apk"]["sha256"], self.digest())
        self.assertEqual(report["manifest"]["package"], "com.instagram.android")
        self.assertEqual(report["manifest"]["version_name"], "451.0.0.20.80")
        self.assertEqual(report["manifest"]["native_abis"], ["arm64-v8a"])
        self.assertTrue(report["signature"]["verified"])
        self.assertEqual(report["signature"]["trust_source"], "user_supplied_expected_digest")
        self.assertFalse(report["signature"]["official_distribution_independently_verified"])
        self.assertEqual(self.apk.read_bytes(), original)
        self.assertEqual(tool.call_args_list[0].args[0], ["/trusted-sdk/aapt", "dump", "badging", str(self.apk)])
        self.assertEqual(tool.call_args_list[2].args[0], ["/trusted-sdk/apksigner", "verify", "--print-certs", str(self.apk)])
        self.assertNotIn(str(self.directory), json.dumps(report))

    def test_hash_mismatch_rejects_before_android_tools(self):
        with mock.patch.object(validator, "run_command") as tool:
            with self.assertRaisesRegex(validator.ValidationError, "SHA-256 does not match"):
                validator.validate_apk(self.apk, "b" * 64, TEST_CERTIFICATE)
        tool.assert_not_called()

    def test_missing_or_malformed_expected_digest_is_not_inferred_from_input(self):
        for value in ("", "abc", "g" * 64, "a" * 63, "a" * 65):
            with self.subTest(value=value):
                with self.assertRaises(validator.ValidationError):
                    self.validate(sha256=value)
                with self.assertRaises(validator.ValidationError):
                    self.validate(signer_sha256=value)

    def test_colon_separated_expected_certificate_digest_is_supported(self):
        colon_digest = ":".join(["AA"] * 32)
        report, _ = self.validate(signer_sha256=colon_digest)
        self.assertEqual(report["signature"]["certificate_sha256"], TEST_CERTIFICATE)

    def test_wrong_package_is_rejected(self):
        with self.assertRaisesRegex(validator.ValidationError, "package is not"):
            self.validate(badging=BADGING.replace("com.instagram.android", "com.example.counterfeit"))

    def test_unknown_version_is_not_automatically_accepted(self):
        with self.assertRaisesRegex(validator.ValidationError, "version allowlist"):
            self.validate(badging=BADGING.replace("451.0.0.20.80", "452.0.0.1.0"))

    def test_duplicate_package_metadata_is_rejected(self):
        with self.assertRaisesRegex(validator.ValidationError, "unique APK package"):
            self.validate(badging=BADGING + BADGING)

    def test_split_apk_and_split_required_base_are_rejected(self):
        cases = [
            (BADGING.replace("platformBuildVersionCode=", "split='config.arm64_v8a' platformBuildVersionCode="), XMLTREE),
            (BADGING + "uses-split: name='config.arm64_v8a'\n", XMLTREE),
            (BADGING, XMLTREE + "    E: uses-split (line=2)\n"),
            (BADGING, XMLTREE + "    A: android:isSplitRequired(0x01010591)=(type 0x12)0xffffffff\n"),
            (BADGING, XMLTREE + '    A: android:requiredSplitTypes(0x0101064e)="base__abi"\n'),
            (BADGING, XMLTREE + '    A: split="config.en"\n'),
        ]
        for badging, xmltree in cases:
            with self.subTest(xmltree=xmltree, badging=badging):
                with self.assertRaisesRegex(validator.ValidationError, "[Ss]plit"):
                    self.validate(badging=badging, xmltree=xmltree)

    def test_false_is_split_required_is_accepted(self):
        self.validate(xmltree=XMLTREE + "    A: android:isSplitRequired(0x01010591)=(type 0x12)0x0\n")

    def test_unreadable_manifest_metadata_is_rejected(self):
        with self.assertRaisesRegex(validator.ValidationError, "readable Android manifest"):
            self.validate(xmltree="Not an XML tree")

    def test_arm64_must_exist_in_archive_and_metadata(self):
        with self.assertRaisesRegex(validator.ValidationError, "arm64-v8a"):
            self.validate(badging=BADGING.replace("'arm64-v8a'", "'x86_64'"))
        self.make_archive(arm64=False)
        with self.assertRaisesRegex(validator.ValidationError, "arm64-v8a native libraries"):
            self.validate()

    def test_metadata_and_actual_native_abis_must_match(self):
        with self.assertRaisesRegex(validator.ValidationError, "ABI metadata does not match"):
            self.validate(badging=BADGING.replace("'arm64-v8a'", "'arm64-v8a' 'x86'"))

    def test_self_reported_certificate_cannot_replace_trusted_expectation(self):
        counterfeit_output = SIGNER.replace(TEST_CERTIFICATE, "b" * 64)
        with self.assertRaisesRegex(validator.ValidationError, "separately trusted"):
            self.validate(signer=counterfeit_output)

    def test_missing_and_multiple_signers_fail_closed(self):
        for signer in ("Verifies\n", SIGNER + SIGNER.replace("#1", "#2")):
            with self.subTest(signer=signer):
                with self.assertRaisesRegex(validator.ValidationError, "Exactly one"):
                    self.validate(signer=signer)

    def test_signature_tool_failure_is_not_ignored(self):
        with mock.patch.object(validator, "run_command", side_effect=[
            BADGING, XMLTREE, validator.ValidationError("apksigner verification rejected the APK.")
        ]):
            with self.assertRaisesRegex(validator.ValidationError, "apksigner verification rejected"):
                validator.validate_apk(self.apk, self.digest(), TEST_CERTIFICATE)

    def test_apk_bundle_and_nested_apk_are_rejected(self):
        with self.assertRaisesRegex(validator.ValidationError, "one .apk"):
            self.validate(apk=self.apk.with_suffix(".apks"))
        self.make_archive([("base.apk", b"nested APK fixture")])
        with self.assertRaisesRegex(validator.ValidationError, "Nested APK"):
            self.validate()

    def test_traversal_and_ambiguous_entry_paths_are_rejected(self):
        for name in ("../escape", "/absolute", "C:/drive", "back\\slash", "a/../escape", "a//b", "./name", "control\x01name"):
            with self.subTest(name=name):
                self.make_archive([(name, b"fixture")])
                with self.assertRaisesRegex(validator.ValidationError, "unsafe ZIP entry path"):
                    self.validate()

    def test_nul_entry_name_is_not_silently_truncated(self):
        self.make_archive([("evilXname", b"fixture")])
        self.apk.write_bytes(self.apk.read_bytes().replace(b"evilXname", b"evil\x00name"))
        with self.assertRaisesRegex(validator.ValidationError, "unsafe ZIP entry path"):
            self.validate()

    def test_duplicate_entry_names_are_rejected(self):
        self.make_archive([("classes.dex", b"second copy")])
        with self.assertRaisesRegex(validator.ValidationError, "duplicate ZIP entry"):
            self.validate()

    def test_symlinks_are_rejected(self):
        link = zipfile.ZipInfo("asset-link")
        link.create_system = 3
        link.external_attr = (stat.S_IFLNK | 0o777) << 16
        self.make_archive([(link, b"../target")])
        with self.assertRaisesRegex(validator.ValidationError, "non-regular ZIP entry"):
            self.validate()

    def test_compression_bomb_rejected_before_decompression_or_tools(self):
        self.make_archive([("bomb", b"\0" * (1024 * 1024))], compression=zipfile.ZIP_DEFLATED)
        with mock.patch.object(validator, "run_command") as tool:
            with self.assertRaisesRegex(validator.ValidationError, "compression ratio"):
                validator.validate_apk(self.apk, self.digest(), TEST_CERTIFICATE)
        tool.assert_not_called()

    def test_file_entry_count_and_expanded_size_limits(self):
        for setting, value, message in (
            ("MAX_APK_BYTES", 1, "file size limits"),
            ("MAX_ENTRIES", 2, "entry count"),
            ("MAX_ENTRY_BYTES", 2, "uncompressed size limit"),
            ("MAX_EXPANDED_BYTES", 2, "total uncompressed"),
            ("MAX_MANIFEST_BYTES", 2, "manifest exceeds"),
        ):
            with self.subTest(setting=setting), mock.patch.object(validator, setting, value):
                with self.assertRaisesRegex(validator.ValidationError, message):
                    self.validate()

    def test_encrypted_entries_are_rejected(self):
        data = bytearray(self.apk.read_bytes())
        local = data.index(b"PK\x03\x04")
        central = data.index(b"PK\x01\x02")
        data[local + 6:local + 8] = b"\x01\x00"
        data[central + 8:central + 10] = b"\x01\x00"
        self.apk.write_bytes(data)
        with self.assertRaisesRegex(validator.ValidationError, "Encrypted ZIP"):
            self.validate()

    def test_unsupported_zip_compression_is_rejected(self):
        self.make_archive(compression=zipfile.ZIP_BZIP2)
        with self.assertRaisesRegex(validator.ValidationError, "compression method"):
            self.validate()

    def test_corrupt_zip_entry_crc_is_rejected_before_android_tools(self):
        self.apk.write_bytes(self.apk.read_bytes().replace(b"dex fixture", b"bad fixture"))
        with self.assertRaisesRegex(validator.ValidationError, "entry integrity is invalid"):
            self.validate()

    def test_input_replacement_during_validation_is_rejected(self):
        def reply(arguments, label):
            if "badging" in arguments:
                return BADGING
            if "xmltree" in arguments:
                return XMLTREE
            self.apk.write_bytes(self.apk.read_bytes() + b"changed")
            return SIGNER

        expected = self.digest()
        with mock.patch.object(validator, "run_command", side_effect=reply):
            with self.assertRaisesRegex(validator.ValidationError, "changed during validation"):
                validator.validate_apk(self.apk, expected, TEST_CERTIFICATE)

    def test_cli_requires_sha256_and_signer_expectation(self):
        with contextlib.redirect_stderr(io.StringIO()):
            for missing in ("--sha256", "--signer-sha256"):
                args = ["--apk", str(self.apk), "--sha256", self.digest(), "--signer-sha256", TEST_CERTIFICATE]
                offset = args.index(missing)
                del args[offset:offset + 2]
                with self.subTest(missing=missing), self.assertRaises(SystemExit) as failure:
                    validator.main(args)
                self.assertEqual(failure.exception.code, 2)

    def test_failure_report_is_json_and_contains_no_paths_or_tool_output(self):
        report_path = self.directory / "reports" / "validation.json"
        stdout = io.StringIO()
        with contextlib.redirect_stdout(stdout):
            result = validator.main([
                "--apk", str(self.apk), "--sha256", "b" * 64,
                "--signer-sha256", TEST_CERTIFICATE, "--report", str(report_path)
            ])
        self.assertEqual(result, 1)
        report = json.loads(report_path.read_text())
        self.assertEqual(report["status"], "rejected")
        self.assertNotIn(str(self.directory), stdout.getvalue())
        self.assertEqual(json.loads(stdout.getvalue()), report)

    def test_report_cannot_overwrite_input_apk(self):
        original = self.apk.read_bytes()
        with contextlib.redirect_stdout(io.StringIO()):
            result = validator.main([
                "--apk", str(self.apk), "--sha256", self.digest(),
                "--signer-sha256", TEST_CERTIFICATE, "--report", str(self.apk)
            ])
        self.assertEqual(result, 1)
        self.assertEqual(self.apk.read_bytes(), original)


class ToolExecutionTests(unittest.TestCase):
    def test_subprocess_uses_argument_list_no_shell_and_timeout(self):
        def execute(arguments, **options):
            options["stdout"].write(b"safe output")
            return types.SimpleNamespace(returncode=0)

        with mock.patch.object(validator.subprocess, "run", side_effect=execute) as run:
            output = validator.run_command(["/sdk/aapt", "dump", "badging", "/tmp/name with spaces.apk"], "aapt")
        self.assertEqual(output, "safe output")
        self.assertIsInstance(run.call_args.args[0], list)
        self.assertFalse(run.call_args.kwargs["shell"])
        self.assertEqual(run.call_args.kwargs["stdin"], subprocess.DEVNULL)
        self.assertGreater(run.call_args.kwargs["timeout"], 0)

    def test_tool_error_does_not_leak_untrusted_output(self):
        def execute(arguments, **options):
            options["stdout"].write(b"sensitive untrusted content")
            return types.SimpleNamespace(returncode=1)

        with mock.patch.object(validator.subprocess, "run", side_effect=execute):
            with self.assertRaises(validator.ValidationError) as error:
                validator.run_command(["apksigner"], "signature check")
        self.assertEqual(str(error.exception), "signature check rejected the APK.")

    def test_tool_output_limit_is_enforced(self):
        def execute(arguments, **options):
            options["stdout"].write(b"x" * 9)
            return types.SimpleNamespace(returncode=0)

        with mock.patch.object(validator, "MAX_TOOL_OUTPUT_BYTES", 8):
            with mock.patch.object(validator.subprocess, "run", side_effect=execute):
                with self.assertRaisesRegex(validator.ValidationError, "excessive output"):
                    validator.run_command(["aapt"], "aapt")

    def test_tool_timeout_fails_closed(self):
        with mock.patch.object(validator.subprocess, "run", side_effect=subprocess.TimeoutExpired(["aapt"], 1)):
            with self.assertRaisesRegex(validator.ValidationError, "timed out"):
                validator.run_command(["aapt"], "aapt")


if __name__ == "__main__":
    unittest.main()
