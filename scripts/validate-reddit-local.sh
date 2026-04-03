#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
JAVA_HOME_DEFAULT="/workspace/personal/adb/jdk/extracted"
BUILD_TOOLS_DIR_DEFAULT="/workspace/personal/adb/google-build-tools/extracted/android-37.0"
CLI_JAR_DEFAULT="$ROOT_DIR/patches/build/downloads/revanced-cli-6.0.0-all.jar"

export JAVA_HOME="${JAVA_HOME:-$JAVA_HOME_DEFAULT}"
export PATH="$JAVA_HOME/bin:$PATH"

BUILD_TOOLS_DIR="${BUILD_TOOLS_DIR:-$BUILD_TOOLS_DIR_DEFAULT}"
APKSIGNER="${APKSIGNER:-$BUILD_TOOLS_DIR/apksigner}"
CLI_JAR="${CLI_JAR:-$CLI_JAR_DEFAULT}"
CUSTOM_PATCHES="${CUSTOM_PATCHES:-}"

if [[ -z "$CUSTOM_PATCHES" ]]; then
    CUSTOM_PATCHES="$(ls -t "$ROOT_DIR"/patches/build/libs/patches-*-android.rvp 2>/dev/null | head -n 1)"
fi

if [[ -z "$CUSTOM_PATCHES" || ! -f "$CUSTOM_PATCHES" ]]; then
    printf 'Missing custom Android bundle: %s\n' "$CUSTOM_PATCHES" >&2
    exit 1
fi

if [[ ! -x "$APKSIGNER" ]]; then
    printf 'Missing apksigner: %s\n' "$APKSIGNER" >&2
    exit 1
fi

if [[ ! -f "$CLI_JAR" ]]; then
    printf 'Missing CLI jar: %s\n' "$CLI_JAR" >&2
    exit 1
fi

printf 'bundle=%s\n' "$CUSTOM_PATCHES"
java -jar "$CLI_JAR" list-patches \
    -p "$CUSTOM_PATCHES" -b \
    --filter-package-name=com.reddit.frontpage \
    --descriptions --index

OUTPUT_APK="$(
    CUSTOM_PATCHES="$CUSTOM_PATCHES" \
    "$ROOT_DIR/scripts/patch-reddit-local.sh"
)"
OUTPUT_APK="$(printf '%s\n' "$OUTPUT_APK" | tail -n 1)"

printf 'apk=%s\n' "$OUTPUT_APK"
"$APKSIGNER" verify -v "$OUTPUT_APK"

unzip -p "$OUTPUT_APK" 'classes*.dex' | strings | rg -n \
    'https://redlib\.kareem\.one(/r/|/u/|/user/|%s|/)?'

printf 'validated=true\n'
