"""Split merge audits use small ZIP fixtures, not an Instagram binary."""
from pathlib import Path
import sys
import tempfile
import unittest
import zipfile

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'scripts'))
from merge_apkm import audit_payloads, payload_inventory
from validate_apk import ValidationError


class MergeAuditTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)

    def apk(self, name, entries):
        path = self.root / name
        with zipfile.ZipFile(path, 'w') as archive:
            for entry, data in entries.items():
                archive.writestr(entry, data)
        return path

    def test_dex_renaming_preserves_code_and_all_assets(self):
        base = self.apk('base.apk', {'classes.dex': b'base code', 'assets/a': b'asset'})
        feature = self.apk('feature.apk', {'classes.dex': b'feature code', 'lib/arm64-v8a/a.so': b'library'})
        merged = self.apk('merged.apk', {'classes.dex': b'base code', 'classes2.dex': b'feature code',
                                         'assets/a': b'asset', 'lib/arm64-v8a/a.so': b'library'})
        result = audit_payloads([base, feature], merged)
        self.assertEqual(result['dex_files_preserved'], 2)

    def test_lost_or_rewritten_code_rejected(self):
        original = self.apk('base.apk', {'classes.dex': b'original'})
        for payload in ({}, {'classes.dex': b'changed'}):
            with self.subTest(payload=payload), self.assertRaises(ValidationError):
                audit_payloads([original], self.apk('merged.apk', payload))

    def test_conflicting_assets_rejected_before_merge(self):
        base = self.apk('base.apk', {'assets/a': b'first'})
        feature = self.apk('feature.apk', {'assets/a': b'different'})
        with self.assertRaises(ValidationError):
            payload_inventory([base, feature])

    def test_missing_native_library_rejected(self):
        original = self.apk('base.apk', {'classes.dex': b'code', 'lib/arm64-v8a/a.so': b'library'})
        merged = self.apk('merged.apk', {'classes.dex': b'code'})
        with self.assertRaises(ValidationError):
            audit_payloads([original], merged)

    def test_uppercase_split_code_included_in_merge_audit(self):
        from unittest import mock
        import merge_apkm
        base = self.apk('base.apk', {'classes.dex': b'base'})
        feature = self.apk('feature.APK', {'classes.dex': b'feature'})
        output = self.root / 'merged.apk'
        def merge_command(_):
            self.apk('merged.apk', {'classes.dex': b'base', 'classes2.dex': b'feature'})
        with mock.patch.object(merge_apkm, 'download'), mock.patch.object(merge_apkm, 'java_tool', return_value='java'), \
             mock.patch.object(merge_apkm, 'run', side_effect=merge_command), \
             mock.patch.object(merge_apkm, 'normalize_apk_manifest', return_value={'status': 'unchanged'}), \
             mock.patch.object(merge_apkm, 'inspect_archive', return_value={'native_abis': ['arm64-v8a']}), \
             mock.patch.object(merge_apkm, 'run_command', return_value='fixture metadata'), \
             mock.patch.object(merge_apkm, 'inspect_manifest', return_value={'package': 'com.instagram.android'}):
            result = merge_apkm.merge_verified_splits(self.root, output, self.root)
        self.assertEqual(result['payload_audit']['dex_files_preserved'], 2)


if __name__ == '__main__':
    unittest.main()
