# Kareem ReVanced Patches

Custom ReVanced patches for apps that need small targeted fixes beyond the upstream bundle.

## Supported app

Current public support:
- X / Twitter `10.86.0-release.0`

Current patch set:
- `Change Twitter share domain`
- `Sanitize Twitter share links`
- `Z Fix Twitter JSON hook compatibility`

The compatibility patch exists so the upstream X patch stack can coexist with the custom share-link patches on this app version.

## Use In ReVanced Manager

Import this source in ReVanced Manager:
- `https://revanced.kareem.one/patches.json`

Notes:
- The source endpoint serves the latest signed release only.
- Use a stock supported APK as patch input, not an already-patched APK.
- Your custom source can be enabled alongside upstream ReVanced patches.

## Build From Source

Requirements:
- JDK 17
- Android build-tools `37.0.0`

Example build:

```bash
JAVA_HOME=/path/to/jdk17 \
PATH=/path/to/jdk17/bin:$PATH \
./gradlew :patches:build \
  -PandroidBuildToolsDir=/path/to/android-sdk/build-tools/37.0.0 \
  --no-daemon
```

Artifacts:
- `patches/build/libs/patches-<version>.rvp`
- `patches/build/libs/patches-<version>-android.rvp`

## Signed Releases

Published releases use detached signatures:
- patch bundle: `patches-<version>-android.rvp`
- signature: `patches-<version>-android.rvp.asc`

The public Manager endpoint will ignore newer unsigned releases until the matching `.asc` is uploaded.

## Cloudflare Worker

The repo includes a Worker that turns GitHub releases into a Manager-compatible `patches.json` source:
- [docs/cloudflare-worker.md](docs/cloudflare-worker.md)
- [cloudflare/patches-json/wrangler.toml](cloudflare/patches-json/wrangler.toml)
- [cloudflare/patches-json/src/index.js](cloudflare/patches-json/src/index.js)

## Future Scope

Additional app namespaces already exist in the tree for future patches:
- `youtube`
- `reddit`
- `instagram`
- `tiktok`
- `threads`

## License

GPL-3.0, aligned with the upstream ReVanced patches ecosystem.
