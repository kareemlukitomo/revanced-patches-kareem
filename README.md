# Kareem ReVanced Patches

Custom ReVanced patches published for use in ReVanced Manager.

Current scope:
- X / Twitter `10.86.0-release.0`
- Custom share domain patch for `nitter.kareem.one`
- Share-link sanitization
- Compatibility hotfix so the current upstream X hook bundle can coexist with these custom patches

## Current patches

The X patch set currently contains:
- `Change Twitter share domain`
- `Sanitize Twitter share links`
- `Z Fix Twitter JSON hook compatibility`

The third patch exists only to make the current upstream X ReVanced patch stack survive startup on `10.86.0-release.0`.
If upstream fixes its runtime/hook issue later, that patch should become a no-op.

## Build

This repo currently uses a minimal Kotlin/JVM build and then repacks an Android-loadable `.rvp` with `classes.dex`
for ReVanced Manager.

Local build:

```bash
JAVA_HOME=/path/to/jdk17 \
PATH=/path/to/jdk17/bin:$PATH \
./gradlew :patches:build \
  -PandroidBuildToolsDir=/path/to/android-sdk/build-tools/37.0.0 \
  --no-daemon
```

Important outputs:
- Plain JVM bundle: `patches/build/libs/patches-<version>.rvp`
- Manager-loadable Android bundle: `patches/build/libs/patches-<version>-android.rvp`

## ReVanced Manager

Manager should import a JSON source, not the raw `.rvp` directly.

Example source document:
- [manager-source.example.json](manager-source.example.json)

For local testing, serve a JSON file that points at the built `patches-<version>-android.rvp`.

## Release flow

The GitHub workflows are set up to:
- build `:patches`
- install Android build-tools `37.0.0`
- upload the Android-capable `patches-*-android.rvp` release asset
- optionally upload the matching `.asc` if signing is enabled later

Current caveat:
- the first real push should be treated as release-workflow validation

## Cloudflare Worker

If you want `https://revanced.kareem.one/patches.json`, the clean pattern is:
- GitHub Actions publishes `patches-<version>-android.rvp`
- a Cloudflare Worker fetches the latest GitHub release
- the Worker returns Manager-compatible JSON
- the Worker rejects releases not authored by the allowed GitHub actor

Example worker and notes:
- [docs/cloudflare-worker.md](docs/cloudflare-worker.md)
- [docs/patches-json-worker.example.js](docs/patches-json-worker.example.js)

## Signing

Release signing is optional right now.

If you later want signed artifacts:
- local signing with your YubiKey-backed OpenPGP subkey is the highest-trust path
- GitHub-hosted Actions cannot use your physical YubiKey directly
- CI signing would require a separately exported signing key in GitHub secrets, which is a different trust model

## Future apps

Placeholder directories already exist for future app-specific patches:
- `youtube`
- `reddit`
- `instagram`
- `tiktok`
- `threads`

## License

GPL-3.0, same as the upstream ReVanced patches ecosystem.
