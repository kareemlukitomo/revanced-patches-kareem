import { execFileSync } from "node:child_process";
import { mkdirSync, readFileSync, readdirSync, rmSync, writeFileSync } from "node:fs";
import path from "node:path";
import process from "node:process";

const nextVersion = process.argv[2];

if (!nextVersion) {
  console.error("Usage: node ./scripts/prepare-release.mjs <next-version>");
  process.exit(1);
}

const repoRoot = process.cwd();
const gradlePropertiesPath = path.join(repoRoot, "gradle.properties");
const buildToolsDir = process.env.ANDROID_BUILD_TOOLS_DIR;

if (!buildToolsDir) {
  console.error("ANDROID_BUILD_TOOLS_DIR must be set for release builds.");
  process.exit(1);
}

const gradleProperties = readFileSync(gradlePropertiesPath, "utf8");
const versionPattern = /^version\s*=.*$/m;
if (!versionPattern.test(gradleProperties)) {
  console.error("Could not find version in gradle.properties.");
  process.exit(1);
}

const updatedGradleProperties = gradleProperties.replace(versionPattern, `version = ${nextVersion}`);

writeFileSync(gradlePropertiesPath, updatedGradleProperties);

const libsDir = path.join(repoRoot, "patches", "build", "libs");
mkdirSync(libsDir, { recursive: true });
for (const entry of readdirSync(libsDir)) {
  if (entry.startsWith("patches-") && (entry.endsWith(".rvp") || entry.endsWith(".asc"))) {
    rmSync(path.join(libsDir, entry), { force: true });
  }
}

execFileSync(
  path.join(repoRoot, "gradlew"),
  [":patches:build", "--no-daemon", `-PandroidBuildToolsDir=${buildToolsDir}`],
  {
    stdio: "inherit",
    cwd: repoRoot,
    env: process.env,
  },
);

const releaseArtifactPath = path.join(libsDir, `patches-${nextVersion}-android.rvp`);
try {
  readFileSync(releaseArtifactPath);
} catch {
  console.error(`Expected release artifact was not created: ${releaseArtifactPath}`);
  process.exit(1);
}
