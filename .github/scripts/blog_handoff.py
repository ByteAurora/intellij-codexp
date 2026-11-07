"""Prepare a validated release identity for the blog; never publish a plugin."""

import argparse
import json
import os
import re
from pathlib import Path
from urllib.request import Request, urlopen

REPO = "ByteAurora/intellij-codexp"


def get(path):
    request = Request(
        f"https://api.github.com/repos/{REPO}/{path}",
        headers={
            "Accept": "application/vnd.github+json",
            "Authorization": f"Bearer {os.environ['GH_TOKEN']}",
            "User-Agent": "codexp-blog-handoff",
        },
    )
    with urlopen(request, timeout=30) as response:
        return json.load(response)


def identity(run, release):
    if not (
        run.get("event") == "release"
        and run.get("path") == ".github/workflows/release.yml"
        and run.get("head_repository", {}).get("full_name") == REPO
        and run.get("actor", {}).get("login") == "ByteAurora"
        and run.get("status") == "completed"
        and run.get("conclusion") == "success"
    ):
        raise ValueError("Not a successful maintainer release run")
    tag = release.get("tag_name", "")
    sha = release.get("target_commitish", "")
    if not (
        re.fullmatch(r"v(?:0|[1-9]\d*)\.(?:0|[1-9]\d*)\.(?:0|[1-9]\d*)", tag)
        and re.fullmatch(r"[0-9a-f]{40}", sha)
        and run.get("head_branch") == tag
        and run.get("head_sha") == sha
        and release.get("draft") is False
        and release.get("prerelease") is False
        and release.get("published_at")
        and isinstance(release.get("body"), str) and release["body"].strip()
        and type(release.get("id")) is int and release["id"] > 0
        and type(run.get("id")) is int and run["id"] > 0
    ):
        raise ValueError("Release identity does not match the workflow run")
    return {"release_id": release["id"], "tag": tag, "sha": sha, "run_id": run["id"]}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--run-id", required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    if not re.fullmatch(r"[1-9]\d*", args.run_id):
        raise ValueError("Invalid run ID")
    run = get(f"actions/runs/{args.run_id}")
    if run.get("id") != int(args.run_id):
        raise ValueError("Unexpected run ID")
    tag = run.get("head_branch", "")
    if not re.fullmatch(r"v\d+\.\d+\.\d+", tag):
        raise ValueError("Run does not identify a stable release tag")
    release = get(f"releases/tags/{tag}")
    payload = {"event_type": "codexp-release", "client_payload": identity(run, release)}
    args.output.write_text(json.dumps(payload) + "\n", encoding="utf-8")
    print(f"Validated {tag}; receiver will independently verify public availability")


if __name__ == "__main__":
    main()
