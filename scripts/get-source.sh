#!/usr/bin/env bash
# Fetch the pinned Firefox release source into ./src (shallow, single tag).
set -euo pipefail
cd "$(dirname "$0")/.."
source config.env

if [ -d src/.git ]; then
  echo "src/ already exists; delete it to re-fetch." >&2
  exit 0
fi

git init -q src
git -C src remote add origin "$FIREFOX_REPO"
git -C src fetch --depth 50 origin "refs/tags/${FIREFOX_TAG}:refs/tags/${FIREFOX_TAG}"
git -C src checkout -q "$FIREFOX_TAG"
echo "Fetched $FIREFOX_TAG into src/"
