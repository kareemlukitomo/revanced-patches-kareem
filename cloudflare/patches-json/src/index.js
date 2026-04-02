export default {
  async fetch(request, env, ctx) {
    const primaryHost = env.PRIMARY_HOST || "";
    const requestUrl = new URL(request.url);
    if (primaryHost && requestUrl.hostname !== primaryHost) {
      const redirectUrl = new URL(requestUrl.pathname + requestUrl.search, `https://${primaryHost}`);
      return Response.redirect(redirectUrl.toString(), 302);
    }

    const config = {
      owner: env.GITHUB_OWNER || "kareemlukitomo",
      repo: env.GITHUB_REPO || "revanced-patches-kareem",
      allowedActors: parseAllowedActors(env.ALLOWED_GITHUB_ACTORS || env.ALLOWED_GITHUB_ACTOR || "kareemlukitomo"),
      allowPrerelease: String(env.ALLOW_PRERELEASE || "false") === "true",
    };

    const cache = caches.default;
    const cacheKey = new Request("https://revanced.kareem.one/patches.json");
    const cached = await cache.match(cacheKey);
    if (cached) return cached;

    const release = await findRelease(config);
    if (!release.ok) {
      return text(release.message, release.status);
    }

    const body = JSON.stringify(buildManagerSource(release.value), null, 2);
    const response = new Response(body, {
      headers: {
        "content-type": "application/json; charset=utf-8",
        "cache-control": "public, max-age=300",
      },
    });
    ctx.waitUntil(cache.put(cacheKey, response.clone()));
    return response;
  },
};

async function findRelease(config) {
  const url = `https://github.com/${config.owner}/${config.repo}/releases.atom`;
  const response = await fetch(url, {
    headers: {
      Accept: "application/atom+xml, application/xml;q=0.9, text/xml;q=0.8",
      "User-Agent": "revanced-kareem-patches-worker",
    },
  });

  if (!response.ok) {
    return {
      ok: false,
      status: 502,
      message: `GitHub release lookup failed: ${response.status}`,
    };
  }

  const feedText = await response.text();
  const releases = parseReleaseFeed(feedText, config);
  for (const release of releases) {
    if (release.prerelease && !config.allowPrerelease) continue;
    if (!config.allowedActors.has(release.author)) continue;

    return {
      ok: true,
      value: {
        ...release,
        signatureExists: await hasAsset(release.signatureUrl),
      },
    };
  }

  return {
    ok: false,
    status: 503,
    message: "Could not find a release with an Android .rvp asset that matches the worker policy.",
  };
}

function buildManagerSource(release) {
  return {
    version: release.tag_name,
    created_at: normalizeManagerDateTime(release.published_at),
    description: release.name || `Kareem ReVanced patches ${release.tag_name}`,
    download_url: release.downloadUrl,
    signature_download_url: release.signatureExists ? release.signatureUrl : null,
  };
}

function normalizeManagerDateTime(value) {
  if (!value) return "1970-01-01T00:00:00";
  return String(value)
    .replace(/\.\d+Z$/, "")
    .replace(/Z$/, "")
    .replace(/[+-]\d\d:\d\d$/, "");
}

function text(message, status) {
  return new Response(message, {
    status,
    headers: {
      "content-type": "text/plain; charset=utf-8",
      "cache-control": "no-store",
    },
  });
}

function parseAllowedActors(value) {
  return new Set(
    String(value)
      .split(",")
      .map((entry) => entry.trim())
      .filter(Boolean),
  );
}

function parseReleaseFeed(feedText, config) {
  const entries = [...feedText.matchAll(/<entry>([\s\S]*?)<\/entry>/g)];
  return entries.map((match) => {
    const entry = match[1];
    const tagName = decodeXml(extract(entry, "title"));
    const author = decodeXml(extract(entry, "author").match(/<name>([\s\S]*?)<\/name>/)?.[1] || "");
    const publishedAt = extract(entry, "updated");
    const version = tagName.replace(/^v/, "");
    const baseUrl = `https://github.com/${config.owner}/${config.repo}/releases/download/${tagName}`;
    const patchName = `patches-${version}-android.rvp`;

    return {
      tag_name: tagName,
      prerelease: /-/.test(version),
      author,
      published_at: publishedAt,
      name: `Kareem ReVanced patches ${tagName}`,
      downloadUrl: `${baseUrl}/${patchName}`,
      signatureUrl: `${baseUrl}/${patchName}.asc`,
    };
  });
}

function extract(entry, tag) {
  return entry.match(new RegExp(`<${tag}[^>]*>([\\s\\S]*?)<\\/${tag}>`))?.[1] || "";
}

async function hasAsset(url) {
  try {
    const response = await fetch(url, {
      method: "HEAD",
      headers: {
        "User-Agent": "revanced-kareem-patches-worker",
      },
    });
    return response.ok;
  } catch {
    return false;
  }
}

function decodeXml(value) {
  return String(value)
    .replace(/&lt;/g, "<")
    .replace(/&gt;/g, ">")
    .replace(/&amp;/g, "&")
    .replace(/&quot;/g, "\"")
    .replace(/&#39;/g, "'");
}
