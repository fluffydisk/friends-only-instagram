#!/usr/bin/env python3
"""Download a manually supplied base. Provenance is checked separately by apply.py."""
import argparse
import sys
from pathlib import Path
from common import download

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--url", required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    try:
        download(args.url, args.output)
    except Exception:
        # URL errors may contain private signed query parameters. Never echo them.
        print("Download failed. Check the HTTPS URL, network access and size limit.", file=sys.stderr)
        sys.exit(1)
