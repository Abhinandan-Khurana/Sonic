#!/usr/bin/env bash
# Enable the GitHub Actions workflow for this repository.
#
# The workflow lives under .github/workflows-templates/ because adding a file to
# .github/workflows/ needs a GitHub login with the separate `workflow` scope. A
# repository-write token is not enough: GitHub refuses the push outright.
#
# This script copies the template into place and prints the one command left to run.

set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
template="$root/.github/workflows-templates/ci.yml"
target_dir="$root/.github/workflows"
target="$target_dir/ci.yml"

if [ ! -f "$template" ]; then
  echo "No template at $template" >&2
  exit 1
fi

if [ -f "$target" ]; then
  echo "$target already exists; nothing to do."
  exit 0
fi

mkdir -p "$target_dir"
cp "$template" "$target"

cat <<EOF

Copied the CI workflow to .github/workflows/ci.yml.

Now push it with a login that has the 'workflow' scope, for example:

  gh auth refresh -s workflow
  git add .github/workflows/ci.yml
  git commit -m "ci: add the Android and Soulseek workflow"
  git push origin main

The workflow builds the native libraries, runs the native and JVM tests, runs
lint, and uploads the reports and APKs as artifacts. It does not sign or
publish releases.
EOF
