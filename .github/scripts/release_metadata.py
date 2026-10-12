"""Validate release identity before running Gradle or accessing publishing secrets."""

import argparse
import os
from pathlib import Path
import re
import subprocess


SHA = re.compile(r"[0-9a-f]{40}")
VERSION = re.compile(
    r"(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)"
    r"(?:-[0-9A-Za-z-]+(?:\.[0-9A-Za-z-]+)*)?"
    r"(?:\+[0-9A-Za-z-]+(?:\.[0-9A-Za-z-]+)*)?"
)


def git(repo, *args):
    return subprocess.check_output(
        ["git", "-C", str(repo), *args], text=True, stderr=subprocess.PIPE
    ).strip()


def metadata(repo, ref):
    if ref != "main" and not SHA.fullmatch(ref):
        raise ValueError("Select main or a full commit SHA, not another branch or tag.")
    selected = "refs/remotes/origin/main" if ref == "main" else ref
    sha = git(repo, "rev-parse", "--verify", "--end-of-options", selected + "^{commit}")
    subprocess.run(
        ["git", "-C", str(repo), "merge-base", "--is-ancestor", sha, "refs/remotes/origin/main"],
        check=True,
        capture_output=True,
        text=True,
    )
    properties = git(repo, "show", sha + ":gradle.properties")
    versions = re.findall(r"^pluginVersion\s*=\s*(\S+)\s*$", properties, re.MULTILINE)
    if len(versions) != 1 or not VERSION.fullmatch(versions[0]):
        raise ValueError("gradle.properties must define one valid pluginVersion.")
    version = versions[0]
    tag = "v" + version
    existing = git(repo, "tag", "--list", tag)
    if existing and git(repo, "rev-parse", "refs/tags/" + tag + "^{commit}") != sha:
        raise ValueError("The version tag already points to another commit; do not move it.")
    return {"sha": sha, "version": version, "tag": tag}


def validate_release(repo, tag, sha, target):
    if not SHA.fullmatch(sha) or target != sha:
        raise ValueError("The release target must be the full, immutable event commit SHA.")
    result = metadata(repo, sha)
    if tag != result["tag"]:
        raise ValueError("The release tag must equal v + pluginVersion.")
    if git(repo, "rev-parse", "--verify", "refs/tags/" + tag + "^{commit}") != sha:
        raise ValueError("The release tag has moved since the event was created.")
    if git(repo, "rev-parse", "HEAD") != sha:
        raise ValueError("The checkout does not match the release commit.")
    return result


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("mode", choices=["draft", "release"])
    parser.add_argument("--ref", default="main")
    parser.add_argument("--tag", default="")
    parser.add_argument("--sha", default="")
    parser.add_argument("--target", default="")
    args = parser.parse_args()
    try:
        if args.mode == "draft":
            result = metadata(Path.cwd(), args.ref)
        else:
            result = validate_release(Path.cwd(), args.tag, args.sha, args.target)
    except (ValueError, subprocess.CalledProcessError) as error:
        parser.exit(1, f"Release validation failed: {error}\n")
    output = "".join(f"{key}={value}\n" for key, value in result.items())
    print(output, end="")
    if os.environ.get("GITHUB_OUTPUT"):
        with open(os.environ["GITHUB_OUTPUT"], "a", encoding="utf-8") as stream:
            stream.write(output)


if __name__ == "__main__":
    main()
