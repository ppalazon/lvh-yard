// Prints what a release depends on, so you can see what is left before you tag: the last tag, CI on HEAD,
// the live site, the release the installers download, the visibility of the image, and the open tickets.
// It reports and never fails. The tag itself is gated by the `ci-gate` job of .github/workflows/release.yml.
import { execFileSync } from "node:child_process";
import { readdirSync, readFileSync } from "node:fs";

const SITE = "https://lvhy.cc";
const IMAGE = "lvh-yard-dashboard";

const run = (cmd, ...args) => {
  try {
    return execFileSync(cmd, args, {
      encoding: "utf8",
      stdio: ["ignore", "pipe", "ignore"],
    }).trim();
  } catch {
    return null;
  }
};

const remote = run("git", "remote", "get-url", "origin") ?? "";
const slug = remote.match(/github\.com[:/](.+?)(?:\.git)?$/)?.[1];
if (!slug) {
  console.error("origin is not a GitHub remote");
  process.exit(1);
}
const owner = slug.split("/")[0].toLowerCase();

function section(title, lines) {
  console.log(`\n${title}`);
  console.log(lines.map((line) => `  ${line}`).join("\n"));
}

async function status(url) {
  try {
    const response = await fetch(url, { redirect: "follow" });
    return String(response.status);
  } catch (error) {
    return `error (${error.cause?.code ?? error.message})`;
  }
}

// Git
const head = run("git", "rev-parse", "HEAD");
const tag = run("git", "describe", "--tags", "--abbrev=0");
const since = tag ? run("git", "rev-list", "--count", `${tag}..HEAD`) : null;
const unpushed = run("git", "rev-list", "--count", "@{u}..HEAD");
const dirty = run("git", "status", "--porcelain") ?? "";
section("Git", [
  tag ? `last tag ${tag}, ${since} commits behind HEAD` : "no tag yet",
  `HEAD ${head?.slice(0, 7)}: ${unpushed === "0" ? "pushed" : `${unpushed ?? "?"} commits not pushed`}, ${dirty ? "uncommitted changes" : "clean"}`,
]);

// CI on HEAD
const runsJson = run(
  "gh",
  "run",
  "list",
  "--repo",
  slug,
  "--workflow",
  "ci.yml",
  "--commit",
  head,
  "--json",
  "status,conclusion,url",
);
const runs = runsJson ? JSON.parse(runsJson) : null;
section(
  "CI on HEAD",
  runs === null
    ? ["unknown: gh is missing or not logged in"]
    : runs.length === 0
      ? ["no CI run for HEAD: push it first"]
      : runs.map((r) => `${r.conclusion || r.status}  ${r.url}`),
);

// Live site and release assets
const urls = [
  `${SITE}/`,
  `${SITE}/docs/`,
  `${SITE}/install.sh`,
  `${SITE}/install.ps1`,
  `https://github.com/${slug}/releases/latest/download/SHA256SUMS`,
];
const codes = await Promise.all(urls.map(status));
section(
  "Live (the last line is what the installers and `lvh-yard update` read: 404 means no stable release)",
  urls.map((url, i) => `${codes[i]}  ${url}`),
);

// Image visibility: an anonymous pull token only works for a public package.
const tokenResponse = await fetch(
  `https://ghcr.io/token?scope=repository:${owner}/${IMAGE}:pull`,
).catch(() => null);
let image = "unknown: ghcr.io did not answer";
if (tokenResponse?.ok) {
  const { token } = await tokenResponse.json();
  const tags = await fetch(`https://ghcr.io/v2/${owner}/${IMAGE}/tags/list`, {
    headers: { Authorization: `Bearer ${token}` },
  }).catch(() => null);
  if (tags?.ok) {
    image = `public, tags ${(await tags.json()).tags?.join(", ") ?? "none"}`;
  } else if (tags) {
    image = `not public (${tags.status}): set it public in the package settings`;
  }
}
section(`Image ghcr.io/${owner}/${IMAGE}`, [image]);

// Tickets that are not done
const open = [];
for (const feature of readdirSync(".scratch", { withFileTypes: true })) {
  if (!feature.isDirectory()) continue;
  const dir = `.scratch/${feature.name}/issues`;
  let files;
  try {
    files = readdirSync(dir)
      .filter((f) => f.endsWith(".md"))
      .sort();
  } catch {
    continue;
  }
  for (const file of files) {
    const text = readFileSync(`${dir}/${file}`, "utf8");
    const state = text.match(/^\**Status:\**\s*(\S+)/m)?.[1];
    if (state && state !== "done") {
      const core = /^\**Core for v[\d.]+:\**\s*yes/im.test(text)
        ? " [core]"
        : "";
      open.push(`${state}${core}  ${dir}/${file}`);
    }
  }
}
section("Tickets not done", open.length ? open : ["none"]);
