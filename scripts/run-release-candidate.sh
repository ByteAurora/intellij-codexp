#!/usr/bin/env bash
set -euo pipefail
repo_root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
rc_name="${1:-user}"
rc_fixture="${2:-legacy175}"
app_path="$(python3 "$repo_root/scripts/prepare-release-candidate.py" --name "$rc_name" --fixture "$rc_fixture" "${@:3}")"
exec open -a "$app_path" "$repo_root/build/release-candidate/$rc_name/CodeXP-RC-First"
