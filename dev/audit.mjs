// Prints the measures of a consolidation audit, so two runs can be compared.
// Each list is a lead to read, not a verdict: a name can be missing from the code
// because a learning records why it was ruled out.
import { execFileSync, spawnSync } from "node:child_process";
import { existsSync, readFileSync } from "node:fs";

const LEARNINGS = ".trail/learnings/";
const LONG_LEARNING = 300;
const LARGE_TEST_FILE = 800;

const git = (...args) =>
  execFileSync("git", args, { encoding: "utf8", maxBuffer: 1 << 26 });
const tracked = git("ls-files").split("\n").filter(Boolean);
const words = (text) => text.split(/\s+/).filter(Boolean).length;

function section(title, lines) {
  console.log(`\n${title}`);
  console.log(
    lines.length ? lines.map((line) => `  ${line}`).join("\n") : "  none",
  );
}

const learnings = tracked
  .filter((path) => path.startsWith(LEARNINGS) && path.endsWith(".md"))
  .map((path) => {
    const text = readFileSync(path, "utf8");
    const front = text.split(/^---$/m)[1] ?? "";
    const field = (key) =>
      front.match(new RegExp(`^${key}:\\s*(.*)$`, "m"))?.[1].trim() ?? "";
    const touchesBlock = front.split(/^touches:\s*$/m)[1] ?? "";
    const touches = [
      ...touchesBlock.matchAll(/^\s+-\s+"?([^"\s]+)"?\s*$/gm),
    ].map((m) => m[1]);
    return {
      path,
      name: path.slice(LEARNINGS.length),
      text,
      id: field("id"),
      replaces: field("replaces"),
      touches,
    };
  });

const byId = new Map(learnings.map((learning) => [learning.id, learning]));
const totalWords = learnings.reduce(
  (sum, learning) => sum + words(learning.text),
  0,
);

console.log("Consolidation audit");
console.log(`\nLearnings: ${learnings.length} files, ${totalWords} words`);

section(
  `Learnings over ${LONG_LEARNING} words`,
  learnings
    .filter((learning) => words(learning.text) > LONG_LEARNING)
    .map((learning) => `${words(learning.text)}  ${learning.name}`),
);

section(
  "Learnings that a newer learning on disk replaces (delete the old one)",
  learnings
    .filter((learning) => byId.has(learning.replaces))
    .map(
      (learning) =>
        `${byId.get(learning.replaces).name}  replaced by  ${learning.name}`,
    ),
);

section(
  "Learnings that touch files that no longer exist",
  learnings
    .map((learning) => [
      learning,
      learning.touches.filter((path) => !existsSync(path.split("#")[0])),
    ])
    .filter(([, missing]) => missing.length)
    .map(([learning, missing]) => `${learning.name}: ${missing.join(", ")}`),
);

// Class-like names in learnings that no tracked file outside .trail/ names.
const code = tracked
  .filter(
    (path) => !path.startsWith(".trail/") && !path.startsWith(".scratch/"),
  )
  .filter((path) =>
    /\.(java|js|mjs|yaml|yml|toml|xml|json|md|css|env|example)$|Dockerfile$/.test(
      path,
    ),
  )
  .map((path) => readFileSync(path, "utf8"))
  .join("\n")
  .toLowerCase();
const nameCache = new Map();
const known = (name) => {
  if (!nameCache.has(name))
    nameCache.set(name, code.includes(name.toLowerCase()));
  return nameCache.get(name);
};
section(
  "Names in learnings that the code no longer has",
  learnings
    .map((learning) => {
      const names = new Set(
        learning.text.match(/\b[A-Z][a-z0-9]+(?:[A-Z][a-z0-9]*)+\b/g) ?? [],
      );
      return [learning, [...names].filter((name) => !known(name))];
    })
    .filter(([, missing]) => missing.length)
    .map(([learning, missing]) => `${learning.name}: ${missing.join(", ")}`),
);

const specs = tracked.filter((path) =>
  /^\.scratch\/[^/]+\/spec\.md$/.test(path),
);
section(
  "Done specs still in .scratch/ (delete the feature folder)",
  specs.filter((path) =>
    /^Status:\s*done\s*$/im.test(readFileSync(path, "utf8")),
  ),
);

const tests = tracked.filter((path) => /\/src\/test\/.*\.java$/.test(path));
const kind = (path) =>
  path.endsWith("ComponentTest.java")
    ? "component"
    : path.endsWith("IT.java")
      ? "integration"
      : path.endsWith("Test.java")
        ? "unit"
        : null;
const counts = { unit: 0, component: 0, integration: 0 };
const sizes = [];
for (const path of tests) {
  const text = readFileSync(path, "utf8");
  const testKind = kind(path);
  if (testKind)
    counts[testKind] += (
      text.match(/@(Test|ParameterizedTest)\b/g) ?? []
    ).length;
  sizes.push([text.split("\n").length, path]);
}
console.log(
  `\nTest methods: ${counts.unit} unit, ${counts.component} component, ${counts.integration} integration`,
);
section(
  `Test files over ${LARGE_TEST_FILE} lines`,
  sizes
    .filter(([lines]) => lines > LARGE_TEST_FILE)
    .sort((a, b) => b[0] - a[0])
    .map(([lines, path]) => `${lines}  ${path}`),
);

if (process.argv.includes("--check")) {
  const started = Date.now();
  const run = spawnSync("mise", ["run", "--force", "check"], {
    encoding: "utf8",
    maxBuffer: 1 << 26,
  });
  const output = `${run.stdout}${run.stderr}`;
  const seconds = ((Date.now() - started) / 1000).toFixed(1);
  console.log(
    `\nmise run check: exit ${run.status}, ${seconds} s, ${output.split("\n").length} output lines`,
  );
} else {
  console.log(
    "\nTo also time mise run check and count its output lines, give --check.",
  );
}
