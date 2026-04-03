#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
JAVA_HOME_DEFAULT="/workspace/personal/adb/jdk/extracted"
CLI_JAR_DEFAULT="$ROOT_DIR/patches/build/downloads/revanced-cli-6.0.0-all.jar"
UPSTREAM_PATCHES_DEFAULT="/workspace/personal/adb/upstream/patches.rvp"
INPUT_APK_DEFAULT="/workspace/personal/adb/local-repro/reddit-2026.13.0-unsplit.apk"
OUTPUT_DIR_DEFAULT="/workspace/personal/adb/local-repro"
TEMP_DIR_DEFAULT=""

export JAVA_HOME="${JAVA_HOME:-$JAVA_HOME_DEFAULT}"
export PATH="$JAVA_HOME/bin:$PATH"

CLI_JAR="${CLI_JAR:-$CLI_JAR_DEFAULT}"
UPSTREAM_PATCHES="${UPSTREAM_PATCHES:-$UPSTREAM_PATCHES_DEFAULT}"
INPUT_APK="${INPUT_APK:-$INPUT_APK_DEFAULT}"
OUTPUT_DIR="${OUTPUT_DIR:-$OUTPUT_DIR_DEFAULT}"
TEMP_DIR="${TEMP_DIR:-$TEMP_DIR_DEFAULT}"

latest_custom_bundle() {
    ls -t "$ROOT_DIR"/patches/build/libs/patches-*-android.rvp 2>/dev/null | head -n 1
}

CUSTOM_PATCHES="${CUSTOM_PATCHES:-$(latest_custom_bundle)}"
PATCH_VERSION="${PATCH_VERSION:-$(basename "$CUSTOM_PATCHES" .rvp | sed 's/^patches-//; s/-android$//')}"
OUTPUT_APK="${OUTPUT_APK:-$OUTPUT_DIR/reddit-$PATCH_VERSION-local.apk}"

disabled_indexes=(135 159 161)
disable_args=()
for index in "${disabled_indexes[@]}"; do
    disable_args+=(--di="$index")
done

mkdir -p "$OUTPUT_DIR"
cleanup_temp_dir=false
if [[ -z "$TEMP_DIR" ]]; then
    TEMP_DIR="$(mktemp -d "$OUTPUT_DIR/tmp-reddit-cli.XXXXXX")"
    cleanup_temp_dir=true
else
    rm -rf "$TEMP_DIR"
    mkdir -p "$TEMP_DIR"
fi

if [[ "$cleanup_temp_dir" == true ]]; then
    trap 'rm -rf "$TEMP_DIR"' EXIT
fi

for path in "$CLI_JAR" "$UPSTREAM_PATCHES" "$CUSTOM_PATCHES" "$INPUT_APK"; do
    if [[ ! -f "$path" ]]; then
        printf 'Missing required file: %s\n' "$path" >&2
        exit 1
    fi
done

java -jar "$CLI_JAR" patch \
    -f \
    -p "$UPSTREAM_PATCHES" -b \
    -p "$CUSTOM_PATCHES" -b \
    "${disable_args[@]}" \
    -t "$TEMP_DIR" \
    -o "$OUTPUT_APK" \
    "$INPUT_APK"

printf '%s\n' "$OUTPUT_APK"
