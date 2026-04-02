# Cloudflare `patches.json` worker

Goal:
- serve `https://revanced.kareem.one/patches.json`
- point ReVanced Manager at the latest GitHub release asset
- only trust releases authored by an allowed GitHub actor

## Expected release asset

This repo publishes:
- `patches-<version>-android.rvp`
- optionally `patches-<version>-android.rvp.asc`

## Worker behavior

The example worker:
- fetches the latest GitHub release for this repo
- rejects draft releases
- optionally rejects prereleases
- checks `release.author.login`
- finds the Android `.rvp` asset
- finds the optional `.asc`
- converts GitHub's timestamp into the `LocalDateTime` format ReVanced Manager accepts
- returns a Manager-compatible JSON document

## Recommended Worker env vars

- `GITHUB_OWNER = kareemlukitomo`
- `GITHUB_REPO = revanced-patches-kareem`
- `ALLOWED_GITHUB_ACTORS = kareemlukitomo,github-actions[bot]`
- `ALLOW_PRERELEASE = false`
- `REQUIRE_SIGNATURE = true`

With `REQUIRE_SIGNATURE = true`, the Worker serves the newest release that has both:
- `patches-<version>-android.rvp`
- `patches-<version>-android.rvp.asc`

## Deployment shape

Suggested route:
- `revanced.kareem.one/patches.json`

The Worker can be deployed separately from this repo. The important contract is just the JSON response shape.
