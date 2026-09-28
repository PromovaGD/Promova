export function renderManagerDashboard(data, query, ui) {
  const [summary, people, teams, roles, members] = data;
  const { esc, sel, date, dateTime, labelStatus, panel, pager, empty } = ui;
  const rows = people.items || [];
  const period = query.period || "30";
  const custom = period === "custom";
  const mobileFilters = typeof window !== "undefined" && window.matchMedia("(max-width: 899px)").matches;
  const filters = '<details class="dashboard-filter-drawer" ' + (mobileFilters ? "" : "open")
    + '><summary>Filtros do dashboard</summary><form id="dashboard-filter" class="dashboard-filters" aria-label="Filtros do dashboard">'
    + '<label>Período<select name="period">'
    + option("30", "Últimos 30 dias", period, sel, esc)
    + option("90", "Últimos 90 dias", period, sel, esc)
    + option("custom", "Personalizado", period, sel, esc)
    + '</select></label>'
    + '<div class="dashboard-custom-dates" ' + (custom ? "" : "hidden") + '>'
    + '<label>De<input type="date" name="from" value="' + esc(query.from || summary.from) + '" ' + (custom ? "" : "disabled") + '></label>'
    + '<label>Até<input type="date" name="to" value="' + esc(query.to || summary.to) + '" ' + (custom ? "" : "disabled") + '></label></div>'
    + '<label>Equipe<select name="team"><option value="">Todas as equipes</option>'
    + teams.map(team => option(team.id, team.name + " (" + team.members + ")", query.team, sel, esc)).join("")
    + '</select></label>'
    + '<label>Cargo<select name="role"><option value="">Todos os cargos</option>'
    + roles.filter(role => role.status === "ACTIVE").map(role => option(role.id, role.name, query.role, sel, esc)).join("")
    + '</select></label>'
    + '<label>Pessoa<select name="member"><option value="">Todas as pessoas</option>'
    + members.map(person => option(person.id, person.name, query.member, sel, esc)).join("")
    + '</select></label>'
    + '<button class="button primary" type="submit">Aplicar</button>'
    + '<a class="button" data-link href="/manage/dashboard">Limpar</a></form></details>';

  const metrics = [
    ["Pessoas acompanhadas", summary.peopleCount, "Escopo atual", "/manage/people"],
    ["Evidências", summary.evidenceCount, "No período", null],
    ["Análises geradas", summary.analysisCount, "No período", null],
    ["Sem revisão", summary.unreviewedCount, "Fila atual", "/manage/reviews?status=unreviewed"],
    ["Objetivos vencidos", summary.overdueObjectiveCount, "Situação atual", null],
  ];
  const cards = '<div class="dashboard-metrics">' + metrics.map(([label, value, hint, href]) => {
    const inner = '<span>' + esc(label) + '</span><strong>' + Number(value || 0) + '</strong><small>' + esc(hint) + '</small>';
    return '<article>' + (href ? '<a data-link href="' + href + '">' + inner + '</a>' : inner) + '</article>';
  }).join("") + '</div>';

  const attention = (summary.attention || []).length
    ? '<div class="dashboard-attention-list">' + summary.attention.map(item => {
      const objective = item.kind === "OBJECTIVE_OVERDUE";
      const href = objective
        ? '/manage/people/' + item.employeeId + '/career-plan/objectives/' + item.resourceId
        : '/workspace/people/' + item.employeeId + '/analyses/' + item.resourceId + '?view=review';
      return '<a class="dashboard-attention-item" data-link href="' + href + '">'
        + '<span class="badge ' + (objective ? "" : "purple") + '">' + (objective ? "Prazo vencido" : labelStatus(item.kind)) + '</span>'
        + '<strong>' + esc(item.employeeName) + '</strong><span>' + esc(item.title || "Registro sem título") + '</span>'
        + '<small>' + (objective ? "Prazo" : "Desde") + ' ' + dateTime(item.date) + '</small><span aria-hidden="true">›</span></a>';
    }).join("") + '</div>'
    : empty("Nenhuma pendência prioritária neste escopo.");

  const trend = summary.trend || [];
  const trendTotals = trend.reduce((totals, point) => ({
    evidence: totals.evidence + Number(point.evidence || 0),
    analyses: totals.analyses + Number(point.analyses || 0),
    reviews: totals.reviews + Number(point.reviews || 0),
  }), { evidence: 0, analyses: 0, reviews: 0 });
  const chart = trend.some(point => point.evidence || point.analyses || point.reviews)
    ? '<p class="dashboard-chart-caption">Progressão acumulada de cada indicador no período · escala de 0 a 100%</p>'
      + lineChart(trend, esc, date)
      + '<ul class="dashboard-chart-legend"><li><i class="evidence"></i>Evidências <strong>' + trendTotals.evidence + '</strong></li>'
      + '<li><i class="analyses"></i>Análises <strong>' + trendTotals.analyses + '</strong></li>'
      + '<li><i class="reviews"></i>Revisões <strong>' + trendTotals.reviews + '</strong></li></ul>'
      + '<details><summary>Ver valores por semana</summary><div class="dashboard-trend-values">'
      + trend.map(point => '<p><strong>' + date(point.start) + '–' + date(point.end)
        + '</strong>: ' + point.evidence + ' evidências, ' + point.analyses
        + ' análises, ' + point.reviews + ' revisões na semana.</p>').join("")
      + '</div></details>'
    : empty("Ainda não há atividade no período selecionado.");

  const statuses = summary.statuses || [];
  const statusTotal = statuses.reduce((sum, item) => sum + Number(item.count || 0), 0);
  const statusBody = statusTotal
    ? '<div class="dashboard-status">'
      + donut(statuses, statusTotal)
      + '<ul>' + statuses.map(item => '<li><span class="dashboard-status-dot ' + esc(item.status) + '"></span>'
        + '<span>' + labelStatus(item.status) + '</span><strong>' + Number(item.count || 0) + '</strong>'
        + '<small>' + Math.round(Number(item.count || 0) / statusTotal * 100) + '%</small></li>').join("")
      + '</ul></div>'
    : empty("Nenhuma análise criada no período.");

  const sourceTotal = (summary.sources || []).reduce((sum, item) => sum + Number(item.count || 0), 0);
  const sources = sourceTotal
    ? '<div class="dashboard-sources">' + summary.sources.map(item =>
      '<div><span>' + esc(item.source) + '</span><strong>' + Number(item.count || 0)
      + '</strong><div class="progress"><span style="width:'
      + Math.round(Number(item.count || 0) / sourceTotal * 100) + '%"></span></div></div>').join("") + '</div>'
    : empty("Nenhuma evidência capturada no período.");

  const personBody = rows.length
    ? '<ul class="dashboard-person-list">' + rows.map(person => personRow(person, esc, dateTime)).join("") + '</ul>'
    : empty("Nenhuma pessoa encontrada neste escopo.");

  const extra = '<section class="dashboard-insights" aria-labelledby="dashboard-insights-title">'
    + '<div class="dashboard-insights-head"><div><h2 id="dashboard-insights-title">Sinais para acompanhar</h2>'
    + '<p>Indicadores que ajudam a priorizar o acompanhamento da equipe.</p></div></div>'
    + '<div class="dashboard-signal-grid">'
    + signal(summary.needsContextCount, 'Precisam de contexto', 'Análises para complementar', 'purple')
    + signal(summary.dueSoonObjectiveCount, 'Prazos próximos', 'Objetivos em até ' + summary.attentionDays + ' dias', 'amber')
    + signal(summary.unreviewedOlderThanThreshold, 'Revisões aguardando', 'Há mais de ' + summary.attentionDays + ' dias', 'blue')
    + '</div><details class="dashboard-more-insights"><summary>Mais indicadores <span aria-hidden="true">⌄</span></summary>'
    + '<dl><div><dt>Pessoas sem objetivo ativo</dt><dd>' + summary.peopleWithoutActiveObjective + '</dd></div>'
    + '<div><dt>Sem captura recente (' + summary.attentionDays + ' dias)</dt><dd>' + summary.peopleWithoutRecentCapture + '</dd></div>'
    + '<div><dt>Última captura</dt><dd>' + (summary.lastCaptureAt ? dateTime(summary.lastCaptureAt) : 'Nenhuma') + '</dd></div>'
    + '<div><dt>Critérios com evidência</dt><dd>' + summary.criteriaWithEvidence + ' de ' + summary.criteriaCount + '</dd></div>'
    + '<div><dt>Falhas de análise no período</dt><dd>' + summary.failedAnalysisRuns + '</dd></div>'
    + (summary.medianFirstReviewHours == null ? '' : '<div><dt>Mediana até a primeira revisão</dt><dd>'
      + Number(summary.medianFirstReviewHours).toFixed(1) + ' h · ' + summary.firstReviewSample + ' análises</dd></div>')
    + '</dl></details></section>';

  const pr = summary.pullRequests || {};
  const prBody = Number(pr.registered || 0)
    ? '<div class="dashboard-pr-metrics">'
      + prMetric("PRs registrados", pr.registered)
      + prMetric("Mesclados", pr.merged)
      + prMetric("Abertos", pr.open)
      + prMetric("Fechados sem merge", pr.closedWithoutMerge)
      + prMetric("Com code review", pr.withHumanReview)
      + '</div><p class="note">Commits registrados: ' + Number(pr.commitsOnKnownPulls || 0)
      + ' em ' + Number(pr.commitCountSample || 0) + ' de ' + Number(pr.registered || 0) + ' PRs. '
      + 'Mediana até o primeiro pedido de revisão: '
      + (pr.medianFirstRequestHours == null ? "sem amostra" : Number(pr.medianFirstRequestHours).toFixed(1) + " h")
      + ' (' + Number(pr.firstRequestSample || 0) + ' PRs). Mediana até o primeiro code review: '
      + (pr.medianFirstReviewHours == null ? "sem amostra" : Number(pr.medianFirstReviewHours).toFixed(1) + " h")
      + ' (' + Number(pr.firstReviewSample || 0) + ' PRs). Revisões do GitHub; diferentes das revisões de análise da Promova.</p>'
    : '<div class="dashboard-pr-empty"><strong>Nenhum PR registrado no período</strong>'
      + '<p>Os indicadores incluem apenas PRs capturados pelo Promova.</p></div>';
  const prPanel = panel("PRs capturados no GitHub", prBody,
    Number(pr.registered || 0) && !summary.demoData
      ? '<button type="button" class="button" data-action="refresh-pr-reviews">Atualizar code reviews</button>'
      : '');

  return '<div class="manager-dashboard"><p class="dashboard-intro">Acompanhe evidências, análises e objetivos das pessoas sob sua gestão.'
    + (summary.demoData ? ' <span class="badge purple">Dados de demonstração</span>' : '') + '</p>'
    + filters + '<p class="dashboard-period-note">Atividade de ' + date(summary.from) + ' a ' + date(summary.to)
    + '. Pendências e prazos mostram a situação atual.</p>'
    + cards + extra
    + '<div class="dashboard-grid">'
    + panel("Precisa de atenção", attention, '<a data-link href="/manage/reviews?status=unreviewed">Abrir revisões →</a>')
    + panel("Progressão da equipe", chart) + '</div><div class="dashboard-grid">'
    + panel("Situação atual das análises do período", statusBody)
    + panel("Fontes das evidências", sources) + '</div>'
    + prPanel
    + panel("Acompanhamento das pessoas (" + Number(people.total || 0) + ")", personBody, pager(people, "/manage/dashboard"))
    + '</div>';
}

function prMetric(label, value) {
  return '<div><span>' + label + '</span><strong>' + Number(value || 0) + '</strong></div>';
}

function option(value, label, selected, sel, esc) {
  return '<option value="' + esc(value) + '" ' + sel(selected, value) + '>' + esc(label) + '</option>';
}

function lineChart(trend, esc, date) {
  const series = [
    ["evidence", "Evidências"], ["analyses", "Análises"], ["reviews", "Revisões"],
  ];
  const totals = { evidence: 0, analyses: 0, reviews: 0 };
  const cumulative = trend.map(point => {
    for (const [key] of series) totals[key] += Number(point[key] || 0);
    return { ...point, evidence: totals.evidence, analyses: totals.analyses, reviews: totals.reviews };
  });
  const left = 37, top = 12, width = 584, height = 172;
  const x = index => left + (cumulative.length === 1 ? width / 2 : index * width / (cumulative.length - 1));
  const y = (key, value) => top + height * (1 - (totals[key] ? value / totals[key] : 0));
  const ticks = [0, 25, 50, 75, 100];
  const labelEvery = Math.max(1, Math.ceil(cumulative.length / 6));
  return '<div class="dashboard-line-chart"><svg viewBox="0 0 640 220" role="img" aria-labelledby="dashboard-line-title dashboard-line-description">'
    + '<title id="dashboard-line-title">Progressão acumulada da equipe</title>'
    + '<desc id="dashboard-line-description">Cada linha mostra a porcentagem acumulada do total de evidências, análises ou revisões no período selecionado.</desc>'
    + ticks.map(value => '<g class="dashboard-chart-grid"><line x1="' + left + '" x2="' + (left + width)
      + '" y1="' + (top + height * (1 - value / 100)) + '" y2="' + (top + height * (1 - value / 100))
      + '"></line><text x="' + (left - 10) + '" y="' + (top + height * (1 - value / 100) + 4)
      + '" text-anchor="end">' + value + '%</text></g>').join('')
    + cumulative.map((point, index) => index % labelEvery === 0 || index === cumulative.length - 1
      ? '<text class="dashboard-chart-date" x="' + x(index) + '" y="211" text-anchor="middle">'
        + esc(point.start.slice(5).split('-').reverse().join('/')) + '</text>' : '').join('')
    + series.map(([key, label]) => '<polyline class="dashboard-line-series ' + key + '" points="'
      + cumulative.map((point, index) => x(index) + ',' + y(key, point[key])).join(' ') + '"></polyline>'
      + cumulative.map((point, index) => '<circle class="dashboard-line-point ' + key + '" cx="' + x(index)
        + '" cy="' + y(key, point[key]) + '" r="4"><title>' + esc(label) + ': ' + point[key]
        + ' acumuladas até ' + esc(date(point.end)) + ' ('
        + (totals[key] ? Math.round(point[key] / totals[key] * 100) : 0) + '% do total)</title></circle>').join('')).join('')
    + '</svg></div>';
}

function signal(value, label, description, tone) {
  return '<div class="dashboard-signal ' + tone + '"><strong>' + Number(value || 0) + '</strong><span>'
    + label + '</span><small>' + description + '</small></div>';
}

function personRow(person, esc, dateTime) {
  const count = Number(person.unreviewedAnalyses || 0);
  const pending = Number(person.pendingEvidence || 0);
  const summary = count ? count + (count === 1 ? ' análise sem revisão' : ' análises sem revisão')
    : pending ? pending + (pending === 1 ? ' evidência pendente' : ' evidências pendentes')
      : Number(person.activeObjectives || 0) === 0 ? 'Sem objetivo ativo' : 'Acompanhamento em dia';
  const initials = String(person.name || '').trim().split(/\s+/).slice(0, 2).map(part => part[0] || '').join('').toUpperCase() || 'P';
  const tone = (Number(person.id) % 4 + 4) % 4;
  return '<li><a class="dashboard-person-row" data-link href="/manage/people/' + Number(person.id) + '/career-plan">'
    + '<span class="dashboard-person-profile"><span class="dashboard-person-avatar tone-' + tone + '" aria-hidden="true">'
    + esc(initials) + '</span><span class="dashboard-person-identity"><strong>' + esc(person.name) + '</strong>'
    + '<small>' + esc(person.jobRoleName || 'Cargo não definido') + ' · ' + esc(person.currentLevel || 'Nível não definido')
    + ' → ' + esc(person.targetLevel || 'Meta não definida') + '</small>'
    + '<span class="dashboard-person-description">' + summary + '</span></span></span>'
    + personStat(person.activeObjectives, 'Objetivos ativos')
    + personStat(person.pendingEvidence, 'Evidências pendentes')
    + personStat(person.unreviewedAnalyses, 'Sem revisão')
    + '<span class="dashboard-person-activity"><small>Última atividade</small><strong>'
    + (person.lastActivity ? dateTime(person.lastActivity) : 'Sem atividade recente') + '</strong></span>'
    + '<span class="dashboard-person-chevron" aria-hidden="true">›</span></a></li>';
}

function personStat(value, label) {
  return '<span class="dashboard-person-stat"><strong>' + Number(value || 0) + '</strong><small>' + label + '</small></span>';
}

function donut(statuses, total) {
  const first = Number(statuses.find(item => item.status === "UNREVIEWED")?.count || 0) / total * 100;
  const second = Number(statuses.find(item => item.status === "NEEDS_CONTEXT")?.count || 0) / total * 100;
  return '<div class="dashboard-donut" style="--first:' + first + '%;--second:' + (first + second)
    + '%"><span><strong>' + total + '</strong><small>análises</small></span></div>';
}
