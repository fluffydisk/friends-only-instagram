"""Patching report checks: an engine exit code alone must never authorize signing."""
import copy
import importlib.util
from pathlib import Path
import sys
import unittest

SCRIPTS = Path(__file__).resolve().parents[1] / "scripts"
sys.path.insert(0, str(SCRIPTS))
spec = importlib.util.spec_from_file_location("native_apply", SCRIPTS / "apply.py")
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)
from validate_apk import ValidationError


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
