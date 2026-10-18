"""Final-output regressions: font must be bundled offline together with its license."""
from pathlib import Path
import sys
import tempfile
import unittest
import zipfile
sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
from validate_design import inspect_design_assets
from validate_apk import ValidationError

ROOT = Path(__file__).resolve().parents[1]

class DesignAssetsTest(unittest.TestCase):
    def archive(self, root, contents):
        target = Path(root) / "design.apk"
        with zipfile.ZipFile(target, "w") as output:
            for name, payload in contents:
                output.writestr("assets/friends_only/" + name, payload)
        return target

    def assets(self):
        return [(name, (ROOT / "design" / name).read_bytes()) for name in ("Manrope.ttf", "OFL-Manrope.txt")]

    def test_real_assets_and_license_are_accepted(self):
        with tempfile.TemporaryDirectory() as root:
            result = inspect_design_assets(self.archive(root, self.assets()))
            self.assertEqual(result["verified_assets"], 2)
            self.assertEqual(result["rendered_ui_test"], "not_run")

    def test_missing_font_or_license_rejects_output(self):
        for keep in (0, 1):
            with self.subTest(keep=keep), tempfile.TemporaryDirectory() as root:
                with self.assertRaises(ValidationError):
                    inspect_design_assets(self.archive(root, [self.assets()[keep]]))

    def test_same_size_modified_asset_is_rejected(self):
        for index in (0, 1):
            items = self.assets()
            name, value = items[index]
            items[index] = (name, bytes([value[0] ^ 1]) + value[1:])
            with self.subTest(index=index), tempfile.TemporaryDirectory() as root:
                with self.assertRaises(ValidationError):
                    inspect_design_assets(self.archive(root, items))

    def test_truncated_font_rejects_output(self):
        items = self.assets()
        items[0] = (items[0][0], items[0][1][:-1])
        with tempfile.TemporaryDirectory() as root:
            with self.assertRaises(ValidationError):
                inspect_design_assets(self.archive(root, items))
