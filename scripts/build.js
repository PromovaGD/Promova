const fs = require("node:fs");
const path = require("node:path");

const root = path.resolve(__dirname, "..");
const dist = path.join(root, "dist");
const files = ["index.html", "styles.css", "app.js"];
const directories = ["frontend"];
const apiBaseUrl = process.env.PROMOVA_API_BASE_URL || "http://localhost:8080";
const modernUi = process.env.PROMOVA_MODERN_UI !== "false";

validateApiBaseUrl(apiBaseUrl);

fs.rmSync(dist, { recursive: true, force: true });
fs.mkdirSync(dist, { recursive: true });

for (const file of files) {
  fs.copyFileSync(path.join(root, file), path.join(dist, file));
}

for (const directory of directories) {
  copyDirectory(path.join(root, directory), path.join(dist, directory));
}

fs.writeFileSync(
  path.join(dist, "promova-config.js"),
  `window.PROMOVA_API_BASE_URL = ${JSON.stringify(apiBaseUrl)};\nwindow.PROMOVA_MODERN_UI = ${modernUi};\n`,
);

console.log(`Build complete. Files copied to ${dist}`);

function validateApiBaseUrl(value) {
  const parsed = new URL(value);
  if (!["http:", "https:"].includes(parsed.protocol)) {
    throw new Error("PROMOVA_API_BASE_URL must use HTTP or HTTPS");
  }
}

function copyDirectory(source, destination) {
  fs.mkdirSync(destination, { recursive: true });

  for (const entry of fs.readdirSync(source, { withFileTypes: true })) {
    const sourcePath = path.join(source, entry.name);
    const destinationPath = path.join(destination, entry.name);

    if (entry.isDirectory()) {
      copyDirectory(sourcePath, destinationPath);
      continue;
    }

    fs.copyFileSync(sourcePath, destinationPath);
  }
}
