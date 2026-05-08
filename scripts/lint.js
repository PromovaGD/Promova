const fs = require("node:fs");
const path = require("node:path");
const { spawnSync } = require("node:child_process");

const root = path.resolve(__dirname, "..");
const filesToCheck = [
  "app.js",
  "scripts/build.js",
  "scripts/dev-server.js",
  "scripts/lint.js",
];
const directoriesToCheck = ["frontend"];

for (const directory of directoriesToCheck) {
  collectJavaScriptFiles(path.join(root, directory), filesToCheck);
}

function collectJavaScriptFiles(directory, target) {
  if (!fs.existsSync(directory)) {
    return;
  }

  for (const entry of fs.readdirSync(directory, { withFileTypes: true })) {
    const entryPath = path.join(directory, entry.name);

    if (entry.isDirectory()) {
      collectJavaScriptFiles(entryPath, target);
      continue;
    }

    if (entry.name.endsWith(".js") || entry.name.endsWith(".mjs")) {
      target.push(path.relative(root, entryPath));
    }
  }
}

function checkSyntax(file) {
  const result = spawnSync(process.execPath, ["--check", path.join(root, file)], {
    stdio: "inherit",
  });

  if (result.status !== 0) {
    throw new Error(`Syntax check failed for ${file}`);
  }
}

function ensureRequiredContent() {
  const appJs = fs.readFileSync(path.join(root, "app.js"), "utf8");

  for (const requiredName of ["frontend/app.mjs", "startApp"]) {
    if (!appJs.includes(requiredName)) {
      throw new Error(`Expected ${requiredName} to exist in app.js`);
    }
  }

  const indexHtml = fs.readFileSync(path.join(root, "index.html"), "utf8");
  if (!indexHtml.includes("app.js") || !indexHtml.includes("styles.css")) {
    throw new Error("index.html must load app.js and styles.css");
  }
}

try {
  ensureRequiredContent();
  for (const file of filesToCheck) {
    checkSyntax(file);
  }
  console.log("Lint checks passed.");
} catch (error) {
  console.error(error.message);
  process.exitCode = 1;
}
