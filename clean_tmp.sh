#!/usr/bin/env bash
# Wipes and recreates the repo-local tmp/ scratch dir (build logs, throwaway verification
# artifacts, etc.). Safe to run any time; nothing under tmp/ is meant to be kept.
set -euo pipefail
cd "$(dirname "$0")"

rm -rf tmp
mkdir -p tmp
echo "tmp/ cleaned."
