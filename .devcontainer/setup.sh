#!/usr/bin/env bash
set -euo pipefail

cd /workspaces/"$(basename "$PWD")" || exit 1

npm install -g aws-cdk

if ! find . -maxdepth 2 \( -name "pom.xml" -o -name "build.gradle" -o -name "build.gradle.kts" \) | grep -q .; then
  cdk init app --language java
fi
