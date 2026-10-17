#!/usr/bin/env python3
"""Public pinned tool bootstrap and process helpers. No Instagram data access."""
from __future__ import annotations

import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import urllib.error
import urllib.parse
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parents[1]
VERSIONS = json.loads((ROOT / "tool-versions.json").read_text())
TOOLS = ROOT / ".tools"
BUILD = ROOT / "build"
REPORTS = ROOT / "reports"


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def download(url: str, target: Path, expected: str | None = None,
             max_bytes: int = 1024 * 1024 * 1024) -> None:
    # Authentication material belongs neither in command lines nor CI inputs.
    def validate_url(value: str) -> None:
        parsed = urllib.parse.urlsplit(value)
        if (parsed.scheme != "https" or not parsed.hostname or parsed.username or
                parsed.password or parsed.fragment):
            raise ValueError("Use a credential-free HTTPS URL without a fragment.")
    validate_url(url)
    if target.exists() and expected and sha256(target) == expected:
        return

    class Redirects(urllib.request.HTTPRedirectHandler):
        def redirect_request(self, req, fp, code, msg, headers, newurl):
            validate_url(newurl)
            return super().redirect_request(req, fp, code, msg, headers, newurl)

    opener = urllib.request.build_opener(Redirects())
    request = urllib.request.Request(url, headers={"User-Agent": "FriendsOnly-PatchBuild/0.3"})
    target.parent.mkdir(parents=True, exist_ok=True)
    temporary = target.with_suffix(target.suffix + ".part")
    try:
        with opener.open(request, timeout=60) as response, temporary.open("wb") as output:
            count = 0
            while chunk := response.read(1024 * 1024):
                count += len(chunk)
                if count > max_bytes:
                    raise ValueError("Download exceeds maximum permitted file size.")
                output.write(chunk)
        if expected and sha256(temporary) != expected:
            raise ValueError("Download SHA-256 does not match the pinned digest.")
        temporary.replace(target)
    finally:
        temporary.unlink(missing_ok=True)


def run(args: list[str | Path], *, capture: bool = False, env: dict | None = None) -> str:
    # No shell and no command-line dumping: some tools receive signing-key paths.
    result = subprocess.run([str(arg) for arg in args], check=True, text=True,
                            stdout=subprocess.PIPE if capture else None,
                            stderr=subprocess.PIPE if capture else None, env=env)
    return result.stdout or ""


def java_tool(name: str) -> Path:
    java_home = os.environ.get("JAVA_HOME")
    candidate = Path(java_home) / "bin" / name if java_home else None
    found = candidate if candidate and candidate.is_file() else shutil.which(name)
    if not found:
        raise RuntimeError("Install JDK 21 and set JAVA_HOME (a JRE is insufficient).")
    return Path(found)


def android_paths() -> tuple[Path, Path]:
    sdk = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
    if not sdk:
        raise RuntimeError("Set ANDROID_HOME to an Android SDK with platform 35 and build-tools 35.0.0.")
    base = Path(sdk)
    android = base / "platforms" / f"android-{VERSIONS['android']['compile_sdk']}" / "android.jar"
    tools = base / "build-tools" / VERSIONS["android"]["build_tools"]
    if not android.is_file() or not (tools / "lib/d8.jar").is_file():
        raise RuntimeError("Missing Android SDK platform 35 or build-tools 35.0.0.")
    return android, tools


def bootstrap() -> tuple[Path, Path]:
    TOOLS.mkdir(parents=True, exist_ok=True)
    cli = TOOLS / f"morphe-desktop-{VERSIONS['morphe']['desktop_version']}-all.jar"
    download(VERSIONS["morphe"]["url"], cli, VERSIONS["morphe"]["sha256"])
    archive = TOOLS / f"kotlin-compiler-{VERSIONS['kotlin']['version']}.zip"
    download(VERSIONS["kotlin"]["url"], archive, VERSIONS["kotlin"]["sha256"])
    kotlin = TOOLS / f"kotlin-{VERSIONS['kotlin']['version']}"
    # Re-extract verified archive to avoid trusting cached compiler executables.
    if kotlin.exists():
        shutil.rmtree(kotlin)
    with zipfile.ZipFile(archive) as source:
        for info in source.infolist():
            path = Path(info.filename)
            if path.is_absolute() or ".." in path.parts:
                raise ValueError("Unsafe compiler archive path.")
        source.extractall(kotlin)
    compiler = kotlin / "kotlinc/bin/kotlinc"
    compiler.chmod(0o755)
    return cli, compiler


def write_report(path: Path, report: dict) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
