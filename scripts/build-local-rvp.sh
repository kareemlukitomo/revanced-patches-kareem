#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
JAVA_HOME_DEFAULT="/workspace/personal/adb/jdk/extracted"
ANDROID_BUILD_TOOLS_DIR_DEFAULT="/workspace/personal/adb/google-build-tools/extracted/android-37.0"

export JAVA_HOME="${JAVA_HOME:-$JAVA_HOME_DEFAULT}"
export PATH="$JAVA_HOME/bin:$PATH"

ANDROID_BUILD_TOOLS_DIR="${ANDROID_BUILD_TOOLS_DIR:-$ANDROID_BUILD_TOOLS_DIR_DEFAULT}"
BASE_VERSION="$(sed -n 's/^version = //p' "$ROOT_DIR/gradle.properties" | tail -n 1)"
BASE_VERSION="${BASE_VERSION:-0.0.0}"
LOCAL_VERSION="${LOCAL_VERSION:-$(date +%Y.%-m.%-d.%H%M)}"

cd "$ROOT_DIR"
./gradlew :patches:build \
  -PandroidBuildToolsDir="$ANDROID_BUILD_TOOLS_DIR" \
  -Pversion="$LOCAL_VERSION" \
  --no-daemon 1>&2

printf '%s\n' "$ROOT_DIR/patches/build/libs/patches-$LOCAL_VERSION-android.rvp"
