export const LEVELS = ["L1", "L2", "L3", "L4", "L5"];

export function levelIndex(level) {
  return LEVELS.indexOf(level);
}

export function readinessFor(impactLevel, targetLevel) {
  return levelIndex(impactLevel) >= levelIndex(targetLevel)
    ? `Esta evidência está alinhada com o seu alvo atual de ${targetLevel}.`
    : `Esta evidência ainda está abaixo do seu alvo de ${targetLevel}, então pode ser fortalecida com resultados mensuráveis.`;
}

export function formatTimestamp(isoDate) {
  return new Intl.DateTimeFormat("pt-BR", {
    dateStyle: "medium",
    timeStyle: "short",
  }).format(new Date(isoDate));
}

export function formatCount(value) {
  return new Intl.NumberFormat("pt-BR").format(Number(value) || 0);
}

export function formatPercentage(value) {
  return `${Math.round(Number(value) || 0)}%`;
}

export function badgeClass(level) {
  if (level === "L4" || level === "L5") {
    return "badge success";
  }

  if (level === "L3") {
    return "badge info";
  }

  return "badge neutral";
}

export function sourceBadgeClass(source) {
  const normalized = String(source || "").toLowerCase();

  if (normalized.includes("github")) {
    return "github";
  }

  if (normalized.includes("jira")) {
    return "jira";
  }

  if (normalized.includes("slack")) {
    return "slack";
  }

  return "neutral";
}

export function confidenceLabel(confidence) {
  const labels = {
    high: "Alta confiança",
    medium: "Confiança média",
    low: "Baixa confiança",
  };

  return labels[confidence] || "Confiança média";
}

export function roleLabel(role) {
  return role === "MANAGER" ? "Gestor" : "Funcionário";
}
