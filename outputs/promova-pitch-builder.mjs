import fs from "node:fs/promises";
import path from "node:path";
import { pathToFileURL } from "node:url";

const artifactEntrypoint =
  "C:/Users/João/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/@oai/artifact-tool/dist/artifact_tool.mjs";
const { Presentation, PresentationFile } = await import(pathToFileURL(artifactEntrypoint).href);

const ROOT = "C:/Users/João/Documents/Projects/FIAP/Startup";
const OUTPUT_DIR = path.join(ROOT, "outputs");
const FINAL_PPTX = path.join(OUTPUT_DIR, "promova-pitch-5min.pptx");
const PREVIEW_DIR = "C:/Users/JOO~1/AppData/Local/Temp/codex-presentations/manual-promova-pitch/promova-pitch/tmp/preview";
const LAYOUT_DIR = "C:/Users/JOO~1/AppData/Local/Temp/codex-presentations/manual-promova-pitch/promova-pitch/tmp/layout";
const QA_DIR = "C:/Users/JOO~1/AppData/Local/Temp/codex-presentations/manual-promova-pitch/promova-pitch/tmp/qa";

const W = 1280;
const H = 720;
const page = { left: 76, top: 58, width: 1128, height: 604 };

const colors = {
  ink: "#14213D",
  muted: "#5F6C7B",
  paper: "#F8FAFC",
  white: "#FFFFFF",
  line: "#D9E2EC",
  teal: "#00A896",
  green: "#2D6A4F",
  coral: "#F26A4B",
  yellow: "#F4B942",
  purple: "#6D5BD0",
  blue: "#2563EB",
  softTeal: "#E6F7F4",
  softCoral: "#FFF0EC",
  softYellow: "#FFF7DD",
  softPurple: "#F0EEFF",
  softBlue: "#EAF1FF",
};

async function saveBlob(filePath, blob) {
  await fs.mkdir(path.dirname(filePath), { recursive: true });
  await fs.writeFile(filePath, Buffer.from(await blob.arrayBuffer()));
}

function addShape(slide, geometry, position, fill, line = { style: "solid", fill: "none", width: 0 }, extra = {}) {
  return slide.shapes.add({
    geometry,
    position,
    fill,
    line,
    ...extra,
  });
}

function addText(slide, text, position, style = {}) {
  const shape = slide.shapes.add({
    geometry: "textbox",
    position,
    fill: "none",
    line: { style: "solid", fill: "none", width: 0 },
  });
  shape.text = text;
  shape.text.style = {
    fontSize: style.fontSize ?? 22,
    bold: style.bold ?? false,
    color: style.color ?? colors.ink,
    typeface: style.typeface ?? "Aptos",
    alignment: style.alignment ?? "left",
  };
  return shape;
}

function addTitle(slide, eyebrow, title, subtitle) {
  addText(slide, eyebrow.toUpperCase(), { left: page.left, top: page.top, width: 760, height: 28 }, {
    fontSize: 16,
    bold: true,
    color: colors.teal,
  });
  addText(slide, title, { left: page.left, top: page.top + 48, width: 900, height: 92 }, {
    fontSize: 40,
    bold: true,
    color: colors.ink,
    typeface: "Aptos Display",
  });
  if (subtitle) {
    addText(slide, subtitle, { left: page.left, top: page.top + 146, width: 900, height: 64 }, {
      fontSize: 21,
      color: colors.muted,
    });
  }
}

function addFooter(slide, n) {
  addText(slide, `Promova · Pitch de 5 minutos · ${n}/8`, { left: page.left, top: 675, width: 420, height: 24 }, {
    fontSize: 13,
    color: "#8090A0",
  });
}

function addBullet(slide, title, copy, x, y, w, accent, bg) {
  addShape(slide, "roundRect", { left: x, top: y, width: w, height: 132 }, bg, {
    style: "solid",
    fill: colors.line,
    width: 1,
  }, { borderRadius: "rounded-lg" });
  addShape(slide, "rect", { left: x, top: y, width: 8, height: 132 }, accent, {
    style: "solid",
    fill: accent,
    width: 0,
  });
  addText(slide, title, { left: x + 26, top: y + 22, width: w - 46, height: 32 }, {
    fontSize: 24,
    bold: true,
    color: colors.ink,
  });
  addText(slide, copy, { left: x + 26, top: y + 62, width: w - 46, height: 54 }, {
    fontSize: 17,
    color: colors.muted,
  });
}

function addMetric(slide, label, value, copy, x, y, w, fill, accent) {
  addShape(slide, "roundRect", { left: x, top: y, width: w, height: 150 }, fill, {
    style: "solid",
    fill: colors.line,
    width: 1,
  }, { borderRadius: "rounded-lg" });
  addText(slide, label.toUpperCase(), { left: x + 26, top: y + 22, width: w - 52, height: 24 }, {
    fontSize: 13,
    bold: true,
    color: accent,
  });
  addText(slide, value, { left: x + 26, top: y + 52, width: w - 52, height: 46 }, {
    fontSize: 34,
    bold: true,
    color: colors.ink,
  });
  addText(slide, copy, { left: x + 26, top: y + 104, width: w - 52, height: 34 }, {
    fontSize: 16,
    color: colors.muted,
  });
}

function setNotes(slide, lines) {
  slide.speakerNotes.textFrame.setText(lines);
  slide.speakerNotes.setVisible(true);
}

function makeDeck() {
  const deck = Presentation.create({ slideSize: { width: W, height: H } });

  {
    const slide = deck.slides.add();
    slide.background.fill = colors.paper;
    addShape(slide, "rect", { left: 0, top: 0, width: W, height: H }, colors.paper);
    addShape(slide, "rect", { left: 0, top: 0, width: W, height: 18 }, colors.teal);
    addShape(slide, "roundRect", { left: 790, top: 104, width: 330, height: 430 }, colors.white, {
      style: "solid",
      fill: colors.line,
      width: 1,
    }, { borderRadius: "rounded-lg" });
    addShape(slide, "roundRect", { left: 835, top: 154, width: 240, height: 54 }, colors.softTeal, {
      style: "solid",
      fill: "#C4ECE5",
      width: 1,
    }, { borderRadius: "rounded-lg" });
    addText(slide, "Evidência capturada", { left: 860, top: 171, width: 190, height: 22 }, { fontSize: 18, bold: true, color: colors.green });
    addShape(slide, "roundRect", { left: 835, top: 238, width: 240, height: 88 }, colors.softBlue, {
      style: "solid",
      fill: "#CADBFF",
      width: 1,
    }, { borderRadius: "rounded-lg" });
    addText(slide, "IA + framework", { left: 860, top: 267, width: 190, height: 26 }, { fontSize: 22, bold: true, color: colors.blue });
    addShape(slide, "roundRect", { left: 835, top: 356, width: 240, height: 108 }, colors.softYellow, {
      style: "solid",
      fill: "#F5D36C",
      width: 1,
    }, { borderRadius: "rounded-lg" });
    addText(slide, "Decisão mais justa", { left: 860, top: 394, width: 190, height: 28 }, { fontSize: 22, bold: true, color: "#9A6B00" });
    addText(slide, "Promova", { left: page.left, top: 132, width: 560, height: 72 }, {
      fontSize: 58,
      bold: true,
      color: colors.ink,
      typeface: "Aptos Display",
    });
    addText(slide, "Promoções justas baseadas em evidências reais", { left: page.left, top: 220, width: 650, height: 112 }, {
      fontSize: 35,
      bold: true,
      color: colors.ink,
      typeface: "Aptos Display",
    });
    addText(slide, "Um pitch de 5 minutos para apresentar o problema, a solução e o caminho de evolução do produto.", { left: page.left, top: 358, width: 610, height: 64 }, {
      fontSize: 22,
      color: colors.muted,
    });
    addText(slide, "Projeto FIAP · Startup", { left: page.left, top: 548, width: 340, height: 32 }, { fontSize: 18, bold: true, color: colors.teal });
    setNotes(slide, [
      "Abrir com uma frase direta: o Promova nasceu para tornar conversas de carreira mais justas, transparentes e baseadas em evidências reais.",
      "Em uma gravação de 5 minutos, não comece explicando tecnologia. Comece pela dor humana: pessoas querem crescer, mas nem sempre sabem quais evidências comprovam esse crescimento.",
      "Transição: hoje, esse processo ainda depende demais de memória, percepção e coleta manual.",
    ]);
  }

  {
    const slide = deck.slides.add();
    slide.background.fill = colors.white;
    addTitle(slide, "Problema", "Promoções ainda dependem de sinais frágeis", "Em times de tecnologia, o trabalho acontece em várias ferramentas, mas a decisão de carreira costuma acontecer em uma reunião.");
    addBullet(slide, "Coleta manual", "Gestores gastam tempo procurando exemplos em PRs, tarefas, mensagens e memória recente.", 76, 302, 350, colors.coral, colors.softCoral);
    addBullet(slide, "Baixa transparência", "A pessoa não enxerga com clareza o que falta para evoluir de nível.", 465, 302, 350, colors.purple, colors.softPurple);
    addBullet(slide, "Risco de viés", "Quando faltam evidências, opinião e lembrança recente pesam mais do que impacto acumulado.", 854, 302, 350, colors.yellow, colors.softYellow);
    addFooter(slide, 2);
    setNotes(slide, [
      "Explique o problema com um exemplo: um desenvolvedor pode ter melhorado testes, feito revisões importantes e apoiado colegas, mas essas evidências ficam espalhadas.",
      "Na hora da avaliação, parte do contexto se perde. Isso prejudica tanto a pessoa avaliada quanto a liderança, que precisa justificar decisões.",
      "A dor central: falta um sistema contínuo de evidências conectado ao framework de carreira.",
    ]);
  }

  {
    const slide = deck.slides.add();
    slide.background.fill = colors.paper;
    addTitle(slide, "Oportunidade", "Carreira precisa virar um processo contínuo", "A oportunidade está em transformar sinais do trabalho diário em uma base confiável para conversas de evolução.");
    addMetric(slide, "Para pessoas", "Clareza", "Entender o que já foi demonstrado e o que precisa ser reforçado.", 76, 292, 340, colors.white, colors.teal);
    addMetric(slide, "Para gestores", "Tempo", "Reduzir coleta manual e apoiar decisões com exemplos concretos.", 470, 292, 340, colors.white, colors.coral);
    addMetric(slide, "Para empresas", "Consistência", "Aplicar critérios de carreira com mais padronização entre times.", 864, 292, 340, colors.white, colors.purple);
    addShape(slide, "rect", { left: 150, top: 512, width: 980, height: 2 }, colors.line, { style: "solid", fill: colors.line, width: 0 });
    addText(slide, "Tese: quanto mais cedo a evidência é capturada, menos subjetiva fica a conversa de promoção.", { left: 214, top: 540, width: 850, height: 42 }, {
      fontSize: 24,
      bold: true,
      color: colors.ink,
      alignment: "center",
    });
    addFooter(slide, 3);
    setNotes(slide, [
      "Aqui vocês mostram que o problema não é só operacional; ele afeta confiança no processo.",
      "O Promova não tenta substituir a liderança. Ele melhora a qualidade da conversa ao trazer evidências organizadas.",
      "Transição: a partir dessa oportunidade, nossa solução conecta fontes reais de trabalho a uma análise estruturada.",
    ]);
  }

  {
    const slide = deck.slides.add();
    slide.background.fill = colors.white;
    addTitle(slide, "Solução", "Promova organiza evidências e traduz impacto", "A plataforma captura sinais do trabalho, compara com o framework de carreira e devolve uma leitura revisável.");
    const y = 305;
    const items = [
      ["1", "Captura", "PRs, commits, revisões e evidências registradas", colors.teal, colors.softTeal],
      ["2", "Análise", "IA avalia nível, competências e justificativa", colors.blue, colors.softBlue],
      ["3", "Painel", "Histórico filtrável de evidências e progresso", colors.purple, colors.softPurple],
      ["4", "Conversa", "Base objetiva para feedback e promoção", colors.coral, colors.softCoral],
    ];
    items.forEach(([num, title, copy, accent, bg], index) => {
      const x = 76 + index * 282;
      addShape(slide, "roundRect", { left: x, top: y, width: 238, height: 178 }, bg, {
        style: "solid",
        fill: colors.line,
        width: 1,
      }, { borderRadius: "rounded-lg" });
      addShape(slide, "ellipse", { left: x + 24, top: y + 24, width: 42, height: 42 }, accent, {
        style: "solid",
        fill: accent,
        width: 0,
      });
      addText(slide, num, { left: x + 37, top: y + 31, width: 18, height: 24 }, { fontSize: 22, bold: true, color: colors.white, alignment: "center" });
      addText(slide, title, { left: x + 24, top: y + 82, width: 190, height: 30 }, { fontSize: 25, bold: true, color: colors.ink });
      addText(slide, copy, { left: x + 24, top: y + 120, width: 188, height: 42 }, { fontSize: 16, color: colors.muted });
      if (index < 3) {
        addText(slide, "→", { left: x + 248, top: y + 70, width: 34, height: 40 }, { fontSize: 35, bold: true, color: "#A7B3C2", alignment: "center" });
      }
    });
    addFooter(slide, 4);
    setNotes(slide, [
      "Explique o fluxo em quatro passos, sem entrar demais em detalhes técnicos.",
      "O diferencial é que a evidência vem do trabalho real e é lida contra critérios explícitos de carreira, como L3, L4 e L5.",
      "Reforce que o resultado é revisável: a IA apoia, mas a decisão final continua humana.",
    ]);
  }

  {
    const slide = deck.slides.add();
    slide.background.fill = colors.paper;
    addTitle(slide, "Produto", "O MVP já demonstra o ciclo completo", "O protótipo conecta evidências, análise e visualização em painel.");
    addShape(slide, "roundRect", { left: 76, top: 258, width: 528, height: 318 }, colors.white, {
      style: "solid",
      fill: colors.line,
      width: 1,
    }, { borderRadius: "rounded-lg" });
    addText(slide, "Evidência importada do GitHub", { left: 110, top: 290, width: 430, height: 30 }, { fontSize: 24, bold: true, color: colors.ink });
    addText(slide, "Refatorou módulo crítico, aumentou cobertura de testes e revisou PRs do time.", { left: 110, top: 338, width: 420, height: 60 }, { fontSize: 19, color: colors.muted });
    addShape(slide, "roundRect", { left: 110, top: 425, width: 160, height: 52 }, colors.softTeal, { style: "solid", fill: "#BEE9E1", width: 1 }, { borderRadius: "rounded-lg" });
    addText(slide, "Impacto L4", { left: 132, top: 440, width: 120, height: 24 }, { fontSize: 20, bold: true, color: colors.green });
    addShape(slide, "roundRect", { left: 286, top: 425, width: 206, height: 52 }, colors.softPurple, { style: "solid", fill: "#D9D2FF", width: 1 }, { borderRadius: "rounded-lg" });
    addText(slide, "Confiança média", { left: 312, top: 440, width: 150, height: 24 }, { fontSize: 20, bold: true, color: colors.purple });
    addText(slide, "Sugestão: incluir métrica de resultado e contribuição específica.", { left: 110, top: 508, width: 430, height: 38 }, { fontSize: 17, color: colors.muted });
    addShape(slide, "roundRect", { left: 676, top: 258, width: 528, height: 318 }, colors.white, {
      style: "solid",
      fill: colors.line,
      width: 1,
    }, { borderRadius: "rounded-lg" });
    addText(slide, "Painel de evolução", { left: 710, top: 290, width: 380, height: 30 }, { fontSize: 24, bold: true, color: colors.ink });
    const bars = [
      ["Code Quality", 315, colors.teal],
      ["Ownership", 245, colors.blue],
      ["Leadership", 160, colors.coral],
    ];
    bars.forEach(([label, width, color], index) => {
      const yy = 350 + index * 62;
      addText(slide, label, { left: 710, top: yy, width: 145, height: 24 }, { fontSize: 18, bold: true, color: colors.ink });
      addShape(slide, "roundRect", { left: 870, top: yy + 2, width: 286, height: 22 }, "#EDF2F7", { style: "solid", fill: "#EDF2F7", width: 0 }, { borderRadius: "rounded-lg" });
      addShape(slide, "roundRect", { left: 870, top: yy + 2, width, height: 22 }, color, { style: "solid", fill: color, width: 0 }, { borderRadius: "rounded-lg" });
    });
    addText(slide, "Visão por período, fonte e colaborador para apoiar conversas recorrentes.", { left: 710, top: 520, width: 420, height: 46 }, { fontSize: 17, color: colors.muted });
    addFooter(slide, 5);
    setNotes(slide, [
      "Este é o slide de demonstração. Mostrem o que o usuário vê: uma evidência capturada, a classificação de impacto, competências identificadas e sugestões.",
      "Citem que o backend já tem endpoints de análise, integração com GitHub, motor mock e opção de análise real via OpenRouter.",
      "Transição: depois de mostrar o produto, deixem claro para quem ele gera valor.",
    ]);
  }

  {
    const slide = deck.slides.add();
    slide.background.fill = colors.white;
    addTitle(slide, "Valor", "O Promova melhora a conversa para todos os lados", "A solução não é apenas um repositório; ela muda a qualidade do processo de evolução.");
    addBullet(slide, "Engenheiros", "Acompanham evidências, entendem lacunas e chegam mais preparados para feedback.", 76, 284, 350, colors.teal, colors.softTeal);
    addBullet(slide, "Gestores", "Reduzem esforço de coleta e defendem decisões com exemplos objetivos.", 465, 284, 350, colors.coral, colors.softCoral);
    addBullet(slide, "Empresa", "Ganha consistência, transparência e menor risco de decisões enviesadas.", 854, 284, 350, colors.purple, colors.softPurple);
    addText(slide, "Diferencial: evidência contínua + framework explícito + IA como apoio, não como decisão automática.", { left: 158, top: 526, width: 964, height: 44 }, {
      fontSize: 25,
      bold: true,
      color: colors.ink,
      alignment: "center",
    });
    addFooter(slide, 6);
    setNotes(slide, [
      "Aqui o objetivo é mostrar benefício por stakeholder.",
      "Para a pessoa desenvolvedora, é clareza. Para o gestor, é economia de tempo e melhor justificativa. Para a empresa, é consistência.",
      "Reforce a frase final como posicionamento: o Promova apoia decisões humanas com evidências mais fortes.",
    ]);
  }

  {
    const slide = deck.slides.add();
    slide.background.fill = colors.paper;
    addTitle(slide, "Negócio e evolução", "Começamos com um nicho claro: times de tecnologia", "A hipótese comercial é B2B SaaS para squads e empresas que precisam padronizar carreira técnica.");
    addShape(slide, "roundRect", { left: 76, top: 250, width: 336, height: 250 }, colors.white, { style: "solid", fill: colors.line, width: 1 }, { borderRadius: "rounded-lg" });
    addText(slide, "Entrada", { left: 110, top: 282, width: 250, height: 30 }, { fontSize: 26, bold: true, color: colors.ink });
    addText(slide, "Pilotos com squads de engenharia, começando por GitHub e framework de carreira.", { left: 110, top: 338, width: 260, height: 92 }, { fontSize: 19, color: colors.muted });
    addShape(slide, "roundRect", { left: 472, top: 250, width: 336, height: 250 }, colors.white, { style: "solid", fill: colors.line, width: 1 }, { borderRadius: "rounded-lg" });
    addText(slide, "Monetização", { left: 506, top: 282, width: 250, height: 30 }, { fontSize: 26, bold: true, color: colors.ink });
    addText(slide, "Assinatura por usuário ou por time, com planos para liderança, RH e integrações.", { left: 506, top: 338, width: 260, height: 92 }, { fontSize: 19, color: colors.muted });
    addShape(slide, "roundRect", { left: 868, top: 250, width: 336, height: 250 }, colors.white, { style: "solid", fill: colors.line, width: 1 }, { borderRadius: "rounded-lg" });
    addText(slide, "Roadmap", { left: 902, top: 282, width: 250, height: 30 }, { fontSize: 26, bold: true, color: colors.ink });
    addText(slide, "Persistência, Jira, Slack, dashboards de time e métricas de evolução ao longo do tempo.", { left: 902, top: 338, width: 260, height: 100 }, { fontSize: 19, color: colors.muted });
    addText(slide, "O MVP valida fluxo e valor antes de escalar integrações.", { left: 208, top: 552, width: 864, height: 38 }, { fontSize: 24, bold: true, color: colors.green, alignment: "center" });
    addFooter(slide, 7);
    setNotes(slide, [
      "Este slide cobre os elementos clássicos de pitch: mercado inicial, modelo e próximos passos.",
      "Como ainda é um projeto/protótipo, use linguagem de hipótese: a tese é vender para empresas com times de engenharia que já têm framework de carreira.",
      "Fechem dizendo que o MVP reduz risco porque valida o fluxo antes de investir em muitas integrações.",
    ]);
  }

  {
    const slide = deck.slides.add();
    slide.background.fill = colors.ink;
    addShape(slide, "rect", { left: 0, top: 0, width: W, height: H }, colors.ink);
    addShape(slide, "rect", { left: 0, top: 0, width: W, height: 18 }, colors.teal);
    addText(slide, "Fechamento", { left: page.left, top: 104, width: 420, height: 34 }, { fontSize: 18, bold: true, color: colors.teal });
    addText(slide, "Queremos transformar promoção em uma conversa com evidências, não em uma aposta de memória.", { left: page.left, top: 164, width: 880, height: 160 }, {
      fontSize: 42,
      bold: true,
      color: colors.white,
      typeface: "Aptos Display",
    });
    addText(slide, "Próximo passo: validar o piloto com usuários reais, coletar feedback do fluxo e evoluir integrações com ferramentas do dia a dia.", { left: page.left, top: 376, width: 760, height: 78 }, {
      fontSize: 23,
      color: "#DDE7F3",
    });
    addShape(slide, "roundRect", { left: 850, top: 382, width: 250, height: 80 }, colors.teal, { style: "solid", fill: colors.teal, width: 0 }, { borderRadius: "rounded-lg" });
    addText(slide, "Promova", { left: 900, top: 404, width: 150, height: 32 }, { fontSize: 28, bold: true, color: colors.white, alignment: "center" });
    addText(slide, "Obrigado", { left: page.left, top: 590, width: 220, height: 34 }, { fontSize: 24, bold: true, color: colors.yellow });
    setNotes(slide, [
      "Finalize com uma frase memorável: promoção não deveria depender de quem lembra melhor, e sim de evidências consistentes.",
      "Reforce o pedido: validar com usuários reais, testar o fluxo em squads e entender quais integrações geram mais valor.",
      "Termine com segurança e deixe espaço para perguntas.",
    ]);
  }

  return deck;
}

async function main() {
  await fs.mkdir(OUTPUT_DIR, { recursive: true });
  await fs.mkdir(PREVIEW_DIR, { recursive: true });
  await fs.mkdir(LAYOUT_DIR, { recursive: true });
  await fs.mkdir(QA_DIR, { recursive: true });

  const presentation = makeDeck();

  for (const [index, slide] of presentation.slides.items.entries()) {
    const stem = `slide-${String(index + 1).padStart(2, "0")}`;
    await saveBlob(path.join(PREVIEW_DIR, `${stem}.png`), await presentation.export({ slide, format: "png", scale: 1 }));
    await fs.writeFile(path.join(LAYOUT_DIR, `${stem}.layout.json`), await (await slide.export({ format: "layout" })).text());
  }

  await saveBlob(path.join(PREVIEW_DIR, "promova-pitch-montage.webp"), await presentation.export({ format: "webp", montage: true, scale: 1 }));

  const inspect = await presentation.inspect({
    kind: "slide,textbox,shape,notes,layout",
    maxChars: 20000,
  });
  await fs.writeFile(path.join(QA_DIR, "inspect.ndjson"), inspect.ndjson);
  await fs.writeFile(
    path.join(QA_DIR, "source-notes.txt"),
    [
      "Deck informed by local repository files only: README.md, plan/PLAN.md, plan/BACKEND-IMPLEMENTATION-PLAN.md, frontend views, and career-framework.json.",
      "Default template inspection was attempted but skipped after the environment failed to provide a working unzip command required by the template-following helper.",
      "No external research or third-party assets were used.",
    ].join("\n"),
  );

  const pptx = await PresentationFile.exportPptx(presentation);
  await pptx.save(FINAL_PPTX);
  console.log(FINAL_PPTX);
}

main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
