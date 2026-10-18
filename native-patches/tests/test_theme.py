"""Regression checks for the compiled resource-ID contract that broke alpha.5 startup."""
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
import validate_theme as theme
from validate_apk import ValidationError


# Independent compact aapt fixtures: a real resource reference differs from an inline
# color even when Android would draw exactly the same color through another API.
STYLES = [
    ("BDSSemanticColorsDark", 0x7F140054), ("BDSSemanticColorsLight", 0x7F140055),
    ("IgdsPrismGrayOverridesDark", 0x7F14029B),
    ("IgdsPrismSemanticColorsExperimentDark", 0x7F1402A7),
    ("IgdsPrismSemanticColorsExperimentLight", 0x7F1402A8),
    ("IgdsSemanticColorsDark", 0x7F1402AA), ("IgdsSemanticColorsLight", 0x7F1402AB),
]
ATTRS = [
    ("primary_background", 0x7F0407B1, "canvas"),
    ("secondary_background", 0x7F0407E0, "surface"),
    ("elevated_background", 0x7F040757, "elevated"),
    ("primary_text", 0x7F0407BD, "text"), ("secondary_text", 0x7F0407F4, "secondary_text"),
    ("link", 0x7F04078E, "link"), ("primary_button", 0x7F0407B2, "button"),
]
COLORS = {
    "light_canvas": "fff8f7f4", "light_surface": "ffedeee9", "light_elevated": "ffffffff",
    "light_text": "ff28292c", "light_secondary_text": "ff68696d", "light_link": "ff424449",
    "light_button": "ff424449", "dark_canvas": "ff0d0e10", "dark_surface": "ff151618",
    "dark_elevated": "ff1d1e21", "dark_text": "ffededef", "dark_secondary_text": "ffa5a6aa",
    "dark_link": "ffd4d5d8", "dark_button": "ff3e4045",
}
COLOR_IDS = {name: 0x7F0604C1 + index for index, name in enumerate(COLORS)}
PACKAGE = "dev.friendsonly.instagram"


def fixture() -> str:
    specs, values = [], []
    for kind, name, rid in (
        [("style", name, rid) for name, rid in STYLES]
        + [("attr", "igds_color_" + name, rid) for name, rid, _ in ATTRS]
        + [("color", "friends_only_" + name, rid) for name, rid in COLOR_IDS.items()]
    ):
        specs.append(f"      spec resource 0x{rid:08x} {PACKAGE}:{kind}/{name}: flags=0x00000000\n")
    for name, argb in COLORS.items():
        values.append(f"        resource 0x{COLOR_IDS[name]:08x} {PACKAGE}:color/friends_only_{name}: "
                      f"t=0x1c d=0x{argb} (s=0x0008 r=0x00)\n")
    for name, rid in STYLES:
        attrs = ATTRS[:5] if name == "IgdsPrismGrayOverridesDark" else ATTRS
        mode = "dark" if name.endswith("Dark") else "light"
        values.append(f"        resource 0x{rid:08x} {PACKAGE}:style/{name}: <bag>\n")
        values.append(f"          Parent=0x7f140053(Resolved=0x7f140053), Count={len(attrs)}\n")
        for index, (_, aid, suffix) in enumerate(attrs):
            color = COLOR_IDS[mode + "_" + suffix]
            values.append(f"          #{index} (Key=0x{aid:08x}): (reference) 0x{color:08x}\n")
    return "Package Groups (1)\n" + "".join(specs + values)


class CompiledThemeTests(unittest.TestCase):
    def setUp(self):
        self.text = fixture()

    def test_all_seven_families_and_47_references_to_14_actual_colors(self):
        result = theme.inspect_theme_resources(self.text.splitlines(keepends=True))
        self.assertEqual((result["semantic_style_count"], result["semantic_attribute_reference_count"],
                          result["palette_color_resource_count"]), (7, 47, 14))
        self.assertEqual(result["palette_colors"]["friends_only_light_canvas"]["argb"], "0xfff8f7f4")

    def test_every_target_rejects_the_alpha5_inline_literal_regression(self):
        # Each of the 47 semantic items must independently fail even with the exact intended RGB.
        lines = self.text.splitlines(keepends=True)
        indexes = [index for index, line in enumerate(lines) if "(reference)" in line]
        self.assertEqual(len(indexes), 47)
        for index in indexes:
            bad = lines.copy()
            bad[index] = bad[index].split(": (reference)")[0] + ": (color) #ff0d0e10\n"
            with self.subTest(index=index), self.assertRaisesRegex(ValidationError, "nonzero resource references"):
                theme.inspect_theme_resources(bad)

    def test_zero_attribute_or_malformed_reference_fails(self):
        original = "(reference) 0x7f0604c8"
        for value in ("(reference) 0x00000000", "(attribute) 0x7f0407b1", "(reference) 0x7f0604c8 trailing"):
            with self.subTest(value=value), self.assertRaises(ValidationError):
                theme.inspect_theme_resources(self.text.replace(original, value, 1))

    def test_unrelated_bags_and_literals_are_outside_the_contract(self):
        text = self.text + f"        resource 0x7f14ffff {PACKAGE}:style/Unchanged: <bag>\n"
        text += "          Parent=0x00000000(Resolved=0x00000000), Count=1\n          #0 (Key=0x7f04ffff): (color) #00000000\n"
        self.assertEqual(theme.inspect_theme_resources(text)["status"], "passed")

    def test_missing_or_duplicate_style_is_rejected(self):
        line = next(line for line in self.text.splitlines(keepends=True) if ":style/BDSSemanticColorsDark: <bag>" in line)
        for text in (self.text.replace(line, ""), self.text + line):
            with self.subTest(text=text[-80:]), self.assertRaises(ValidationError):
                theme.inspect_theme_resources(text)

    def test_missing_or_duplicate_declaration_is_rejected(self):
        line = next(line for line in self.text.splitlines(keepends=True) if "spec resource" in line)
        for text in (self.text.replace(line, ""), self.text + line):
            with self.subTest(text=text[-80:]), self.assertRaises(ValidationError):
                theme.inspect_theme_resources(text)

    def test_renamed_or_moved_style_and_attribute_ids_are_rejected(self):
        for old, new in (("BDSSemanticColorsDark", "RenamedDark"), ("0x7f140054", "0x7f14ffff"),
                         ("igds_color_primary_background", "unknown_background"), ("0x7f0407b1", "0x7f04ffff")):
            with self.subTest(old=old), self.assertRaises(ValidationError):
                theme.inspect_theme_resources(self.text.replace(old, new))

    def test_missing_semantic_attribute_or_bag_tail_is_rejected(self):
        line = next(line for line in self.text.splitlines(keepends=True) if "#6 (Key=" in line)
        with self.assertRaisesRegex(ValidationError, "missing semantic"):
            theme.inspect_theme_resources(self.text.replace(line, "", 1))
        with self.assertRaises(ValidationError):
            theme.inspect_theme_resources(self.text.rsplit("\n", 3)[0])

    def test_duplicate_semantic_key_is_rejected(self):
        text = self.text.replace("#1 (Key=0x7f0407e0)", "#1 (Key=0x7f0407b1)", 1)
        with self.assertRaisesRegex(ValidationError, "duplicate semantic"):
            theme.inspect_theme_resources(text)

    def test_broken_wrong_or_light_dark_swapped_reference_is_rejected(self):
        for target in ("0x7f06ffff", "0x7f0604c1", "0x7f130001"):
            text = self.text.replace("(reference) 0x7f0604c8", "(reference) " + target, 1)
            with self.subTest(target=target), self.assertRaisesRegex(ValidationError, "incorrect or missing palette"):
                theme.inspect_theme_resources(text)

    def test_wrong_argb_or_color_storage_type_is_rejected(self):
        for old, new in (("d=0xfff8f7f4", "d=0xff000000"), ("t=0x1c", "t=0x01"),
                         ("t=0x1c", "t=0x1d")):
            with self.subTest(new=new), self.assertRaises(ValidationError):
                theme.inspect_theme_resources(self.text.replace(old, new, 1))

    def test_missing_or_duplicate_color_value_is_rejected(self):
        line = next(line for line in self.text.splitlines(keepends=True) if ":color/" in line and " t=" in line)
        for text in (self.text.replace(line, ""), self.text + line):
            with self.subTest(text=text[-80:]), self.assertRaises(ValidationError):
                theme.inspect_theme_resources(text)

    def test_shared_zero_or_non_color_palette_id_is_rejected(self):
        for value in ("0x7f0604c2", "0x00000000", "0x7f130001"):
            with self.subTest(value=value), self.assertRaises(ValidationError):
                theme.inspect_theme_resources(self.text.replace("0x7f0604c1", value))

    def test_foreign_package_is_rejected(self):
        with self.assertRaisesRegex(ValidationError, "foreign"):
            theme.inspect_theme_resources(self.text.replace(PACKAGE, "com.instagram.android", 1))

    def test_malformed_or_duplicate_bag_header_is_rejected(self):
        line = "          Parent=0x7f140053(Resolved=0x7f140053), Count=7\n"
        for value in ("", line + line, line.replace("Count=7", "Count=6")):
            with self.subTest(value=value), self.assertRaises(ValidationError):
                theme.inspect_theme_resources(self.text.replace(line, value, 1))

    def test_empty_or_unrecognized_dump_is_rejected(self):
        for text in ("", "aapt: resources unavailable\n"):
            with self.assertRaises(ValidationError):
                theme.inspect_theme_resources(text)


class ResourceToolTests(unittest.TestCase):
    def run_fake(self, body: str):
        with tempfile.TemporaryDirectory(prefix="friends only theme ") as directory:
            executable = Path(directory) / "fake aapt"
            executable.write_text("#!/usr/bin/env python3\n" + body)
            executable.chmod(0o700)
            return theme.inspect_theme_apk(Path(directory) / "input ; $(unsafe).apk", executable)

    def test_bounded_tool_preserves_the_real_resource_dump(self):
        result = self.run_fake("import sys\nassert sys.argv[1:4] == ['dump', '--values', 'resources']\nprint(" + repr(fixture()) + ")\n")
        self.assertEqual(result["semantic_attribute_reference_count"], 47)

    def test_nonzero_exit_does_not_expose_raw_tool_output(self):
        with self.assertRaisesRegex(ValidationError, "^aapt rejected the APK theme resources\\.$"):
            self.run_fake("import sys\nprint('PRIVATE RAW OUTPUT')\nsys.exit(3)\n")

    def test_tool_output_limit_is_enforced(self):
        with patch.object(theme, "MAX_THEME_OUTPUT_BYTES", 1024), self.assertRaisesRegex(ValidationError, "excessive output"):
            self.run_fake("print('x' * 4096)\n")

    def test_tool_timeout_is_enforced(self):
        with patch.object(theme, "THEME_TOOL_TIMEOUT_SECONDS", 0.1), self.assertRaisesRegex(ValidationError, "timed out"):
            self.run_fake("import time\ntime.sleep(3)\n")

    def test_unexecutable_tool_has_a_safe_error(self):
        with self.assertRaisesRegex(ValidationError, "could not be executed"):
            theme.inspect_theme_apk("ignored.apk", "/nonexistent/friends-only-aapt")

    def test_native_non_utf8_strings_outside_checked_resources_do_not_break_inspection(self):
        body = "import sys\nsys.stdout.buffer.write(" + repr(fixture().encode() + b"\n        unrelated native string: \xed\xa0\x80\xff\n") + ")\n"
        self.assertEqual(self.run_fake(body)["semantic_attribute_reference_count"], 47)

    def test_invalid_bytes_in_a_required_name_still_fail_closed(self):
        broken = fixture().encode().replace(b"friends_only_light_canvas", b"friends_only_light_\xffcanvas")
        with self.assertRaises(ValidationError):
            self.run_fake("import sys\nsys.stdout.buffer.write(" + repr(broken) + ")\n")


if __name__ == "__main__":
    unittest.main()
