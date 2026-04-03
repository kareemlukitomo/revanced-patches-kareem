# Cloudflare `patches.json` worker

This repo includes a Worker that exposes a ReVanced Manager source at:
- `https://revanced.kareem.one/patches.json`

## What the live worker does

The current JavaScript implementation has two entry paths:
- `revanced.kareem.one/patches.json` returns the Manager-compatible JSON body directly.
- `revanced.kareem.one/` and any other non-`/patches.json` path redirect to the repo homepage.
- The `workers.dev` hostname redirects back to `revanced.kareem.one`.

The lookup flow is:
- fetch the repo's public GitHub releases Atom feed
- parse releases in newest-first order
- ignore prereleases unless explicitly allowed
- ignore releases not authored by an allowed actor
- derive the expected asset names from the tag name
- optionally require a detached `.asc` signature
- normalize the timestamp to the format ReVanced Manager accepts
- cache the generated JSON for a short period

With `REQUIRE_SIGNATURE = true`, the Worker serves the newest release that has both:
- `patches-<version>-android.rvp`
- `patches-<version>-android.rvp.asc`

## Tracked config

The tracked worker config is intentionally public:
- `GITHUB_OWNER`
- `GITHUB_REPO`
- `ALLOWED_GITHUB_ACTORS`
- `ALLOW_PRERELEASE`
- `REQUIRE_SIGNATURE`
- `PRIMARY_HOST`
- `REPO_HOMEPAGE`

None of those values are secrets. They describe public repo metadata and routing policy.

## Sensitive data check

Nothing in the current worker project requires a force-push or history rewrite.

What is not committed:
- Cloudflare API tokens
- Cloudflare OAuth credentials
- GPG private keys
- GitHub access tokens

The public `workers.dev` hostname still exists, but it is only a canonical redirect target helper and is not the intended user-facing entry point.

## Cache behavior

The Worker currently returns:
- `Cache-Control: public, max-age=300`

That means clients and Cloudflare can reuse the generated JSON for about 5 minutes before refetching release metadata.

## Tracked files

- [wrangler.toml](../cloudflare/patches-json/wrangler.toml)
- [index.js](../cloudflare/patches-json/src/index.js)
