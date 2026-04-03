#!/usr/bin/env bash
set -euo pipefail

if [[ $# -lt 1 ]]; then
  echo "usage: $0 <base-url> [artifact-dir]" >&2
  exit 1
fi

base_url="${1%/}"
artifact_dir="${2:-patches/build/libs}"

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
if [[ "$artifact_dir" != /* ]]; then
  artifact_dir="$repo_root/$artifact_dir"
fi
artifact_path="$(find "$artifact_dir" -maxdepth 1 -type f -name 'patches-*-android.rvp' -printf '%T@ %p\n' | sort -nr | awk 'NR==1 { print $2 }')"

if [[ -z "${artifact_path:-}" ]]; then
  echo "no Android .rvp artifact found in $artifact_dir" >&2
  exit 1
fi

artifact_name="$(basename "$artifact_path")"
version="${artifact_name#patches-}"
version="${version%-android.rvp}"
output_path="$artifact_dir/patches.json"
signature_name="${artifact_name}.asc"
signature_download_url="null"

if [[ -f "$artifact_dir/$signature_name" ]]; then
  signature_download_url="\"$base_url/$signature_name\""
fi

cat >"$output_path" <<EOF
{
  "version": "v$version",
  "created_at": "$(date -u +%Y-%m-%dT%H:%M:%S)",
  "description": "Kareem ReVanced patches.",
  "download_url": "$base_url/$artifact_name",
  "signature_download_url": $signature_download_url
}
EOF

echo "$output_path"
