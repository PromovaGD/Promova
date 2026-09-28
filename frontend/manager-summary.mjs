const number = value => new Intl.NumberFormat("pt-BR").format(Number(value || 0));
const percent = (part, total) => total ? Math.round(Number(part || 0) / total * 100) : 0;

export function renderManagerSummary(data, query, ui) {
  const [summary, teams, roles, members] = data;
  const { esc, sel, date, dateTime, labelStatus } = ui;
  const demo = query.preview === "demo";
  const period = query.period || "30";
  const custom = period === "custom";
  const periodText = date(summary.from) + " a " + date(summary.to);
  const filters = '<form id="summary-filter" class="executive-filters" aria-label="Filtros do sumário">'
    + '<label>Período<select name="period">'
    + option("7", "Últimos 7 dias", period, sel, esc)
    + option("30", "Últimos 30 dias", period, sel, esc)
    + option("90", "Últimos 90 dias", period, sel, esc)
    + option("custom", "Personalizado", period, sel, esc)
    + '</select></label>'
    + '<div class="dashboard-custom-dates" ' + (custom ? "" : "hidden") + '>'
    + '<label>De<input type="date" name="from" value="' + esc(query.from || summary.from) + '" ' + (custom ? "" : "disabled") + '></label>'
    + '<label>Até<input type="date" name="to" value="' + esc(query.to || summary.to) + '" ' + (custom ? "" : "disabled") + '></label></div>'
    + '<label>Equipe<select name="team" ' + (demo ? "disabled" : "") + '><option value="">Todas as equipes</option>'
    + teams.map(team => option(team.id, team.name, query.team, sel, esc)).join("") + '</select></label>'
    + '<label>Cargo<select name="role" ' + (demo ? "disabled" : "") + '><option value="">Todos os cargos</option>'
    + roles.filter(role => role.status === "ACTIVE").map(role => option(role.id, role.name, query.role, sel, esc)).join("") + '</select></label>'
    + '<label>Pessoa<select name="member" ' + (demo ? "disabled" : "") + '><option value="">Todas as pessoas</option>'
    + members.map(person => option(person.id, person.name, query.member, sel, esc)).join("") + '</select></label>'
    + '<label>Visualização<select name="preview">'
    + option("real", "Dados do espaço", demo ? "demo" : "real", sel, esc)
    + option("demo", "Prévia com dados fictícios", demo ? "demo" : "real", sel, esc)
    + '</select></label>'
    + '<button class="button primary" type="submit">Atualizar sumário</button>'
    + '</form>';

  const pr = summary.pullRequests || {};
  const activity = Number(summary.evidenceCount || 0) + Number(summary.analysisCount || 0) + Number(pr.registered || 0);
  const lede = activity
    ? 'No período, ' + number(summary.peopleCount) + ' pessoas no escopo registraram '
      + number(summary.evidenceCount) + ' evidências e ' + number(summary.analysisCount)
      + ' análises. ' + number(pr.registered) + ' PRs foram capturados; '
      + number(pr.merged) + ' constam como mesclados.'
    : 'Ainda não há evidências, análises ou PRs capturados neste período e escopo. Ajuste os filtros para explorar outro recorte.';
  const notice = demo
    ? '<div class="executive-demo-notice"><strong>Prévia fictícia</strong><span>Os números e exemplos abaixo são ilustrativos. A seleção de equipe, cargo e pessoa fica disponível em “Dados do espaço”.</span></div>'
    : summary.demoData
      ? '<div class="executive-demo-notice"><strong>Ambiente de demonstração</strong><span>Este espaço contém registros de exemplo do ambiente de testes.</span></div>'
      : '';
  const metrics = [
    ["Pessoas acompanhadas", summary.peopleCount, "No escopo selecionado", "people"],
    ["Evidências", summary.evidenceCount, "Criadas no período", "evidence"],
    ["Análises", summary.analysisCount, "Geradas no período", "analyses"],
    ["PRs capturados", pr.registered, "Registrados no período", "pulls"],
    ["PRs mesclados", pr.merged, "Entre os capturados", "merged"],
  ];
  const metricCards = metrics.map(([label, value, hint, tone]) => '<article class="executive-metric ' + tone + '">'
    + '<span>' + esc(label) + '</span><strong>' + number(value) + '</strong><small>' + esc(hint) + '</small></article>').join("");
  const signals = [
    [summary.unreviewedCount, "Análises sem revisão", "Fila atual", "/manage/reviews?status=unreviewed"],
    [summary.needsContextCount, "Pedem contexto", "Situação atual", "/manage/reviews?status=needs-context"],
    [summary.overdueObjectiveCount, "Objetivos vencidos", "Situação atual", "/manage/people"],
  ];
  const signalCards = signals.map(([value, label, note, href]) => {
    const content = '<strong>' + number(value) + '</strong><span>' + esc(label) + '</span><small>' + note + '</small>'
      + (demo ? '' : '<b aria-hidden="true">↗</b>');
    return demo ? '<article class="executive-signal">' + content + '</article>'
      : '<a class="executive-signal" data-link href="' + href + '">' + content + '</a>';
  }).join("");
  const trend = summary.trend || [];
  const maxWeek = Math.max(1, ...trend.map(item => Math.max(Number(item.evidence || 0), Number(item.analyses || 0))));
  const trendRows = trend.length && trend.some(item => item.evidence || item.analyses)
    ? trend.map(item => '<li><span class="executive-week-label">' + shortDate(item.start) + '–' + shortDate(item.end) + '</span>'
      + '<div class="executive-week-bars"><span class="executive-bar evidence" style="width:' + percent(item.evidence, maxWeek) + '%" title="' + number(item.evidence) + ' evidências"></span>'
      + '<span class="executive-bar analyses" style="width:' + percent(item.analyses, maxWeek) + '%" title="' + number(item.analyses) + ' análises"></span></div>'
      + '<span class="executive-week-values">' + number(item.evidence) + ' / ' + number(item.analyses) + '</span></li>').join("")
    : '<li class="executive-empty">Nenhuma atividade semanal neste período.</li>';
  const statusTotal = (summary.statuses || []).reduce((total, item) => total + Number(item.count || 0), 0);
  const statuses = statusTotal
    ? summary.statuses.map(item => distributionRow(labelStatus(item.status), item.count, statusTotal, statusTone(item.status), esc)).join("")
    : '<p class="executive-empty">Nenhuma análise no período.</p>';
  const sourceTotal = (summary.sources || []).reduce((total, item) => total + Number(item.count || 0), 0);
  const sources = (summary.sources || []).map(item => distributionRow(item.source, item.count, sourceTotal, "source", esc)).join("")
    || '<p class="executive-empty">Nenhuma evidência no período.</p>';
  const highlights = (summary.highlights || []).length
    ? summary.highlights.map(item => {
      const title = item.title?.trim() || 'Análise de ' + (item.source || 'evidência');
      const content = '<span class="executive-highlight-top"><span class="executive-tag">' + esc(item.source || 'Fonte não informada')
        + '</span><small>' + dateTime(item.createdAt) + '</small></span><strong>' + esc(title) + '</strong>'
        + '<span class="executive-highlight-meta">' + (demo ? '' : esc(item.employeeName || 'Pessoa não informada') + ' · ')
        + labelStatus(item.reviewStatus) + '</span>'
        + '<span class="executive-highlight-foot">Impacto estimado: ' + esc(impactLabel(item.impactLevel)) + '<b aria-hidden="true">↗</b></span>';
      return demo ? '<article class="executive-highlight">' + content + '</article>'
        : '<a class="executive-highlight" data-link href="/workspace/people/' + Number(item.employeeId) + '/analyses/' + Number(item.analysisId) + '">' + content + '</a>';
    }).join("")
    : '<p class="executive-empty">Nenhuma análise para destacar neste período.</p>';

  return '<div class="executive-summary">' + filters + notice
    + '<section class="executive-hero" aria-labelledby="executive-hero-title"><div>'
    + '<p class="executive-kicker">Panorama da equipe · ' + esc(periodText) + '</p>'
    + '<h2 id="executive-hero-title">O que aconteceu recentemente?</h2><p>' + esc(lede) + '</p>'
    + '<span class="executive-hero-foot">Dados registrados na Promova · atualização ao abrir a página</span></div>'
    + '<div class="executive-hero-figure"><strong>' + number(summary.analysisCount) + '</strong><span>análises no período</span></div></section>'
    + '<section aria-labelledby="executive-numbers-title"><div class="executive-section-heading"><div><p class="executive-eyebrow">Em números</p><h2 id="executive-numbers-title">Visão rápida</h2></div><span>' + esc(periodText) + '</span></div>'
    + '<div class="executive-metrics">' + metricCards + '</div></section>'
    + '<section class="executive-section" aria-labelledby="executive-flow-title"><div class="executive-section-heading"><div><p class="executive-eyebrow">Ritmo</p><h2 id="executive-flow-title">Atividade ao longo do período</h2></div></div>'
    + '<div class="executive-chart-grid"><article class="executive-card"><h3>Evidências e análises por semana</h3><p>Volume registrado, agrupado pela semana do período.</p>'
    + '<div class="executive-legend"><span><i class="evidence"></i>Evidências</span><span><i class="analyses"></i>Análises</span></div>'
    + '<ol class="executive-week-list">' + trendRows + '</ol></article>'
    + '<article class="executive-card"><h3>Situação das análises</h3><p>Status atual das análises geradas no período.</p>'
    + '<div class="executive-distribution">' + statuses + '</div></article></div></section>'
    + '<section class="executive-section" aria-labelledby="executive-highlights-title"><div class="executive-section-heading"><div><p class="executive-eyebrow">Registros recentes</p><h2 id="executive-highlights-title">Destaques do período</h2></div>'
    + '<span>Até 6 análises mais recentes</span></div><div class="executive-highlights">' + highlights + '</div></section>'
    + '<section class="executive-section" aria-labelledby="executive-attention-title"><div class="executive-section-heading"><div><p class="executive-eyebrow">Para acompanhar</p><h2 id="executive-attention-title">Sinais que pedem atenção</h2></div><span>Valores da situação atual</span></div>'
    + '<div class="executive-signals">' + signalCards + '</div></section>'
    + '<section class="executive-section" aria-labelledby="executive-sources-title"><div class="executive-section-heading"><div><p class="executive-eyebrow">Origem</p><h2 id="executive-sources-title">Fontes das evidências</h2></div></div>'
    + '<article class="executive-card"><p>Distribuição das evidências capturadas no período.</p><div class="executive-distribution">' + sources + '</div></article></section>'
    + '<p class="executive-method">Os números refletem registros disponíveis no escopo da gestão. PRs consideram apenas os capturados pela Promova. Revisões e objetivos pendentes mostram a situação atual.</p>'
    + '</div>';
}

function option(value, label, selected, sel, esc) {
  return '<option value="' + esc(value) + '" ' + sel(selected, value) + '>' + esc(label) + '</option>';
}

function statusTone(status) {
  return ({ UNREVIEWED: "pending", NEEDS_CONTEXT: "context", ACCEPTED: "accepted" })[status] || "source";
}

function impactLabel(value) {
  return ({ HIGH: "alto", MEDIUM: "médio", LOW: "baixo" })[String(value || "").toUpperCase()]
    || value || "não informado";
}

function shortDate(value) {
  return value ? value.slice(8, 10) + "/" + value.slice(5, 7) : "—";
}

function distributionRow(label, count, total, tone, esc) {
  const share = percent(count, total);
  return '<div class="executive-distribution-row"><div><span>' + esc(label || 'Não informada') + '</span><strong>' + number(count) + ' <small>' + share + '%</small></strong></div>'
    + '<div class="executive-track"><span class="' + tone + '" style="width:' + share + '%"></span></div></div>';
}

export function createDemoSummary(query = {}) {
  const today = new Intl.DateTimeFormat("en-CA", { timeZone: "America/Sao_Paulo", year: "numeric", month: "2-digit", day: "2-digit" }).format(new Date());
  const to = query.period === "custom" && query.to ? query.to : today;
  const defaultDays = query.period === "7" ? 7 : query.period === "90" ? 90 : 30;
  const from = query.period === "custom" && query.from ? query.from : shiftDate(to, 1 - defaultDays);
  const days = Math.max(1, Math.round((new Date(to + "T12:00:00Z") - new Date(from + "T12:00:00Z")) / 86400000) + 1);
  const scale = days / 30;
  const scaled = value => Math.max(0, Math.round(value * scale));
  const evidenceCount = scaled(48), analysisCount = scaled(31);
  const statuses = [
    { status: "UNREVIEWED", count: scaled(8) },
    { status: "NEEDS_CONTEXT", count: scaled(5) },
    { status: "ACCEPTED", count: Math.max(0, analysisCount - scaled(13)) },
  ];
  const trend = [];
  for (let start = from, index = 0; start <= to; index += 1) {
    const end = shiftDate(start, 6) > to ? to : shiftDate(start, 6);
    const length = Math.round((new Date(end + "T12:00:00Z") - new Date(start + "T12:00:00Z")) / 86400000) + 1;
    trend.push({ start, end, weight: length * (1 + index % 3) });
    start = shiftDate(end, 1);
  }
  const totalWeight = trend.reduce((total, point) => total + point.weight, 0);
  let assignedEvidence = 0, assignedAnalyses = 0;
  trend.forEach((point, index) => {
    point.evidence = index === trend.length - 1 ? evidenceCount - assignedEvidence : Math.floor(evidenceCount * point.weight / totalWeight);
    point.analyses = index === trend.length - 1 ? analysisCount - assignedAnalyses : Math.floor(analysisCount * point.weight / totalWeight);
    point.reviews = Math.round(point.analyses * 0.6);
    assignedEvidence += point.evidence;
    assignedAnalyses += point.analyses;
    delete point.weight;
  });
  const sample = [
    ["Redução do tempo de processamento no fluxo de pagamentos", "HIGH", "ACCEPTED"],
    ["Automação de testes para o serviço de autenticação", "MEDIUM", "UNREVIEWED"],
    ["Aprimoramento da observabilidade de integrações", "HIGH", "ACCEPTED"],
    ["Correção de falha na conciliação de eventos", "MEDIUM", "NEEDS_CONTEXT"],
  ];
  return {
    from, to, demoData: true, peopleCount: 12, evidenceCount, analysisCount,
    unreviewedCount: 8, needsContextCount: 5, overdueObjectiveCount: 2,
    trend, statuses,
    sources: [{ source: "GitHub", count: scaled(41) }, { source: "Registro manual", count: Math.max(0, evidenceCount - scaled(41)) }],
    pullRequests: { registered: scaled(36), merged: scaled(27) },
    highlights: sample.slice(0, analysisCount).map(([title, impactLevel, reviewStatus], index) => ({
      analysisId: index + 1, employeeId: index + 1, title, impactLevel, reviewStatus,
      source: "GitHub", createdAt: shiftDate(to, -Math.min(index * 2, days - 1)) + "T15:00:00Z",
    })),
  };
}

function shiftDate(value, days) {
  const date = new Date(value + "T12:00:00Z");
  date.setUTCDate(date.getUTCDate() + days);
  return date.toISOString().slice(0, 10);
}
