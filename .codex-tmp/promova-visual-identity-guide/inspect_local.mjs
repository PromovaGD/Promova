import fs from "node:fs/promises";
import path from "node:path";
import { createRequire } from "node:module";
import { pathToFileURL } from "node:url";

const require = createRequire(import.meta.url);
const JSZip = require(path.join(process.env.RUNTIME_NODE_MODULES, "jszip"));

function parseArgs(argv) {
  const args = {};
  for (let i = 0; i < argv.length; i += 1) {
    const token = argv[i];
    if (token.startsWith("--")) args[token.slice(2)] = argv[++i];
  }
  return args;
}

async function saveBlobToFile(blob, outputPath) {
  await fs.mkdir(path.dirname(outputPath), { recursive: true });
  if (blob && typeof blob.arrayBuffer === "function") {
    await fs.writeFile(outputPath, Buffer.from(await blob.arrayBuffer()));
    return;
  }
  if (blob instanceof Uint8Array || Buffer.isBuffer(blob)) {
    await fs.writeFile(outputPath, Buffer.from(blob));
    return;
  }
  throw new Error("Expected a Blob or Uint8Array.");
}

function relativeFromWorkspace(workspaceDir, filePath) {
  return path.relative(workspaceDir, filePath).split(path.sep).join("/");
}

function slidesFromPresentation(presentation) {
  if (Array.isArray(presentation.slides?.items)) return presentation.slides.items;
  if (Number.isInteger(presentation.slides?.count) && typeof presentation.slides.getItem === "function") {
    return Array.from({ length: presentation.slides.count }, (_, index) => presentation.slides.getItem(index));
  }
  throw new Error("Could not enumerate imported presentation slides.");
}

async function main() {
  const args = parseArgs(process.argv.slice(2));
  const workspaceDir = path.resolve(args.workspace);
  const pptxPath = path.resolve(args.pptx);
  const scale = args.scale ? Number.parseFloat(args.scale) : 1;
  const outDir = path.resolve(workspaceDir, args["out-dir"] || "template-inspect");
  const sourceBytes = await fs.readFile(pptxPath);
  const zip = await JSZip.loadAsync(sourceBytes);
  const names = Object.keys(zip.files).filter((name) => !zip.files[name].dir);
  const readZipText = async (name) => (await zip.file(name).async("nodebuffer")).toString("utf8");

  const { importRuntimeModule } = await import(pathToFileURL("C:/Users/João/.codex/plugins/cache/openai-primary-runtime/presentations/26.826.12353/skills/presentations/container_tools/runtime_helpers.mjs").href);
  const { FileBlob, PresentationFile } = await importRuntimeModule("@oai/artifact-tool");
  await fs.rm(outDir, { recursive: true, force: true });
  const slidesDir = path.join(outDir, "source-slides");
  const layoutsDir = path.join(outDir, "layouts");
  const mediaDir = path.join(outDir, "assets", "ppt", "media");
  const inspectPath = path.join(outDir, "template-inspect.ndjson");
  const manifestPath = path.join(outDir, "template-manifest.json");
  await fs.mkdir(slidesDir, { recursive: true });
  await fs.mkdir(layoutsDir, { recursive: true });

  const presentation = await PresentationFile.importPptx(await FileBlob.load(pptxPath));
  const slides = slidesFromPresentation(presentation);
  const slideArtifacts = [];
  for (let index = 0; index < slides.length; index += 1) {
    const slide = slides[index];
    const slideNumber = index + 1;
    const padded = String(slideNumber).padStart(2, "0");
    const pngPath = path.join(slidesDir, `source-slide-${padded}.png`);
    const layoutPath = path.join(layoutsDir, `source-slide-${padded}.layout.json`);
    await saveBlobToFile(await presentation.export({ slide, format: "png", scale }), pngPath);
    await saveBlobToFile(await presentation.export({ slide, format: "layout" }), layoutPath);
    slideArtifacts.push({ slide: slideNumber, previewPath: pngPath, previewRelativePath: relativeFromWorkspace(workspaceDir, pngPath), layoutPath, layoutRelativePath: relativeFromWorkspace(workspaceDir, layoutPath) });
  }

  const extractedMedia = [];
  for (const entry of names.filter((name) => name.startsWith("ppt/media/"))) {
    const target = path.join(mediaDir, path.basename(entry));
    await fs.mkdir(path.dirname(target), { recursive: true });
    await fs.writeFile(target, await zip.file(entry).async("nodebuffer"));
    const stat = await fs.stat(target);
    extractedMedia.push({ entry, path: target, relativePath: relativeFromWorkspace(workspaceDir, target), bytes: stat.size });
  }

  const inspect = await presentation.inspect({
    kind: "slide,textbox,shape,image,table,chart,notes,layout",
    include: "id,slide,name,title,text,textPreview,textChars,textLines,bbox,bboxUnit,preview,chartType,alt,prompt,isPlaceholder,comments,placeholders",
    maxChars: 500000,
  });
  await fs.writeFile(inspectPath, inspect.ndjson || "", "utf8");
  const fonts = new Set();
  for (const name of names) {
    if (!/^ppt\/(?:slides|slideMasters|slideLayouts|theme)\/.*\.xml$/.test(name) && !/^ppt\/theme\/.*\.xml$/.test(name)) continue;
    const xml = await readZipText(name);
    for (const match of xml.matchAll(/\btypeface="([^"]+)"/g)) fonts.add(match[1]);
  }
  const slideXmlNames = names.filter((name) => /^ppt\/slides\/slide\d+\.xml$/.test(name));
  const manifest = {
    sourcePptx: pptxPath,
    workspace: workspaceDir,
    outDir,
    generatedAt: new Date().toISOString(),
    slideCount: slides.length,
    slideArtifacts,
    inspectPath,
    inspectRelativePath: relativeFromWorkspace(workspaceDir, inspectPath),
    inspectTruncated: Boolean(inspect.truncated),
    inspectMetadata: inspect.metadata || {},
    extractedMedia,
    fonts: [...fonts].sort(),
    packageParts: {
      mediaCount: names.filter((name) => name.startsWith("ppt/media/")).length,
      slideXmlCount: slideXmlNames.length,
      chartCount: names.filter((name) => /^ppt\/(?:charts|embeddings\/charts)\/chart\d+\.xml$/.test(name)).length,
      tableSlideCount: (await Promise.all(slideXmlNames.map(async (name) => (await readZipText(name)).includes("<a:tbl>")))).filter(Boolean).length,
    },
  };
  await fs.writeFile(manifestPath, `${JSON.stringify(manifest, null, 2)}\n`, "utf8");
  console.log(manifestPath);
}

main().catch((error) => {
  console.error(error?.stack || error);
  process.exitCode = 1;
});
