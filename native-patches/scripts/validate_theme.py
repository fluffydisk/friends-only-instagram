#!/usr/bin/env python3
"""Check the emitted APK's pinned semantic-color resource contract.

The native theme resolver uses TypedValue.resourceId, followed by Context.getColor().
An inline color is visually plausible in decoded XML but yields resourceId=0 and
crashes native startup. Inspect aapt's compiled resource values before delivery.
This deliberately checks the seven edited style families, not arbitrary ARSC data.
"""

from __future__ import annotations

from collections.abc import Iterable
import io
import os
from pathlib import Path
import re
import selectors
import subprocess
import tempfile
import time

from validate_apk import ValidationError


MAX_THEME_OUTPUT_BYTES = 64 * 1024 * 1024
THEME_TOOL_TIMEOUT_SECONDS = 120
_READ_BYTES = 64 * 1024
_PACKAGE = "dev.friendsonly.instagram"

# Independent pinned output contract: neither decoded XML nor patch-engine success
# proves that these references survived the resource encoder.
STYLE_IDS = {
    "BDSSemanticColorsDark": 0x7F140054,
    "BDSSemanticColorsLight": 0x7F140055,
    "IgdsPrismGrayOverridesDark": 0x7F14029B,
    "IgdsPrismSemanticColorsExperimentDark": 0x7F1402A7,
    "IgdsPrismSemanticColorsExperimentLight": 0x7F1402A8,
    "IgdsSemanticColorsDark": 0x7F1402AA,
    "IgdsSemanticColorsLight": 0x7F1402AB,
}
ATTRIBUTE_IDS = {
    "igds_color_primary_background": 0x7F0407B1,
    "igds_color_secondary_background": 0x7F0407E0,
    "igds_color_elevated_background": 0x7F040757,
    "igds_color_primary_text": 0x7F0407BD,
    "igds_color_secondary_text": 0x7F0407F4,
    "igds_color_link": 0x7F04078E,
    "igds_color_primary_button": 0x7F0407B2,
}
_ATTRIBUTE_PALETTE = {
    "igds_color_primary_background": "canvas",
    "igds_color_secondary_background": "surface",
    "igds_color_elevated_background": "elevated",
    "igds_color_primary_text": "text",
    "igds_color_secondary_text": "secondary_text",
    "igds_color_link": "link",
    "igds_color_primary_button": "button",
}
PALETTE_ARGB = {
    "friends_only_light_canvas": 0xFFF8F7F4,
    "friends_only_light_surface": 0xFFEDEEE9,
    "friends_only_light_elevated": 0xFFFFFFFF,
    "friends_only_light_text": 0xFF242B27,
    "friends_only_light_secondary_text": 0xFF626B65,
    "friends_only_light_link": 0xFF344D43,
    "friends_only_light_button": 0xFF344D43,
    "friends_only_dark_canvas": 0xFF151A17,
    "friends_only_dark_surface": 0xFF202722,
    "friends_only_dark_elevated": 0xFF29312B,
    "friends_only_dark_text": 0xFFF2F3EE,
    "friends_only_dark_secondary_text": 0xFFB3BDB5,
    "friends_only_dark_link": 0xFFC0CFC4,
    "friends_only_dark_button": 0xFF375A49,
}

_SPEC = re.compile(
    r"\s*spec resource (0x[0-9a-fA-F]{8}) ([\w.]+):(\w+)/([^:\s]+): flags=0x[0-9a-fA-F]{8}\s*"
)
_RESOURCE = re.compile(
    r"\s*resource (0x[0-9a-fA-F]{8}) ([\w.]+):(\w+)/([^:\s]+): (.+?)\s*"
)
_COLOR = re.compile(r"t=0x([0-9a-fA-F]+) d=0x([0-9a-fA-F]{8}) \(s=0x[0-9a-fA-F]+ r=0x[0-9a-fA-F]+\)")
_PARENT = re.compile(r"\s*Parent=0x[0-9a-fA-F]{8}\(Resolved=0x[0-9a-fA-F]{8}\), Count=([0-9]+)\s*")
_ITEM = re.compile(r"\s*#([0-9]+) \(Key=(0x[0-9a-fA-F]{8})\): (.+?)\s*")
_REFERENCE = re.compile(r"\(reference\) (0x[0-9a-fA-F]{8})")


def _expected_attributes(style: str) -> dict[int, str]:
    mode = "dark" if style.endswith("Dark") else "light"
    names = list(_ATTRIBUTE_PALETTE)
    if style == "IgdsPrismGrayOverridesDark":
        names = names[:5]
    return {ATTRIBUTE_IDS[name]: f"friends_only_{mode}_{_ATTRIBUTE_PALETTE[name]}" for name in names}


def inspect_theme_resources(resources: str | Iterable[str]) -> dict:
    """Validate a pinned aapt ``dump --values resources`` text/line stream.

    Unknown resources remain outside this check. Every configured declaration,
    value, target bag key and color must be present exactly once. Only actual
    TYPE_REFERENCE bag values targeting our correctly encoded colors are allowed.
    """
    lines = io.StringIO(resources) if isinstance(resources, str) else resources
    expected_specs = {("style", name): value for name, value in STYLE_IDS.items()}
    expected_specs.update({("attr", name): value for name, value in ATTRIBUTE_IDS.items()})
    expected_specs.update({("color", name): None for name in PALETTE_ARGB})
    pinned_ids = {value: key for key, value in expected_specs.items() if value is not None}
    specs: dict[tuple[str, str], int] = {}
    colors: dict[str, tuple[int, int]] = {}
    styles: dict[str, dict] = {}
    current: dict | None = None

    for line in lines:
        spec = _SPEC.fullmatch(line)
        if spec:
            current = None
            resource_id = int(spec[1], 16)
            key = (spec[3], spec[4])
            if resource_id in pinned_ids and key != pinned_ids[resource_id]:
                raise ValidationError("The compiled quiet theme has a renamed pinned resource.")
            if key not in expected_specs:
                continue
            if spec[2] != _PACKAGE or key in specs:
                raise ValidationError("The compiled quiet theme has a duplicate or foreign resource declaration.")
            expected_id = expected_specs[key]
            if (expected_id is not None and resource_id != expected_id) or resource_id == 0:
                raise ValidationError("The compiled quiet theme has an unexpected resource ID.")
            if key[0] == "color" and (resource_id & 0xFFFF0000) != 0x7F060000:
                raise ValidationError("The compiled quiet palette is not stored as color resources.")
            specs[key] = resource_id
            continue

        resource = _RESOURCE.fullmatch(line)
        if resource:
            current = None
            resource_id = int(resource[1], 16)
            kind, name, value = resource[3], resource[4], resource[5]
            key = (kind, name)
            if key not in expected_specs or kind == "attr":
                continue
            if resource[2] != _PACKAGE or specs.get(key) != resource_id:
                raise ValidationError("The compiled quiet theme value lacks its expected resource declaration.")
            if kind == "color":
                color = _COLOR.fullmatch(value)
                if name in colors or color is None or int(color[1], 16) != 0x1C:
                    raise ValidationError("The compiled quiet palette has a duplicate or invalid color value.")
                argb = int(color[2], 16)
                if argb != PALETTE_ARGB[name]:
                    raise ValidationError("The compiled quiet palette does not match the intended colors.")
                colors[name] = (resource_id, argb)
            else:
                if name in styles or value != "<bag>":
                    raise ValidationError("The compiled quiet theme has a missing bag or duplicate configured style.")
                current = {"name": name, "count": None, "items": 0, "references": {}}
                styles[name] = current
            continue

        if current is None:
            continue
        parent = _PARENT.fullmatch(line)
        if parent:
            if current["count"] is not None:
                raise ValidationError("The compiled quiet theme has a duplicate style bag header.")
            current["count"] = int(parent[1])
            continue
        item = _ITEM.fullmatch(line)
        if item:
            if current["count"] is None or int(item[1]) != current["items"]:
                raise ValidationError("The compiled quiet theme style bag is incomplete or malformed.")
            current["items"] += 1
            attribute = int(item[2], 16)
            if attribute not in _expected_attributes(current["name"]):
                continue
            if attribute in current["references"]:
                raise ValidationError("The compiled quiet theme has a duplicate semantic attribute.")
            reference = _REFERENCE.fullmatch(item[3])
            if reference is None or int(reference[1], 16) == 0:
                raise ValidationError("The compiled quiet theme semantic colors must remain nonzero resource references.")
            current["references"][attribute] = int(reference[1], 16)
        elif line.lstrip().startswith(("#", "Parent=")):
            raise ValidationError("The compiled quiet theme style bag is malformed.")

    if set(specs) != set(expected_specs):
        raise ValidationError("The compiled quiet theme is missing required style, attribute or color declarations.")
    if set(colors) != set(PALETTE_ARGB) or set(styles) != set(STYLE_IDS):
        raise ValidationError("The compiled quiet theme is missing required color values or style bags.")
    color_ids = [value[0] for value in colors.values()]
    if len(set(color_ids)) != len(color_ids):
        raise ValidationError("The compiled quiet palette has duplicate color resource IDs.")
    reference_count = 0
    for name, style in styles.items():
        expected = _expected_attributes(name)
        if style["count"] != style["items"] or set(style["references"]) != set(expected):
            raise ValidationError("The compiled quiet theme is missing semantic attributes or complete style bags.")
        for attribute, color_name in expected.items():
            if style["references"][attribute] != colors[color_name][0]:
                raise ValidationError("The compiled quiet theme references an incorrect or missing palette color.")
            reference_count += 1
    return {
        "status": "passed",
        "semantic_style_count": len(styles),
        "semantic_attribute_reference_count": reference_count,
        "palette_color_resource_count": len(colors),
        "palette_colors": {
            name: {"resource_id": f"0x{resource_id:08x}", "argb": f"0x{argb:08x}"}
            for name, (resource_id, argb) in sorted(colors.items())
        },
        "compiled_color_reference_contract": "TYPE_REFERENCE to nonzero own color resource IDs",
    }


def inspect_theme_apk(apk: Path | str, aapt: Path | str) -> dict:
    """Run the pinned aapt without a shell or raw output in reports/logs.

    Resource dumps exceed the ordinary small tool-output allowance. Stream to a
    temporary file with a separate 64 MiB cap and a finite 120 second deadline.
    """
    process = None
    try:
        with tempfile.TemporaryFile() as output:
            process = subprocess.Popen(
                [str(aapt), "dump", "--values", "resources", str(apk)],
                stdin=subprocess.DEVNULL, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                shell=False,
            )
            deadline = time.monotonic() + THEME_TOOL_TIMEOUT_SECONDS
            size = 0
            with selectors.DefaultSelector() as selector:
                assert process.stdout is not None
                selector.register(process.stdout, selectors.EVENT_READ)
                while selector.get_map():
                    remaining = deadline - time.monotonic()
                    if remaining <= 0:
                        raise ValidationError("aapt theme resource inspection timed out.")
                    for event, _ in selector.select(min(remaining, 1)):
                        chunk = os.read(event.fileobj.fileno(), _READ_BYTES)
                        if not chunk:
                            selector.unregister(event.fileobj)
                            continue
                        size += len(chunk)
                        if size > MAX_THEME_OUTPUT_BYTES:
                            raise ValidationError("aapt theme resource inspection returned excessive output.")
                        output.write(chunk)
            remaining = deadline - time.monotonic()
            if remaining <= 0:
                raise ValidationError("aapt theme resource inspection timed out.")
            if process.wait(timeout=remaining) != 0:
                raise ValidationError("aapt rejected the APK theme resources.")
            output.seek(0)
            # Native string resources can contain CESU-8/unpaired-surrogate bytes.
            # Preserve these outside our exact ASCII resource names and typed values.
            with io.TextIOWrapper(output, encoding="utf-8", errors="surrogateescape") as text:
                return inspect_theme_resources(text)
    except subprocess.TimeoutExpired as exc:
        raise ValidationError("aapt theme resource inspection timed out.") from exc
    except (OSError, UnicodeError) as exc:
        raise ValidationError("aapt theme resource inspection could not be executed successfully.") from exc
    finally:
        if process is not None:
            if process.poll() is None:
                process.kill()
                process.wait()
            if process.stdout is not None:
                process.stdout.close()
