#!/usr/bin/env python3
"""Return the Docker Hub build matrix for changed ReleasePilot inputs."""

import argparse
import json
from pathlib import Path


SERVICES = {
    "control-plane": "apps/control-plane",
    "analysis-worker": "apps/analysis-worker",
    "web-console": "apps/web-console",
}
GLOBAL_INPUTS = {
    ".github/workflows/ci.yml",
    ".github/workflows/dockerhub-cd.yml",
    "scripts/select_dockerhub_images.py",
    "scripts/update_releasepilot_images.py",
}


def select(paths: set[str], select_all: bool = False) -> dict[str, list[dict[str, str]]]:
    candidates = [{"name": name, "context": context} for name, context in SERVICES.items()]
    if select_all or paths & GLOBAL_INPUTS:
        return {"include": candidates}
    selected = [
        item
        for item in candidates
        if any(path == item["context"] or path.startswith(item["context"] + "/") for path in paths)
    ]
    if not selected:
        raise ValueError("triggered Docker Hub CD has no image input changes")
    return {"include": selected}


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("changed_paths", nargs="?", type=Path)
    parser.add_argument("--all", action="store_true")
    args = parser.parse_args()
    if not args.all and args.changed_paths is None:
        parser.error("CHANGED_PATHS or --all is required")
    paths = set() if args.changed_paths is None else set(args.changed_paths.read_text().splitlines())
    print(json.dumps(select(paths, args.all), separators=(",", ":")))


if __name__ == "__main__":
    main()
