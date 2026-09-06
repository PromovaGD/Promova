const fs = require("node:fs");
const http = require("node:http");
const path = require("node:path");

const root = path.resolve(__dirname, "../dist");
const host = process.env.HOST || "127.0.0.1";
const port = Number(process.env.PORT || 4173);

if (!Number.isInteger(port) || port < 1 || port > 65535) {
  throw new Error("PORT must be an integer between 1 and 65535");
}

if (!fs.existsSync(path.join(root, "index.html"))) {
  throw new Error("dist/index.html is missing; run npm run build first");
}

const mimeTypes = {
  ".css": "text/css; charset=utf-8",
  ".html": "text/html; charset=utf-8",
  ".js": "application/javascript; charset=utf-8",
  ".json": "application/json; charset=utf-8",
  ".mjs": "application/javascript; charset=utf-8",
  ".png": "image/png",
  ".svg": "image/svg+xml",
};

const server = http.createServer((request, response) => {
  const pathname = decodeURIComponent(new URL(request.url, "http://localhost").pathname);
  const requestedPath = pathname === "/" ? "index.html" : pathname.replace(/^\/+/, "");
  const filePath = path.resolve(root, requestedPath);

  if (filePath !== root && !filePath.startsWith(`${root}${path.sep}`)) {
    response.writeHead(403);
    response.end("Forbidden");
    return;
  }

  const resolvedPath =
    fs.existsSync(filePath) && fs.statSync(filePath).isFile()
      ? filePath
      : path.join(root, "index.html");
  const contentType = mimeTypes[path.extname(resolvedPath).toLowerCase()] || "application/octet-stream";

  response.writeHead(200, { "Content-Type": contentType });
  fs.createReadStream(resolvedPath).pipe(response);
});

server.on("error", (error) => {
  console.error(`Frontend server failed: ${error.message}`);
  process.exitCode = 1;
});

function shutdown() {
  const forceExit = setTimeout(() => process.exit(1), 5000);
  forceExit.unref();
  server.close(() => process.exit(0));
}

process.once("SIGINT", shutdown);
process.once("SIGTERM", shutdown);

server.listen(port, host, () => {
  console.log(`Frontend build available at http://${host}:${port}`);
});
