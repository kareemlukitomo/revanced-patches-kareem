export default {
  async fetch(request, env) {
    const owner = env.GITHUB_OWNER || "kareemlukitomo";
    const repo = env.GITHUB_REPO || "revanced-patches-kareem";
    const allowedActor = env.ALLOWED_GITHUB_ACTOR || "kareemlukitomo";
    const allowPrerelease = String(env.ALLOW_PRERELEASE || "false") === "true";

    const releaseUrl = `https://api.github.com/repos/${owner}/${repo}/releases/latest`;
    const response = await fetch(releaseUrl, {
      headers: {
        "Accept": "application/vnd.github+json",
        "User-Agent": "revanced-kareem-patches-worker",
      },
    });

    if (!response.ok) {
      return new Response(`GitHub release lookup failed: ${response.status}`, { status: 502 });
    }

    const release = await response.json();

    if (release.draft) {
      return new Response("Latest release is still a draft.", { status: 503 });
    }

    if (release.prerelease && !allowPrerelease) {
      return new Response("Latest release is a prerelease and prereleases are not allowed.", { status: 503 });
    }

    if (release.author?.login !== allowedActor) {
      return new Response("Release author is not allowed.", { status: 403 });
    }

    const patchAsset = release.assets?.find((asset) =>
      /^patches-.*-android\.rvp$/.test(asset.name),
    );
    if (!patchAsset) {
      return new Response("Could not find Android .rvp asset in latest release.", { status: 503 });
    }

    const signatureAsset = release.assets?.find((asset) =>
      asset.name === `${patchAsset.name}.asc`,
    );

    const publishedAt = release.published_at || release.created_at;
    const createdAt = normalizeManagerDateTime(publishedAt);

    const body = {
      version: release.tag_name,
      created_at: createdAt,
      description: release.name || `Kareem ReVanced patches ${release.tag_name}`,
      download_url: patchAsset.browser_download_url,
      signature_download_url: signatureAsset?.browser_download_url ?? null,
    };

    return Response.json(body, {
      headers: {
        "Cache-Control": "public, max-age=300",
      },
    });
  },
};

function normalizeManagerDateTime(value) {
  if (!value) return "1970-01-01T00:00:00";
  return String(value)
    .replace(/\.\d+Z$/, "")
    .replace(/Z$/, "")
    .replace(/[+-]\d\d:\d\d$/, "");
}
