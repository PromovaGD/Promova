import fs from "node:fs/promises";
import path from "node:path";
import { FileBlob, PresentationFile } from "@oai/artifact-tool";

const tmpDir = "C:/Users/João/Documents/Projects/FIAP/Startup/.codex-tmp/promova-pitch-deck";
const starterPptxPath = `${tmpDir}/template-starter.pptx`;
const finalPptxPath = "C:/Users/João/Documents/Projects/FIAP/Startup/artifacts/promova-pitch-tcc.pptx";
const renderDir = `${tmpDir}/final-renders`;
const layoutDir = `${tmpDir}/final-layout`;

const C = {
  canvas: "#FBFAFF",
  dark: "#0D102B",
  ink: "#111744",
  white: "#FFFFFF",
  lavender: "#F0ECFF",
  border: "#E5E0F5",
  violet: "#7C60F6",
  deep: "#4B1FD1",
  soft: "#A896FF",
  muted: "#67708E",
  lightText: "#D8D7F1",
  faintText: "#AAA9CD",
  green: "#1F9D77",
  amber: "#CF8A2C",
  red: "#C85263",
};

async function saveBlob(filePath, blob) {
  await fs.mkdir(path.dirname(filePath), { recursive: true });
  await fs.writeFile(filePath, Buffer.from(await blob.arrayBuffer()));
}

function parseNdjson(ndjson) {
  return ndjson
    .split(/\r?\n/)
    .filter((line) => line.trim())
    .map((line) => JSON.parse(line));
}

function close(a, b, tolerance = 2) {
  return Math.abs(Number(a) - Number(b)) <= tolerance;
}

function getRecord(records, slide, predicate, label) {
  const matches = records.filter((record) => record.slide === slide && predicate(record));
  if (matches.length !== 1) {
    throw new Error(`Expected one ${label} on slide ${slide}; found ${matches.length}.`);
  }
  return matches[0];
}

function byText(records, slide, text) {
  return getRecord(records, slide, (record) => record.text === text, `text ${JSON.stringify(text)}`);
}

function byBbox(records, slide, bbox, kind) {
  return getRecord(
    records,
    slide,
    (record) =>
      (!kind || record.kind === kind) &&
      Array.isArray(record.bbox) &&
      record.bbox.length === 4 &&
      record.bbox.every((value, index) => close(value, bbox[index])),
    `${kind || "element"} bbox ${bbox.join(",")}`,
  );
}

function resolve(presentation, record) {
  return presentation.resolve(record.id);
}

function setText(shape, text, position, style = {}) {
  if (position) shape.position = position;
  shape.text.set(text);
  shape.text.style = {
    typeface: "Inter",
    fontSize: 16,
    color: C.ink,
    alignment: "left",
    verticalAlignment: "top",
    autoFit: "none",
    wrap: "square",
    insets: { top: 0, right: 0, bottom: 0, left: 0 },
    ...style,
  };
  return shape;
}

function addText(slide, name, text, position, style = {}) {
  const shape = slide.shapes.add({
    geometry: "textbox",
    name,
    position,
    fill: "none",
    line: { style: "solid", fill: "none", width: 0 },
  });
  return setText(shape, text, undefined, style);
}

function addRoundRect(slide, name, position, fill, line = { style: "solid", fill: "none", width: 0 }, radius = 18) {
  const shape = slide.shapes.add({
    geometry: "roundRect",
    name,
    position,
    fill,
    line,
    borderRadius: radius,
  });
  return shape;
}

function addPill(slide, name, text, position, fill, textColor, lineFill = "none") {
  const pill = addRoundRect(
    slide,
    name,
    position,
    fill,
    { style: "solid", fill: lineFill, width: lineFill === "none" ? 0 : 1 },
    18,
  );
  pill.text.set(text);
  pill.text.style = {
    typeface: "Inter",
    fontSize: 12,
    bold: true,
    color: textColor,
    alignment: "center",
    verticalAlignment: "middle",
    autoFit: "none",
    wrap: "none",
    insets: { top: 2, right: 8, bottom: 2, left: 8 },
  };
  return pill;
}

function addNumberedRow(slide, index, label, left, top, width, options = {}) {
  const circle = slide.shapes.add({
    geometry: "ellipse",
    name: `${options.prefix || "row"}-${index}-marker`,
    position: { left, top, width: 30, height: 30 },
    fill: options.fill || C.lavender,
    line: { style: "solid", fill: "none", width: 0 },
  });
  circle.text.set(String(index).padStart(2, "0"));
  circle.text.style = {
    typeface: "Inter",
    fontSize: 11,
    bold: true,
    color: options.markerText || C.deep,
    alignment: "center",
    verticalAlignment: "middle",
    autoFit: "none",
    insets: { top: 0, right: 0, bottom: 0, left: 0 },
  };
  addText(
    slide,
    `${options.prefix || "row"}-${index}-label`,
    label,
    { left: left + 46, top: top + 2, width: width - 46, height: 28 },
    { fontSize: options.fontSize || 17, bold: true, color: options.color || C.ink, verticalAlignment: "middle" },
  );
}

function addCheckRow(slide, name, label, left, top, width, color = C.violet) {
  const dot = slide.shapes.add({
    geometry: "ellipse",
    name: `${name}-dot`,
    position: { left, top: top + 7, width: 12, height: 12 },
    fill: color,
    line: { style: "solid", fill: "none", width: 0 },
  });
  dot.text.set("");
  addText(slide, `${name}-text`, label, { left: left + 24, top, width: width - 24, height: 28 }, { fontSize: 16, bold: true, verticalAlignment: "middle" });
}

function deleteTexts(presentation, records, slide, texts) {
  for (const text of texts) resolve(presentation, byText(records, slide, text)).delete();
}

function deleteBboxes(presentation, records, slide, entries) {
  for (const entry of entries) {
    const [bbox, kind] = entry;
    resolve(presentation, byBbox(records, slide, bbox, kind)).delete();
  }
}

function editLightChrome(presentation, records, slide, pageNumber, title, subtitle) {
  setText(resolve(presentation, byText(records, slide, "PROMOVA  /  VISUAL IDENTITY")), "PROMOVA  /  PITCH TCC", { left: 72, top: 40, width: 320, height: 22 }, { fontSize: 12, bold: true, color: C.muted });
  setText(resolve(presentation, byText(records, slide, "04")), String(pageNumber).padStart(2, "0"), { left: 1158, top: 40, width: 50, height: 22 }, { fontSize: 12, bold: true, color: C.ink, alignment: "right" });
  setText(resolve(presentation, byText(records, slide, "FOUNDATION / V0.1")), "TCC / STARTUP", { left: 72, top: 697, width: 220, height: 14 }, { fontSize: 10, bold: true, color: C.muted });
  setText(resolve(presentation, byText(records, slide, "PROGRESS YOU CAN PROVE")), "PROMOVA", { left: 982, top: 697, width: 226, height: 14 }, { fontSize: 10, bold: true, color: C.muted, alignment: "right" });
  setText(resolve(presentation, byText(records, slide, "Typography and components should feel calm, then decisive")), title, { left: 72, top: 82, width: 1040, height: 54 }, { fontSize: 40, bold: true, color: C.ink });
  setText(resolve(presentation, byText(records, slide, "Inter gives the product a modern, readable voice; rounded surfaces carry the logo's friendliness.")), subtitle, { left: 72, top: 145, width: 980, height: 30 }, { fontSize: 18, color: C.muted });
}

function clearSourceSlide4Content(presentation, records, slide) {
  deleteTexts(presentation, records, slide, [
    "TYPE SYSTEM",
    "Inter",
    "Make progress visible.",
    "Use sentence case, short lines and clear hierarchy. Let the metrics be loud; let the supporting copy stay quiet.",
    "DISPLAY  44 / 52",
    "HEADING  32 / 38",
    "UI  16 / 24",
    "CAPTION  12 / 16",
    "WEBSITE COMPONENTS",
    "Progress is the interface language",
    "Start tracking",
    "WEEKLY MOMENTUM",
    "+18%",
    "ON TRACK",
    "A single focused action",
    "12 px radius",
    "8 px grid",
    "1 px border",
    "Rule of thumb: one energetic element per surface.",
  ]);
  deleteBboxes(presentation, records, slide, [
    [[100, 488, 380, 0], "shape"],
    [[630, 338, 548, 134], "shape"],
    [[654, 441, 476, 9], "shape"],
    [[654, 441, 342, 9], "shape"],
  ]);
}

function setNotes(slide, lines) {
  slide.speakerNotes.textFrame.setText(lines.join("\n"));
  slide.speakerNotes.setVisible(true);
}

async function main() {
  await fs.mkdir(renderDir, { recursive: true });
  await fs.mkdir(layoutDir, { recursive: true });
  await fs.mkdir(path.dirname(finalPptxPath), { recursive: true });

  const presentation = await PresentationFile.importPptx(await FileBlob.load(starterPptxPath));
  const snapshot = await presentation.inspect({
    kind: "slide,textbox,shape,image,notes,layout",
    include: "id,slide,name,title,text,textPreview,bbox,bboxUnit",
    maxChars: 500000,
  });
  const records = parseNdjson(snapshot.ndjson);

  // Slide 1 — opening + problem
  {
    const slideNo = 1;
    const slide = presentation.slides.getItem(slideNo - 1);
    setText(resolve(presentation, byText(records, slideNo, "PROMOVA  /  VISUAL IDENTITY 01")), "PROMOVA  /  TCC 2026", { left: 72, top: 64, width: 360, height: 22 }, { fontSize: 12, bold: true, color: C.lightText });
    const topPill = resolve(presentation, byText(records, slideNo, "FOUNDATION / V0.1"));
    setText(topPill, "PITCH ACADÊMICO + STARTUP", { left: 980, top: 58, width: 228, height: 32 }, { fontSize: 10, bold: true, color: C.lightText, alignment: "center", verticalAlignment: "middle", insets: { top: 2, right: 8, bottom: 2, left: 8 } });
    topPill.fill = "#25294F";
    topPill.line = { style: "solid", fill: "#454875", width: 1 };

    setText(resolve(presentation, byText(records, slideNo, "Progress\nyou can prove.")), "PROMOVA", { left: 72, top: 150, width: 570, height: 84 }, { fontSize: 64, bold: true, color: C.white, verticalAlignment: "middle" });
    setText(resolve(presentation, byText(records, slideNo, "A visual system for a product that turns career momentum into visible evidence.")), "Gestão de equipes orientada por evidências", { left: 72, top: 258, width: 520, height: 58 }, { fontSize: 21, color: C.lightText, lineSpacing: 1.1 });

    const tag1 = resolve(presentation, byText(records, slideNo, "PRODUCT"));
    setText(tag1, "AVALIAÇÕES SUBJETIVAS", { left: 72, top: 370, width: 250, height: 34 }, { fontSize: 11, bold: true, color: C.white, alignment: "center", verticalAlignment: "middle", insets: { top: 2, right: 8, bottom: 2, left: 8 } });
    tag1.fill = C.violet;
    tag1.line = { style: "solid", fill: "none", width: 0 };

    const tag2 = resolve(presentation, byText(records, slideNo, "PITCH"));
    setText(tag2, "INFORMAÇÕES ESPALHADAS", { left: 334, top: 370, width: 250, height: 34 }, { fontSize: 11, bold: true, color: C.lightText, alignment: "center", verticalAlignment: "middle", insets: { top: 2, right: 8, bottom: 2, left: 8 } });
    tag2.fill = "#25294F";
    tag2.line = { style: "solid", fill: "#454875", width: 1 };

    addPill(slide, "problem-criteria", "CRITÉRIOS POUCO CLAROS", { left: 72, top: 416, width: 250, height: 34 }, "#25294F", C.lightText, "#454875");
    addPill(slide, "problem-bias", "RISCO DE VIESES", { left: 334, top: 416, width: 250, height: 34 }, C.deep, C.white);

    const messageLine = resolve(presentation, byBbox(records, slideNo, [72, 646, 220, 5], "shape"));
    messageLine.position = { left: 72, top: 524, width: 220, height: 5 };
    setText(resolve(presentation, byText(records, slideNo, "A direction-first identity: optimistic, precise, human.")), "Decisões de promoção mais justas, transparentes e baseadas no trabalho real.", { left: 72, top: 546, width: 560, height: 62 }, { fontSize: 18, bold: true, color: C.white, lineSpacing: 1.08 });

    setText(resolve(presentation, byText(records, slideNo, "PRIMARY LOCKUP")), "EVIDÊNCIA, NÃO INTUIÇÃO", { left: 744, top: 565, width: 240, height: 16 }, { fontSize: 11, bold: true, color: C.deep });
    setText(resolve(presentation, byText(records, slideNo, "For light surfaces and first-impression moments.")), "O trabalho real vira contexto para decisões melhores.", { left: 744, top: 589, width: 360, height: 22 }, { fontSize: 14, color: C.muted });
    setText(resolve(presentation, byText(records, slideNo, "supplied reference / keep proportions intact")), "IA apoia a análise. A decisão final permanece humana.", { left: 744, top: 615, width: 390, height: 16 }, { fontSize: 10, color: "#9B9FB9" });

    setNotes(slide, [
      "Abrir com a tensão: avaliações importantes dependem de memória, percepção e evidências dispersas.",
      "Reforçar que o Promova não automatiza a decisão de promoção.",
      "[Sources]",
      "- User-provided Promova TCC pitch brief, current conversation.",
      "- User-provided Promova visual identity guide and embedded logo asset.",
    ]);
  }

  // Slide 2 — solution + MVP
  {
    const slideNo = 2;
    const slide = presentation.slides.getItem(slideNo - 1);
    editLightChrome(presentation, records, slideNo, slideNo, "Como o Promova funciona", "O trabalho real vira evidência estruturada — e prepara a demonstração do MVP.");
    clearSourceSlide4Content(presentation, records, slideNo);

    addText(slide, "flow-label", "FLUXO DE EVIDÊNCIAS", { left: 100, top: 250, width: 260, height: 18 }, { fontSize: 11, bold: true, color: C.deep });
    slide.shapes.add({
      geometry: "line",
      name: "flow-spine",
      position: { left: 119, top: 288, width: 0, height: 236 },
      fill: "none",
      line: { style: "solid", fill: "#C8BCFF", width: 3 },
    });
    const flow = ["Trabalho real", "Coleta de evidências", "Análise + IA", "Gestão da equipe", "Promoção"];
    flow.forEach((label, index) => {
      const top = 274 + index * 58;
      const node = slide.shapes.add({
        geometry: "ellipse",
        name: `flow-node-${index + 1}`,
        position: { left: 100, top, width: 38, height: 38 },
        fill: index === flow.length - 1 ? C.deep : C.lavender,
        line: { style: "solid", fill: index === flow.length - 1 ? C.deep : "#D9D4EF", width: 1 },
      });
      node.text.set(String(index + 1).padStart(2, "0"));
      node.text.style = {
        typeface: "Inter",
        fontSize: 11,
        bold: true,
        color: index === flow.length - 1 ? C.white : C.deep,
        alignment: "center",
        verticalAlignment: "middle",
        autoFit: "none",
        insets: { top: 0, right: 0, bottom: 0, left: 0 },
      };
      addText(slide, `flow-step-${index + 1}`, label, { left: 158, top: top + 3, width: 330, height: 32 }, { fontSize: 18, bold: true, verticalAlignment: "middle" });
      if (index === 1) addPill(slide, "github-source", "GITHUB", { left: 416, top: top + 5, width: 92, height: 26 }, C.lavender, C.deep);
    });

    addText(slide, "mvp-label", "MVP PRONTO PARA DEMO", { left: 630, top: 250, width: 250, height: 18 }, { fontSize: 11, bold: true, color: C.deep });
    addText(slide, "mvp-heading", "O ciclo principal já funciona", { left: 630, top: 282, width: 500, height: 34 }, { fontSize: 24, bold: true });
    addText(slide, "mvp-caption", "Integração inicial: GitHub", { left: 630, top: 322, width: 260, height: 22 }, { fontSize: 14, color: C.muted });
    addCheckRow(slide, "mvp-dashboard", "Dashboard", 630, 368, 220);
    addCheckRow(slide, "mvp-evidence", "Evidências", 630, 412, 220);
    addCheckRow(slide, "mvp-skills", "Competências", 630, 456, 220);
    addCheckRow(slide, "mvp-growth", "Evolução", 900, 368, 220);
    addCheckRow(slide, "mvp-analysis", "Análise", 900, 412, 220);
    addText(slide, "mvp-human-note", "IA interpreta evidências; a decisão continua humana.", { left: 630, top: 502, width: 510, height: 26 }, { fontSize: 14, color: C.muted, bold: true });
    addPill(slide, "mvp-demo", "AGORA: DEMONSTRAÇÃO DO MVP", { left: 630, top: 548, width: 280, height: 38 }, C.deep, C.white);

    setNotes(slide, [
      "Percorrer o fluxo em menos de um minuto e iniciar a demonstração ao vivo do site.",
      "O MVP também possui backend Spring Boot, endpoints de análise e motor simulado preparado para IA real.",
      "[Sources]",
      "- User-provided Promova TCC pitch brief, current conversation.",
      "- User-provided Promova visual identity guide.",
    ]);
  }

  // Slide 3 — audience + value
  {
    const slideNo = 3;
    const slide = presentation.slides.getItem(slideNo - 1);
    editLightChrome(presentation, records, slideNo, slideNo, "Para quem criamos valor", "Começamos por equipes de tecnologia e ampliamos o contexto da decisão.");
    clearSourceSlide4Content(presentation, records, slideNo);

    addText(slide, "audience-label", "PÚBLICO", { left: 100, top: 250, width: 160, height: 18 }, { fontSize: 11, bold: true, color: C.deep });
    addText(slide, "audience-heading", "Equipes de tecnologia", { left: 100, top: 286, width: 380, height: 46 }, { fontSize: 30, bold: true });
    const audienceRows = [
      ["PÚBLICO INICIAL", "Empresas com equipes de tecnologia"],
      ["USUÁRIO PRINCIPAL", "Gestores e líderes de equipe"],
      ["BENEFICIÁRIOS", "Colaboradores e RH"],
    ];
    audienceRows.forEach(([kicker, value], index) => {
      const top = 360 + index * 72;
      addText(slide, `audience-kicker-${index + 1}`, kicker, { left: 100, top, width: 180, height: 16 }, { fontSize: 10, bold: true, color: C.muted });
      addText(slide, `audience-value-${index + 1}`, value, { left: 100, top: top + 22, width: 390, height: 28 }, { fontSize: 17, bold: true });
      if (index < audienceRows.length - 1) {
        slide.shapes.add({ geometry: "line", name: `audience-divider-${index + 1}`, position: { left: 100, top: top + 58, width: 380, height: 0 }, fill: "none", line: { style: "solid", fill: C.border, width: 1 } });
      }
    });

    addText(slide, "value-label", "PROPOSTA DE VALOR", { left: 630, top: 250, width: 220, height: 18 }, { fontSize: 11, bold: true, color: C.deep });
    addText(slide, "value-statement", "Transformar o trabalho diário em evidências para uma gestão mais objetiva.", { left: 630, top: 286, width: 510, height: 92 }, { fontSize: 28, bold: true, lineSpacing: 1.04 });
    slide.shapes.add({ geometry: "line", name: "value-divider", position: { left: 630, top: 397, width: 500, height: 0 }, fill: "none", line: { style: "solid", fill: C.border, width: 1 } });
    addNumberedRow(slide, 1, "Mais contexto para o gestor", 630, 424, 500, { prefix: "benefit" });
    addNumberedRow(slide, 2, "Mais transparência para o colaborador", 630, 480, 500, { prefix: "benefit" });
    addNumberedRow(slide, 3, "Mais consistência para a empresa", 630, 536, 500, { prefix: "benefit", fill: C.deep, markerText: C.white });

    setNotes(slide, [
      "Apresentar o recorte inicial sem limitar a expansão futura do produto.",
      "Não repetir a arquitetura; concentrar a fala no valor para cada participante.",
      "[Sources]",
      "- User-provided Promova TCC pitch brief, current conversation.",
      "- User-provided Promova visual identity guide.",
    ]);
  }

  // Slide 4 — channels + support
  {
    const slideNo = 4;
    const slide = presentation.slides.getItem(slideNo - 1);
    editLightChrome(presentation, records, slideNo, slideNo, "Como chegamos e sustentamos a adoção", "Aquisição B2B com pilotos próximos das equipes e apoio à implantação.");
    clearSourceSlide4Content(presentation, records, slideNo);

    addText(slide, "channels-label", "CANAIS", { left: 100, top: 250, width: 160, height: 18 }, { fontSize: 11, bold: true, color: C.deep });
    addText(slide, "channels-heading", "Venda consultiva com demonstração", { left: 100, top: 286, width: 400, height: 58 }, { fontSize: 27, bold: true, lineSpacing: 1.04 });
    [
      "Prospecção de empresas",
      "Pilotos com squads de tecnologia",
      "Demonstrações do produto",
      "Parcerias com RH / People",
    ].forEach((label, index) => addNumberedRow(slide, index + 1, label, 100, 366 + index * 50, 400, { prefix: "channel", fontSize: 16 }));

    addText(slide, "support-label", "APOIO À ADOÇÃO", { left: 630, top: 250, width: 220, height: 18 }, { fontSize: 11, bold: true, color: C.deep });
    addText(slide, "support-heading", "Responsabilidade compartilhada", { left: 630, top: 286, width: 500, height: 40 }, { fontSize: 26, bold: true });
    slide.shapes.add({ geometry: "line", name: "support-column-divider", position: { left: 904, top: 360, width: 0, height: 190 }, fill: "none", line: { style: "solid", fill: C.border, width: 1 } });

    addText(slide, "support-company-label", "DENTRO DA EMPRESA", { left: 630, top: 356, width: 220, height: 18 }, { fontSize: 11, bold: true, color: C.muted });
    addCheckRow(slide, "company-manager", "Gestores", 630, 402, 220, C.green);
    addCheckRow(slide, "company-hr", "RH", 630, 450, 220, C.green);
    addCheckRow(slide, "company-leadership", "Liderança", 630, 498, 220, C.green);

    addText(slide, "support-promova-label", "DO LADO DO PROMOVA", { left: 938, top: 356, width: 220, height: 18 }, { fontSize: 11, bold: true, color: C.muted });
    addCheckRow(slide, "promova-onboarding", "Onboarding", 938, 402, 220);
    addCheckRow(slide, "promova-support", "Suporte", 938, 450, 220);
    addCheckRow(slide, "promova-docs", "Documentação", 938, 498, 220);

    setNotes(slide, [
      "Conectar os canais ao caráter demonstrável do MVP: pilotos e demonstrações são o ponto de entrada.",
      "Destacar que a adoção depende de gestores, RH e liderança, com suporte do Promova.",
      "[Sources]",
      "- User-provided Promova TCC pitch brief, current conversation.",
      "- User-provided Promova visual identity guide.",
    ]);
  }

  // Slide 5 — success + expansion + business model
  {
    const slideNo = 5;
    const slide = presentation.slides.getItem(slideNo - 1);
    setText(resolve(presentation, byText(records, slideNo, "PROMOVA  /  VISUAL IDENTITY 05")), "PROMOVA  /  GTM + NEGÓCIO", { left: 72, top: 54, width: 360, height: 22 }, { fontSize: 12, bold: true, color: "#F1EEFF" });
    setText(resolve(presentation, byText(records, slideNo, "Start with the foundation, then scale the system")), "Como medimos, expandimos e monetizamos", { left: 72, top: 92, width: 960, height: 60 }, { fontSize: 40, bold: true, color: C.white });
    setText(resolve(presentation, byText(records, slideNo, "One system across the product, the website and the story we tell in the room.")), "Começamos em tecnologia, com modelo B2B SaaS por usuário.", { left: 72, top: 164, width: 830, height: 28 }, { fontSize: 18, color: "#F1EEFF" });

    const groups = [
      {
        oldX: 72,
        x: 72,
        number: "01",
        heading: "Sucesso",
        body: "• Adesão dos gestores\n• Uso recorrente\n• Evidências analisadas\n• Avaliações realizadas\n• Evolução dos colaboradores\n• Satisfação dos usuários",
        label: "INDICADORES",
      },
      {
        oldX: 344,
        x: 460,
        number: "02",
        heading: "Expansão",
        body: "GitHub  →  Jira  →  Slack\n\nDepois: outras equipes e áreas",
        label: "TECNOLOGIA COMO PONTO DE PARTIDA",
      },
      {
        oldX: 616,
        x: 848,
        number: "03",
        heading: "Modelo de negócio",
        body: "B2B SaaS\nBase — R$ 29,99 / usuário / mês\nPremium — R$ 45,36 / usuário / mês\nCenário inicial: 150 usuários",
        label: "3 EMPRESAS × 50 PESSOAS",
      },
    ];

    for (const group of groups) {
      const bg = resolve(presentation, byBbox(records, slideNo, [group.oldX, 225, 252, 250], "shape"));
      bg.position = { left: group.x, top: 225, width: 360, height: 290 };
      const badge = resolve(presentation, byBbox(records, slideNo, [group.oldX + 22, 249, 42, 28], "textbox"));
      setText(badge, group.number, { left: group.x + 22, top: 249, width: 42, height: 28 }, { fontSize: 12, bold: true, color: C.deep, alignment: "center", verticalAlignment: "middle", insets: { top: 2, right: 2, bottom: 2, left: 2 } });
      badge.fill = C.lavender;

      setText(resolve(presentation, byBbox(records, slideNo, [group.oldX + 22, 299, 200, 30], "textbox")), group.heading, { left: group.x + 22, top: 299, width: 316, height: 36 }, { fontSize: 21, bold: true, color: C.ink });
      const body = resolve(presentation, byBbox(records, slideNo, [group.oldX + 22, 343, 200, 76], "textbox"));
      setText(body, group.body, { left: group.x + 22, top: 346, width: 316, height: 126 }, { fontSize: group.number === "02" ? 18 : 14.5, bold: group.number === "02", color: C.muted, lineSpacing: 1.08 });
      const divider = resolve(presentation, byBbox(records, slideNo, [group.oldX + 22, 445, 208, 0], "shape"));
      divider.position = { left: group.x + 22, top: 482, width: 316, height: 0 };
      setText(resolve(presentation, byBbox(records, slideNo, [group.oldX + 22, 454, 200, 15], "textbox")), group.label, { left: group.x + 22, top: 492, width: 316, height: 15 }, { fontSize: 9.5, bold: true, color: C.deep });
    }

    deleteBboxes(presentation, records, slideNo, [
      [[888, 225, 252, 250], "shape"],
      [[910, 249, 42, 28], "textbox"],
      [[910, 299, 200, 30], "textbox"],
      [[910, 343, 200, 76], "textbox"],
      [[910, 445, 208, 0], "shape"],
      [[910, 454, 200, 15], "textbox"],
    ]);
    deleteTexts(presentation, records, slideNo, [
      "GUARDRAILS FOR EVERY SURFACE",
      "KEEP  proportions intact",
      "PAIR  violet with ink",
      "SIGNAL  gradient = movement",
      "PROVE  evidence before decoration",
    ]);
    setText(resolve(presentation, byText(records, slideNo, "Next review point: after the first implementation PR, validate the logo at mobile, desktop, dark-surface and export sizes.")), "Decisão humana apoiada por evidências.", { left: 72, top: 606, width: 860, height: 28 }, { fontSize: 16, bold: true, color: "#F1EEFF" });
    setText(resolve(presentation, byText(records, slideNo, "ONE SYSTEM. MANY MOMENTS. SAME FEELING.")), "TCC / STARTUP / 2026", { left: 72, top: 665, width: 480, height: 22 }, { fontSize: 12, bold: true, color: C.white });
    setText(resolve(presentation, byText(records, slideNo, "PROMOVA")), "PROMOVA", { left: 1090, top: 665, width: 118, height: 22 }, { fontSize: 12, bold: true, color: C.white, alignment: "right" });

    setNotes(slide, [
      "Sucesso: adesão, recorrência, volume de evidências e avaliações, evolução e satisfação.",
      "Precificação informada pelo projeto: custo estimado de R$ 16,66 no Base e R$ 25,20 no Premium, com markup de 80% e comparação de mercado.",
      "Cenário inicial: 3 empresas com 50 funcionários cada, total de 150 usuários.",
      "[Sources]",
      "- User-provided Promova TCC pitch brief, current conversation.",
      "- User-provided Promova visual identity guide.",
    ]);
  }

  // Slide 6 — minimal closing
  {
    const slideNo = 6;
    const slide = presentation.slides.getItem(slideNo - 1);
    setText(resolve(presentation, byText(records, slideNo, "PROMOVA  /  VISUAL IDENTITY 05")), "PROMOVA  /  FECHAMENTO", { left: 72, top: 54, width: 360, height: 22 }, { fontSize: 12, bold: true, color: "#F1EEFF" });
    setText(resolve(presentation, byText(records, slideNo, "Start with the foundation, then scale the system")), "Promoção não deveria depender\nde quem é mais lembrado.", { left: 72, top: 190, width: 980, height: 150 }, { fontSize: 48, bold: true, color: C.white, lineSpacing: 0.98 });
    setText(resolve(presentation, byText(records, slideNo, "One system across the product, the website and the story we tell in the room.")), "Deveria ser apoiada por evidências.", { left: 72, top: 382, width: 820, height: 58 }, { fontSize: 28, bold: true, color: "#F1EEFF" });

    deleteBboxes(presentation, records, slideNo, [
      [[72, 225, 252, 250], "shape"], [[94, 249, 42, 28], "textbox"], [[94, 299, 200, 30], "textbox"], [[94, 343, 200, 76], "textbox"], [[94, 445, 208, 0], "shape"], [[94, 454, 200, 15], "textbox"],
      [[344, 225, 252, 250], "shape"], [[366, 249, 42, 28], "textbox"], [[366, 299, 200, 30], "textbox"], [[366, 343, 200, 76], "textbox"], [[366, 445, 208, 0], "shape"], [[366, 454, 200, 15], "textbox"],
      [[616, 225, 252, 250], "shape"], [[638, 249, 42, 28], "textbox"], [[638, 299, 200, 30], "textbox"], [[638, 343, 200, 76], "textbox"], [[638, 445, 208, 0], "shape"], [[638, 454, 200, 15], "textbox"],
      [[888, 225, 252, 250], "shape"], [[910, 249, 42, 28], "textbox"], [[910, 299, 200, 30], "textbox"], [[910, 343, 200, 76], "textbox"], [[910, 445, 208, 0], "shape"], [[910, 454, 200, 15], "textbox"],
      [[72, 584, 1136, 0], "shape"],
    ]);
    deleteTexts(presentation, records, slideNo, [
      "GUARDRAILS FOR EVERY SURFACE",
      "KEEP  proportions intact",
      "PAIR  violet with ink",
      "SIGNAL  gradient = movement",
      "PROVE  evidence before decoration",
      "Next review point: after the first implementation PR, validate the logo at mobile, desktop, dark-surface and export sizes.",
    ]);
    setText(resolve(presentation, byText(records, slideNo, "ONE SYSTEM. MANY MOMENTS. SAME FEELING.")), "PROGRESS YOU CAN PROVE.", { left: 72, top: 665, width: 480, height: 22 }, { fontSize: 12, bold: true, color: C.white });
    setText(resolve(presentation, byText(records, slideNo, "PROMOVA")), "PROMOVA", { left: 1090, top: 665, width: 118, height: 22 }, { fontSize: 12, bold: true, color: C.white, alignment: "right" });

    setNotes(slide, [
      "Encerrar sem introduzir novos conceitos. Pausar após a frase secundária.",
      "[Sources]",
      "- User-provided Promova TCC pitch brief, current conversation.",
      "- User-provided Promova visual identity guide.",
    ]);
  }

  const finalInspect = await presentation.inspect({
    kind: "slide,textbox,shape,image,notes,layout",
    include: "id,slide,name,title,text,textPreview,bbox,bboxUnit",
    maxChars: 500000,
  });
  await fs.writeFile(`${tmpDir}/final-inspect.ndjson`, finalInspect.ndjson, "utf8");

  for (let index = 0; index < presentation.slides.items.length; index += 1) {
    const slide = presentation.slides.getItem(index);
    const stem = `slide-${String(index + 1).padStart(2, "0")}`;
    await saveBlob(`${renderDir}/${stem}.png`, await presentation.export({ slide, format: "png", scale: 1 }));
    const layout = await slide.export({ format: "layout" });
    await fs.writeFile(`${layoutDir}/${stem}.layout.json`, await layout.text(), "utf8");
  }
  await saveBlob(`${tmpDir}/final-montage.webp`, await presentation.export({ format: "webp", montage: true, scale: 1 }));

  const pptx = await PresentationFile.exportPptx(presentation);
  await pptx.save(finalPptxPath);
  const stat = await fs.stat(finalPptxPath);
  console.log(JSON.stringify({ finalPptxPath, bytes: stat.size, slides: presentation.slides.items.length, renderDir, layoutDir }, null, 2));
}

main().catch((error) => {
  console.error(error?.stack || error);
  process.exitCode = 1;
});
