"""Guard the independent design assets in the final APK; not a rendered UI test."""
from __future__ import annotations
import hashlib
from pathlib import Path
import zipfile
from validate_apk import ValidationError

ASSETS = {
    "assets/friends_only/Manrope.ttf": (164700, "3ae11c49db0455a3cc33e37d380f20fdb8c7f8b41dc07625c177e3d87a9d6ae6"),
    "assets/friends_only/OFL-Manrope.txt": (4387, "58172e0c0fac2cda8a37b348164bb55e44b0e69051e557e92b1d3f6910141f7b"),
}


def inspect_design_assets(apk: Path) -> dict:
    with zipfile.ZipFile(apk) as source:
        names = source.namelist()
        for name, (size, digest) in ASSETS.items():
            if names.count(name) != 1:
                raise ValidationError("Missing or duplicate independent design asset.")
            info = source.getinfo(name)
            if info.file_size != size or hashlib.sha256(source.read(info)).hexdigest() != digest:
                raise ValidationError("Independent font or license digest changed.")
    return {"font": "Manrope", "license": "SIL Open Font License 1.1", "verified_assets": 2,
            "asset_hashes_verified": True, "rendered_ui_test": "not_run"}

# Pinned resource table paths for the four density/night wordmark variants in 451.
WORDMARK_PATHS = {
    "res/pq2.webp": "wordmark-xhdpi.webp", "res/kvb.webp": "wordmark-night-xhdpi.webp",
    "res/uep.webp": "wordmark-xxhdpi.webp", "res/k38.webp": "wordmark-night-xxhdpi.webp",
}


def inspect_wordmark_assets(apk: Path) -> dict:
    import json
    manifest = json.loads((Path(__file__).resolve().parents[1] / "design/wordmark-manifest.json").read_text())
    checked = []
    with zipfile.ZipFile(apk) as source:
        names = source.namelist()
        for resource, asset in WORDMARK_PATHS.items():
            spec = manifest[asset]
            if names.count(resource) != 1:
                raise ValidationError("Missing or duplicate pinned wordmark resource path.")
            info = source.getinfo(resource)
            if info.file_size != spec["size"] or hashlib.sha256(source.read(info)).hexdigest() != spec["sha256"]:
                raise ValidationError("Original wordmark survived or replacement payload changed.")
            checked.append(resource)
    return {"status": "passed", "density_night_variants": 4, "resource_paths": checked,
            "original_intrinsic_dimensions_preserved": True, "rendered_ui_test": "not_run"}
