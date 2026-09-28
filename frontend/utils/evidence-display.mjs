const titleLine = /^T[ií]tulo\s*:\s*(.*)$/i;
const descriptionLine = /^Descri[cç][aã]o\/resumo\s*:\s*(.*)$/i;
const sourceLine = /^(?:GitHub\s*[-–]|Contexto\/perfil|Autor GitHub|Estado:|Merge:|Fechamento:|Volume coletado|Link (?:do|p[uú]blico)|Leitura preparada)/i;
const descriptionEnd = /^(?:Link (?:do|p[uú]blico)|Leitura preparada)/i;

export function evidenceDisplay(evidence) {
  if (evidence?.title && evidence?.summary && evidence?.origin) {
    return { title: evidence.title, summary: evidence.summary, origin: evidence.origin };
  }
  const lines = String(evidence?.content || "").replace(/\r/g, "").split("\n").map(line => line.trim());
  const namedTitle = lines.find(line => titleLine.test(line))?.replace(titleLine, "$1").trim();
  const meta = compact(evidence?.sourceMeta);
  const prMeta = meta.match(/^(PR\s*#\d+)\s+[-–—]\s+(.+?)\s+[-–—]\s+(.+)$/i);
  const metaTitle = prMeta?.[3];
  const meaningful = lines.filter(line => line && !titleLine.test(line) && !descriptionLine.test(line) && !sourceLine.test(line));
  const descriptionIndex = lines.findIndex(line => descriptionLine.test(line));
  const descriptionParts = [];
  if (descriptionIndex >= 0) {
    descriptionParts.push(lines[descriptionIndex].replace(descriptionLine, "$1"));
    for (const line of lines.slice(descriptionIndex + 1)) {
      if (descriptionEnd.test(line)) break;
      if (line) descriptionParts.push(line);
    }
  }
  const description = compact(descriptionParts.join(" "));

  const title = clip(compact(evidence?.analysisTitle) || namedTitle || metaTitle || meaningful[0] || (!/^PR\s*#\d+/i.test(meta) ? meta : "") || "Descrição não informada", 100);
  const summaryText = description || meaningful.find(line => compact(line) !== compact(title)) || "";
  const summary = summaryText ? clip(summaryText, 170) : "A fonte não traz um resumo adicional.";
  const origin = prMeta ? `${prMeta[1]} · ${prMeta[2]}` : meta || compact(evidence?.externalId) || "Origem não informada";
  return { title, summary, origin };
}

export function selectedEvidence(items, id) {
  return items.find(item => Number(item.id) === Number(id)) || items[0] || null;
}

function compact(value) {
  return String(value || "").replace(/\s+/g, " ").trim();
}

function clip(value, limit) {
  const text = compact(value);
  if (text.length <= limit) return text;
  const lastSpace = text.slice(0, limit + 1).lastIndexOf(" ");
  return `${text.slice(0, lastSpace > limit * 0.65 ? lastSpace : limit).trimEnd()}…`;
}
