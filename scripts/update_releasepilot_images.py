#!/usr/bin/env python3
"""Pin the three ReleasePilot images in a Kustomize overlay by digest."""

from pathlib import Path
import re
import sys


def main() -> None:
    if len(sys.argv) != 3:
        raise SystemExit("usage: update_releasepilot_images.py DIGESTS OVERLAY")

    digest_file, overlay_file = map(Path, sys.argv[1:])
    digests = {}
    for line in digest_file.read_text(encoding="utf-8").splitlines():
        name, image = line.strip().split("=", 1)
        repository, digest = image.rsplit("@", 1)
        if not re.fullmatch(r"sha256:[0-9a-f]{64}", digest):
            raise SystemExit(f"invalid digest for {name}: {digest}")
        digests[name] = (repository, digest)

    required = {"control-plane", "analysis-worker", "web-console"}
    if set(digests) != required:
        raise SystemExit(f"digest set must be exactly {sorted(required)}")

    text = overlay_file.read_text(encoding="utf-8")
    for name, (repository, digest) in digests.items():
        pattern = rf"(?ms)(-\s+name:\s*{re.escape(name)}\s*\n\s+newName:)\s*\S+(\s*\n\s+digest:)\s*\S+"
        text, count = re.subn(pattern, rf"\1 {repository}\2 {digest}", text)
        if count != 1:
            raise SystemExit(f"expected one Kustomize image entry for {name}, found {count}")
    overlay_file.write_text(text, encoding="utf-8")


if __name__ == "__main__":
    main()
