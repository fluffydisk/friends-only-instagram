"""Patching report checks: an engine exit code alone must never authorize signing."""
import copy
import contextlib
import importlib.util
import io
from pathlib import Path
import sys
import unittest

SCRIPTS = Path(__file__).resolve().parents[1] / "scripts"
sys.path.insert(0, str(SCRIPTS))
spec = importlib.util.spec_from_file_location("native_apply", SCRIPTS / "apply.py")
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)
from validate_apk import ValidationError


OWN_PACKAGE = "dev.friendsonly.instagram"
IDENTITY_BADGING = ("application-label:'Friends Only'\n"
                    "application-label-tr:'Friends Only'\n"
                    "launchable-activity: name='com.instagram.android.activity.MainTabActivity' label='Friends Only' icon='res/icon.png'\n")
IDENTITY_XML = '''N: android=http://schemas.android.com/apk/res/android
  E: manifest (line=1)
    A: package="dev.friendsonly.instagram" (Raw: "dev.friendsonly.instagram")
    E: permission (line=2)
      A: android:name(0x01010003)="dev.friendsonly.instagram.permission.SYSTEM_ONLY"
    E: permission (line=3)
      A: android:name(0x01010003)=".permission.RECEIVE_ADM_MESSAGE"
    E: uses-permission (line=4)
      A: android:name(0x01010003)=".permission.RECEIVE_ADM_MESSAGE"
    E: queries (line=5)
      E: provider (line=6)
        A: android:authorities(0x01010018)="com.facebook.pages.app.ig4work.tokenhandoff"
    E: application (line=7)
      A: android:label(0x01010001)=@0x7f130009
      E: provider (line=8)
        A: android:authorities(0x01010018)="dev.friendsonly.instagram.provider.a;dev.friendsonly.instagram.provider.b"
      E: activity (line=9)
        A: android:name(0x01010003)="com.instagram.android.activity.SettingsActivity"
        A: android:permission(0x01010006)="dev.friendsonly.instagram.permission.SYSTEM_ONLY"
      E: activity (line=10)
        A: android:permission(0x01010006)="com.instagram.android.permission.CREATE_SHORTCUT"
      E: activity-alias (line=11)
        A: android:name(0x01010003)="com.instagram.android.activity.MainTabActivity"
        A: android:label(0x01010001)=@0x7f130009
        A: android:targetActivity(0x01010202)="com.instagram.android.activity.SettingsActivity"
        A: android:exported(0x01010010)=(type 0x12)0xffffffff
        E: intent-filter (line=12)
          E: action (line=13)
            A: android:name(0x01010003)="android.intent.action.MAIN"
          E: category (line=14)
            A: android:name(0x01010003)="android.intent.category.LAUNCHER"
'''


class OutputIdentityTests(unittest.TestCase):
    def test_own_identity_and_all_authorities_checked_without_renaming_classes_or_external_permissions(self):
        report = module.inspect_application_identity(IDENTITY_BADGING, IDENTITY_XML)
        self.assertEqual(report["application_label"], "Friends Only")
        self.assertEqual(report["own_provider_authorities"], [OWN_PACKAGE + ".provider.a", OWN_PACKAGE + ".provider.b"])
        self.assertIn(OWN_PACKAGE + ".permission.RECEIVE_ADM_MESSAGE", report["own_permission_definitions"])
        self.assertEqual(report["launcher_and_localized_labels_checked"], 3)

    def test_original_application_or_launcher_label_is_rejected(self):
        for badging in (IDENTITY_BADGING.replace("application-label:'Friends Only'", "application-label:'Instagram'"),
                        IDENTITY_BADGING.replace("application-label-tr:'Friends Only'", "application-label-tr:'Instagram'"),
                        IDENTITY_BADGING.replace("label='Friends Only'", "label='Instagram'"),
                        ""):
            with self.subTest(badging=badging), self.assertRaisesRegex(ValidationError, "labels"):
                module.inspect_application_identity(badging, IDENTITY_XML)

    def test_manifest_alias_proves_launcher_when_aapt_badging_omits_alias(self):
        badging = "application-label:'Friends Only'\napplication-label-tr:'Friends Only'\n"
        result = module.inspect_application_identity(badging, IDENTITY_XML)
        self.assertEqual(result["manifest_launchers"], ["com.instagram.android.activity.MainTabActivity"])

    def test_disabled_or_unexported_default_alias_is_rejected(self):
        for xml in (IDENTITY_XML.replace('E: activity-alias (line=11)', 'E: activity-alias (line=11)\n        A: android:enabled(0x0101000e)=(type 0x12)0x0'),
                    IDENTITY_XML.replace('android:exported(0x01010010)=(type 0x12)0xffffffff', 'android:exported(0x01010010)=(type 0x12)0x0')):
            with self.subTest(xml=xml), self.assertRaises(ValidationError):
                module.inspect_application_identity(IDENTITY_BADGING, xml)

    def test_alias_label_must_match_app_label_reference(self):
        xml = IDENTITY_XML.replace('A: android:label(0x01010001)=@0x7f130009\n        A: android:targetActivity',
                                  'A: android:label(0x01010001)=@0x7f130123\n        A: android:targetActivity')
        with self.assertRaisesRegex(ValidationError, "launcher label"):
            module.inspect_application_identity(IDENTITY_BADGING, xml)

    def test_alias_can_inherit_application_label(self):
        xml = IDENTITY_XML.replace('        A: android:label(0x01010001)=@0x7f130009\n', '')
        module.inspect_application_identity(IDENTITY_BADGING, xml)

    def test_main_and_launcher_must_be_in_the_same_intent_filter(self):
        xml = IDENTITY_XML.replace('          E: category (line=14)', '        E: intent-filter (line=14)\n          E: category (line=14)')
        with self.assertRaisesRegex(ValidationError, "MAIN/LAUNCHER"):
            module.inspect_application_identity(IDENTITY_BADGING, xml)

    def test_launcher_target_must_be_declared_and_enabled(self):
        xml = IDENTITY_XML.replace('android:targetActivity(0x01010202)="com.instagram.android.activity.SettingsActivity"',
                                  'android:targetActivity(0x01010202)="com.instagram.MissingActivity"')
        with self.assertRaisesRegex(ValidationError, "target activity"):
            module.inspect_application_identity(IDENTITY_BADGING, xml)

    def test_one_old_authority_in_a_semicolon_list_is_rejected(self):
        xml = IDENTITY_XML.replace(OWN_PACKAGE + ".provider.b", "com.instagram.android.provider.b")
        with self.assertRaisesRegex(ValidationError, "provider authority"):
            module.inspect_application_identity(IDENTITY_BADGING, xml)

    def test_provider_authorities_outside_original_package_also_need_own_identity(self):
        xml = IDENTITY_XML.replace(OWN_PACKAGE + ".provider.b", "com.instagram.fileprovider")
        with self.assertRaisesRegex(ValidationError, "provider authority"):
            module.inspect_application_identity(IDENTITY_BADGING, xml)

    def test_independent_fallback_authority_namespace_is_accepted(self):
        authority = OWN_PACKAGE + "_com.instagram.fileprovider"
        xml = IDENTITY_XML.replace(OWN_PACKAGE + ".provider.b", authority)
        report = module.inspect_application_identity(IDENTITY_BADGING, xml)
        self.assertIn(authority, report["own_provider_authorities"])

    def test_old_custom_permission_definition_is_rejected(self):
        xml = IDENTITY_XML.replace(OWN_PACKAGE + ".permission.SYSTEM_ONLY", "com.instagram.android.permission.SYSTEM_ONLY", 1)
        with self.assertRaisesRegex(ValidationError, "custom permission"):
            module.inspect_application_identity(IDENTITY_BADGING, xml)

    def test_component_permission_must_reference_renamed_definition(self):
        xml = IDENTITY_XML.replace('android:permission(0x01010006)="' + OWN_PACKAGE + '.permission.SYSTEM_ONLY"',
                                  'android:permission(0x01010006)="com.instagram.android.permission.SYSTEM_ONLY"')
        with self.assertRaisesRegex(ValidationError, "app-defined permission"):
            module.inspect_application_identity(IDENTITY_BADGING, xml)

    def test_missing_own_permission_declaration_is_rejected(self):
        xml = IDENTITY_XML.replace('android:name(0x01010003)="' + OWN_PACKAGE + '.permission.SYSTEM_ONLY"',
                                  'android:name(0x01010003)="android.permission.INTERNET"', 1)
        with self.assertRaisesRegex(ValidationError, "no matching declaration"):
            module.inspect_application_identity(IDENTITY_BADGING, xml)

    def test_external_query_provider_and_permission_are_preserved(self):
        xml = IDENTITY_XML.replace("com.facebook.pages.app.ig4work.tokenhandoff", "com.instagram.android.external.query")
        module.inspect_application_identity(IDENTITY_BADGING, xml)


class PatchResultTest(unittest.TestCase):
    def setUp(self):
        self.result = {
            "success": True, "packageName": "com.instagram.android", "packageVersion": "451.0.0.20.80",
            "appliedPatches": [{"name": name} for name in module.PATCH_NAMES],
            "failedPatches": [], "patchingSteps": [
                {"step": "PATCHING", "success": True}, {"step": "REBUILDING", "success": True}],
        }

    def test_only_complete_result_accepted(self):
        module.validate_patch_result(self.result)

    def test_engine_default_success_omitted(self):
        del self.result["success"]
        module.validate_patch_result(self.result)

    def test_identity_patch_is_required_in_addition_to_ui_and_discovery(self):
        self.result["appliedPatches"] = self.result["appliedPatches"][:2]
        with self.assertRaisesRegex(ValidationError, "configured Friends Only patches"):
            module.validate_patch_result(self.result)

    def test_feed_video_tap_patch_is_required_even_when_earlier_three_succeed(self):
        self.result["appliedPatches"] = [entry for entry in self.result["appliedPatches"]
                                          if entry["name"] != "Friends Only feed video taps"]
        with self.assertRaisesRegex(ValidationError, "configured Friends Only patches"):
            module.validate_patch_result(self.result)

    def test_duplicate_requested_patch_is_not_a_complete_result(self):
        self.result["appliedPatches"][-1] = self.result["appliedPatches"][0]
        with self.assertRaises(ValidationError):
            module.validate_patch_result(self.result)

    def test_report_path_cannot_overwrite_original_on_failure(self):
        import tempfile
        from unittest import mock
        with tempfile.TemporaryDirectory() as directory:
            original = Path(directory) / "original.apk"
            original.write_bytes(b"original-test-input")
            argv = ["apply.py", "--apk", str(original), "--sha256", "a" * 64,
                    "--signer-sha256", "b" * 64, "--patch-bundle", str(Path(directory) / "own.mpp"),
                    "--output", str(Path(directory) / "patched.apk"), "--report", str(original)]
            with mock.patch.object(sys, "argv", argv), mock.patch.object(module, "apply") as patched:
                self.assertEqual(module.main(), 1)
                patched.assert_not_called()
            self.assertEqual(original.read_bytes(), b"original-test-input")

    def test_invalid_zip_produces_safe_failure_report(self):
        import json
        import tempfile
        import zipfile
        from unittest import mock
        with tempfile.TemporaryDirectory() as directory:
            report = Path(directory) / 'report.json'
            argv = ['apply.py', '--apk', str(Path(directory) / 'bad.apk'), '--sha256', 'a' * 64,
                    '--signer-sha256', 'b' * 64, '--patch-bundle', str(Path(directory) / 'own.mpp'),
                    '--output', str(Path(directory) / 'patched.apk'), '--report', str(report)]
            with mock.patch.object(sys, 'argv', argv), mock.patch.object(module, 'apply', side_effect=zipfile.BadZipFile()):
                self.assertEqual(module.main(), 1)
            self.assertEqual(json.loads(report.read_text())['status'], 'failed')

    def test_our_validation_error_identifies_failed_check_without_subprocess_details(self):
        import json
        import tempfile
        from unittest import mock
        with tempfile.TemporaryDirectory() as directory:
            report = Path(directory) / "report.json"
            argv = ["apply.py", "--apk", str(Path(directory) / "original.apk"), "--sha256", "a" * 64,
                    "--signer-sha256", "b" * 64, "--patch-bundle", str(Path(directory) / "own.mpp"),
                    "--output", str(Path(directory) / "patched.apk"), "--report", str(report)]
            error = ValidationError("The output manifest has non-nested namespace closing events.")
            stderr = io.StringIO()
            with mock.patch.object(sys, "argv", argv), mock.patch.object(module, "apply", side_effect=error), contextlib.redirect_stderr(stderr):
                self.assertEqual(module.main(), 1)
            result = json.loads(report.read_text())
            self.assertEqual(result["category"], "validation")
            self.assertEqual(result["reason"], str(error))
            self.assertIn(str(error), stderr.getvalue())
            self.assertNotIn(directory, stderr.getvalue())

    def test_subprocess_exception_does_not_print_sensitive_command_arguments(self):
        import json
        import subprocess
        import tempfile
        from unittest import mock
        with tempfile.TemporaryDirectory() as directory:
            report = Path(directory) / "report.json"
            argv = ["apply.py", "--apk", str(Path(directory) / "original.apk"), "--sha256", "a" * 64,
                    "--signer-sha256", "b" * 64, "--patch-bundle", str(Path(directory) / "own.mpp"),
                    "--output", str(Path(directory) / "patched.apk"), "--report", str(report)]
            secret = "synthetic-sensitive-signing-argument"
            error = subprocess.CalledProcessError(1, ["sign", secret])
            stderr = io.StringIO()
            with mock.patch.object(sys, "argv", argv), mock.patch.object(module, "apply", side_effect=error), contextlib.redirect_stderr(stderr):
                self.assertEqual(module.main(), 1)
            self.assertNotIn(secret, stderr.getvalue() + report.read_text())
            self.assertNotIn("validation", json.loads(report.read_text()).get("category", ""))

    def test_partial_or_foreign_result_rejected(self):
        changes = [
            {"success": False}, {"success": "true"}, {"failedPatches": [{"name": "broken"}]},
            {"appliedPatches": []}, {"appliedPatches": [{"name": module.PATCH_NAMES[0]}]},
            {"appliedPatches": self.result["appliedPatches"] + [{"name": "unrequested patch"}]},
            {"packageName": "other.package"}, {"packageVersion": "452.0.0.0.0"},
            {"patchingSteps": []}, {"patchingSteps": [{"success": False}]},
            {"patchingSteps": [{"step": "PATCHING", "success": True}]},
            {"patchingSteps": [{"step": "PATCHING", "success": True}, {"step": "PATCHING", "success": True}]},
        ]
        for change in changes:
            with self.subTest(change=change), self.assertRaises(ValidationError):
                result = copy.deepcopy(self.result)
                result.update(change)
                module.validate_patch_result(result)


if __name__ == "__main__":
    unittest.main()
