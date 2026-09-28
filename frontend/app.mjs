import { loginUser, logoutUser, registerUser, fetchCurrentUser } from "./services/auth-api.mjs";
import { clearAuthSession, loadAuthToken, loadAuthUser, saveAuthSession } from "./services/auth-store.mjs";
import { api } from "./services/api.mjs";
import { renderManagerDashboard } from "./manager-dashboard.mjs";
import { renderManagerSummary, createDemoSummary } from "./manager-summary.mjs";
import { renderManagerTeams } from "./manager-teams.mjs";
import { canAccess, canonicalUrl, matchRoute, roleHome, safeReturnTo } from "./router.mjs";
import { evidenceDisplay, selectedEvidence } from "./utils/evidence-display.mjs";
import { escapeHtml as esc } from "./utils/html.mjs";

const state = { user: loadAuthUser(), validated: false, route: null, data: null, error: null, loading: false, epoch: 0, controller: null, drafts: new Map(), shellRole: null, returnTo: null, expiring: false };
const titles = { overview:"Visão geral", inbox:"Caixa de entrada", analyses:"Análises", reports:"Relatórios de análises", framework:"Framework", criterion:"Critério", "career-plan":"Plano de carreira", integrations:"Integrações", github:"GitHub", "github-import":"Importar pull request", "manager-dashboard":"Visão da gestão", "manager-summary":"Sumários executivos", "manager-teams":"Equipes", people:"Pessoas", "person-plan":"Plano de carreira", "objective-new":"Novo objetivo", objective:"Editar objetivo", "person-evidence":"Evidências", "person-analyses":"Análises", reviews:"Revisões", terminology:"Terminologia da organização", roles:"Cargos", "role-new":"Novo cargo", role:"Cargo", "manager-framework":"Framework oficial", "evidence-detail":"Evidência capturada", "analysis-detail":"Análise salva" };
const employeeNav = [["/app/overview","Visão geral"],["/app/inbox?status=pending","Caixa de entrada"],["/app/analyses","Análises"],["/app/framework","Framework"],["/app/career-plan","Plano de carreira"],["/app/integrations","Integrações"]];
const managerNav = [["/manage/dashboard","Visão da gestão"],["/manage/summaries","Sumários executivos"],["/manage/people","Pessoas"],["/manage/reviews?status=unreviewed","Revisões"],["/manage/teams","Equipes"],["/manage/career/roles","Configuração de carreira"]];

export async function startApp(root) {
  document.body.classList.add("app-ui");
  root.innerHTML = `<a class="skip-link" href="#main-content">Pular para o conteúdo</a><div id="application-root"></div><div id="toast" class="toast" role="status" aria-live="polite"></div>${dialogMarkup()}`;
  document.addEventListener("click", onArchiveRole, true);
  document.addEventListener("click", onDrawerNavigation, true);
  document.addEventListener("click", onCompactFilterClick);
  document.addEventListener("click", onInboxClick);
  document.addEventListener("click", onClick);
  document.addEventListener("keydown", onCompactFilterKeydown);
  document.addEventListener("submit", onDashboardSubmit, true);
  document.addEventListener("submit", onTeamSubmit, true);
  document.addEventListener("submit", onSubmit);
  document.addEventListener("click", onTeamClick, true);
  document.addEventListener("click", onDashboardClick, true);
  document.addEventListener("change", onDashboardPeriodChange);
  document.addEventListener("input", onInput);
  document.addEventListener("change", onInput);
  bindActionDialog();
  window.addEventListener("popstate", onPopState);
  window.addEventListener("promova:auth-expired", () => expireSession());
  window.addEventListener("beforeunload", (event) => { if (state.drafts.size) { event.preventDefault(); event.returnValue = ""; } });
  await navigate(location.href, { history: "none" });
}

async function navigate(destination, { history: historyMode = "push", focus = true } = {}) {
  const target = new URL(destination, location.href);
  if (target.origin !== location.origin) return;
  const next = matchRoute(target);
  const canonical = canonicalUrl(next);
  if (["employee", "manager", "resource"].includes(next.access)) state.returnTo = canonical;
  if (historyMode === "push") window.history.pushState({}, "", canonical);
  if (historyMode === "replace" && `${location.pathname}${location.search}` !== canonical) window.history.replaceState({}, "", canonical);
  if (historyMode === "none" && next.access !== "auth" && `${location.pathname}${location.search}` !== canonical) window.history.replaceState({}, "", canonical);
  state.route = next;
  state.error = null;
  state.data = null;
  state.epoch += 1;
  state.controller?.abort();
  state.controller = new AbortController();

  if (next.access === "auth" && loadAuthToken() && state.user && !state.validated) {
    try {
      state.user = await fetchCurrentUser();
      state.validated = true;
      return go(roleHome(state.user), "replace");
    } catch (error) {
      if (error.status !== 401) {
        state.error = "Não foi possível validar sua sessão. Você ainda pode entrar novamente.";
      }
    }
  }

  if (["employee", "manager", "resource"].includes(next.access)) {
    if (!loadAuthToken()) return redirectToLogin();
    if (!state.validated) {
      state.loading = true; render();
      try { state.user = await fetchCurrentUser(); state.validated = true; }
      catch (error) { if (error.status === 401) return redirectToLogin(); state.loading = false; state.error = "Não foi possível validar sua sessão."; render(); return; }
    }
    if (!canAccess(next, state.user)) { state.loading = false; state.error = "Você não tem permissão para acessar este espaço."; renderForbidden(); return; }
  }
  if (next.access === "auth" && state.validated && state.user) return go(roleHome(state.user), "replace");

  state.loading = !["landing", "login", "register", "not-found"].includes(next.id);
  render();
  if (state.loading) await loadRoute(state.epoch, state.controller.signal);
  if (focus) requestAnimationFrame(() => document.querySelector("#route-title")?.focus({ preventScroll: true }));
}

async function loadRoute(epoch, signal) {
  try {
    state.data = await routeData(state.route, signal);
    if (epoch !== state.epoch) return;
    if (state.route.id === "inbox") reconcileInboxSelection();
    state.loading = false; render();
  } catch (error) {
    if (error.name === "AbortError" || epoch !== state.epoch) return;
    state.loading = false; state.error = error.status === 404 ? "Este recurso não existe ou não está disponível para você." : error.message || "Não foi possível carregar esta página."; render();
  }
}

async function routeData(route, signal) {
  const q = route.query, p = route.params, manager = state.user?.role === "MANAGER";
  switch (route.id) {
    case "overview": return Promise.all([api.analyses({ page:1 }, signal), api.insights({}, signal), api.evidences({ status:"PENDING", page:1 }, signal)]);
    case "inbox": return api.inbox(q, signal);
    case "analyses": return api.analyses(q, signal);
    case "reports": case "framework": case "criterion": return Promise.all([api.insights(q, signal), api.framework(false, signal)]);
    case "career-plan": return api.profile(signal);
    case "integrations": case "github": return api.githubSettings(signal);
    case "manager-dashboard": return Promise.all([
      api.managerDashboard(q, signal), api.dashboardPeople(q, signal),
      api.managerTeams(signal), api.roles(signal), api.teamVisibleMembers(signal),
    ]);
    case "manager-summary": return q.preview === "demo"
      ? [createDemoSummary(q), [], [], []]
      : Promise.all([
        api.managerDashboard(q, signal), api.managerTeams(signal),
        api.roles(signal), api.teamVisibleMembers(signal),
      ]);
    case "manager-teams": {
      const teams=await api.managerTeams(signal);
      const selected=teams.find(team=>team.id===Number(q.team))||teams[0];
      return [teams,selected?await api.teamMembers(selected.id,signal):[],await api.teamCandidates(signal),selected?.id||null];
    }
    case "people": return Promise.all([api.employees(q, signal), api.roles(signal)]);
    case "person-plan": case "person-evidence": case "person-analyses": {
      const directoryParams={q:q.dq,role:q.drole,level:q.dlevel,page:q.dpage};
      const detail=route.id==="person-plan"?api.employeePlan(p.employeeId,signal):route.id==="person-evidence"
        ?api.employeeEvidences(p.employeeId,{...q,status:q.status?.toUpperCase()},signal)
        :api.employeeAnalyses(p.employeeId,q,signal);
      const [directory,roles,person,result,settings]=await Promise.all([
        api.employees(directoryParams,signal),api.roles(signal),api.employee(p.employeeId,signal),
        detail,route.id==="person-plan"?api.settings(signal):Promise.resolve(null),
      ]);
      return {directory,roles,person,detail:result,settings};
    }
    case "objective-new": case "objective": return Promise.all([api.employee(p.employeeId, signal), api.employeePlan(p.employeeId, signal), api.settings(signal)]);
    case "reviews": return api.reviewQueue({ ...q, status:q.status || "unreviewed" }, signal);
    case "terminology": return api.settings(signal);
    case "roles": case "role-new": case "role": return Promise.all([api.roles(signal), api.framework(true, signal)]);
    case "manager-framework": return api.framework(true, signal);
    case "evidence-detail": return api.evidence(p.ownerId, p.evidenceId, manager, signal);
    case "analysis-detail": return Promise.all([api.analysis(p.ownerId, p.analysisId, manager, signal), api.reviews(p.ownerId, p.analysisId, manager, signal)]);
    default: return null;
  }
}

function render() {
  const mount = document.querySelector("#application-root");
  if (!mount) return;
  if (state.route.id === "landing") { document.title="Promova · Evidências para conversas de carreira"; mount.innerHTML=landing(); return; }
  if (["login","register"].includes(state.route.id)) { document.title=`${state.route.id === "login" ? "Entrar" : "Criar conta"} · Promova`; mount.innerHTML=auth(); return; }
  if (state.route.id === "not-found") { document.title="Página não encontrada · Promova"; mount.innerHTML=publicState("Página não encontrada", "O endereço não corresponde a uma página do Promova."); return; }
  if (!state.user && state.error) { mount.innerHTML=publicState("Não foi possível validar a sessão", state.error, true); return; }
  document.title=`${titles[state.route.id] || "Promova"} · Promova`;
  mount.innerHTML=shell(workspace());
  bindDrawerDialog();
}

function shell(content) {
  const manager=state.user?.role === "MANAGER", nav=manager?managerNav:employeeNav;
  return `<div class="app-shell"><aside class="sidebar" id="navigation"><a class="brand" href="${roleHome(state.user)}" data-link><img src="/frontend/assets/promova-designer-symbol.png" alt=""><span>promova</span></a><p class="workspace-label">${manager?"Gestão":"Minha carreira"}</p><nav aria-label="Principal">${nav.map(([href,label])=>`<a href="${href}" data-link ${navActive(href)?'aria-current="page"':''}><span aria-hidden="true">${navIcon(label)}</span>${esc(label)}</a>`).join("")}</nav><div class="sidebar-foot"><a href="/" data-link>Site público</a><button type="button" data-action="logout">Sair</button></div></aside><div class="shell-main"><header class="topbar"><button class="menu-button" type="button" data-action="open-menu" aria-controls="navigation" aria-expanded="false">☰ <span>Menu</span></button><span>${manager?"Espaço da gestão":"Espaço pessoal"}</span><button type="button" class="account" data-action="account">${initials(state.user?.name)} <span>${esc(state.user?.name || "Conta")}</span></button></header><main id="main-content" tabindex="-1">${content}</main></div></div>`;
}

function workspace() {
  const peopleRoute=state.route.id==="people"||state.route.id.startsWith("person-");
  const title=peopleRoute?"Pessoas":titles[state.route.id]||"Promova";
  const intro=state.route.id==="inbox"?"Leia a fonte antes de analisar. Evidências dispensadas ficam em outra visualização.":peopleRoute?"Encontre uma pessoa e abra seu contexto de carreira.":"";
  return `<section class="workspace"><header class="page-head ${intro?"page-head-intro":""}"><div><p class="eyebrow">${state.user?.role === "MANAGER" ? "Espaço de gestão" : "Meu espaço de carreira"}</p><h1 id="route-title" tabindex="-1">${esc(title)}</h1>${intro?`<p class="page-intro">${intro}</p>`:""}</div>${primaryAction()}</header>${state.loading?loading():state.error?errorState():renderRoute()}</section>`;
}

function renderRoute() {
  const id=state.route.id;
  if (id==="overview") return overview();
  if (id==="inbox") return inbox();
  if (id==="analyses") return analyses();
  if (id==="reports") return reports();
  if (id==="framework" || id==="criterion") return framework();
  if (id==="career-plan") return careerPlan(false);
  if (id==="integrations") return integrations();
  if (id==="github") return github();
  if (id==="github-import") return githubImport();
  if (id==="manager-dashboard") return managerDashboard();
  if (id==="manager-summary") return managerSummary();
  if (id==="manager-teams") return managerTeams();
  if (id==="people") return people();
  if (id==="person-plan") return peopleWorkspace(careerPlan(true));
  if (["objective-new","objective"].includes(id)) return careerPlan(true);
  if (id==="person-evidence") return peopleWorkspace(personEvidence());
  if (id==="person-analyses") return peopleWorkspace(personAnalyses());
  if (id==="reviews") return reviews();
  if (id==="terminology") return terminology();
  if (["roles","role-new","role"].includes(id)) return roles();
  if (id==="manager-framework") return managerFramework();
  if (id==="evidence-detail") return evidenceDetail();
  if (id==="analysis-detail") return analysisDetail();
  return errorState();
}

function overview() {
  const [analyses,insights,evidence]=state.data, items=pageItems(analyses), pending=pageItems(evidence);
  return `<div class="metrics"><article><span>Análises salvas</span><strong>${analyses.total ?? items.length}</strong><small>Todo o período</small></article><article><span>Critérios com apoio</span><strong>${insights.criteriaWithEvidence || 0}<i> / ${insights.criteriaCount || 0}</i></strong><small>Framework atual</small></article><article><span>Pendências</span><strong>${evidence.total ?? pending.length}</strong><small>Para revisar</small></article></div><div class="callout"><div><strong>${pending.length?"Há evidências aguardando sua leitura":"Sua caixa de entrada está em dia"}</strong><p>Leia a fonte e acrescente contexto verificável antes de analisar.</p></div><a class="button primary" data-link href="/app/inbox?status=pending">Abrir caixa de entrada</a></div><div class="grid-two">${panel("Análises recentes", items.length?items.slice(0,2).map(analysisRow).join(""):empty("Nenhuma análise salva ainda."), `<a data-link href="/app/analyses">Todas as análises →</a>`)}${panel("Seu framework", `<p><span class="badge purple">${esc(items[0]?.currentLevel || "Nível atual")}</span></p><p>${insights.criteriaWithEvidence || 0} de ${insights.criteriaCount || 0} critérios possuem evidência de apoio.</p><div class="progress"><span style="width:${insights.criteriaCount?Math.round(insights.criteriaWithEvidence/insights.criteriaCount*100):0}%"></span></div><p class="note">Ausência de evidência não é uma avaliação negativa.</p>`, `<a data-link href="/app/framework">Explorar critérios →</a>`)}</div>`;
}

function managerDashboard() {
  return renderManagerDashboard(state.data, state.route.query, {
    esc, sel, date, dateTime, labelStatus, panel, pager, empty,
  });
}

function managerSummary() {
  return renderManagerSummary(state.data, state.route.query, { esc, sel, date, dateTime, labelStatus });
}

function managerTeams() {
  return renderManagerTeams(state.data, { esc, panel, empty });
}

async function onTeamSubmit(event) {
  const form = event.target;
  if (!["create-team-form", "assign-team-member-form"].includes(form.id)) return;
  event.preventDefault();
  event.stopImmediatePropagation();
  const values = new FormData(form);
  try {
    setFormBusy(form, true);
    if (form.id === "create-team-form") {
      const team = await api.createTeam(values.get("name"));
      toast("Equipe criada.");
      return go("/manage/teams?team=" + team.id);
    }
    await api.assignTeamMember(Number(form.dataset.teamId), Number(values.get("employeeId")));
    toast("Pessoa adicionada à equipe.");
    return navigate(location.href, {history:"none"});
  } catch (error) {
    toast(error.message || "Não foi possível salvar a equipe.", true);
  } finally {
    setFormBusy(form, false);
  }
}

function onTeamClick(event) {
  const button = event.target.closest('button[data-action="remove-team-member"]');
  if (!button) return;
  event.preventDefault();
  event.stopImmediatePropagation();
  confirmAction("Remover da equipe", "A pessoa deixará de aparecer nesta equipe.", async () => {
    await api.removeTeamMember(Number(button.dataset.teamId), Number(button.dataset.employeeId));
    toast("Pessoa removida.");
    navigate(location.href, {history:"none"});
  });
}

function onDashboardPeriodChange(event) {
  if (!["dashboard-filter", "summary-filter"].includes(event.target.form?.id) || event.target.name !== "period") return;
  const form = event.target.form, custom = form.elements.period.value === "custom";
  const dates = form.querySelector(".dashboard-custom-dates");
  dates.hidden = !custom;
  dates.querySelectorAll("input").forEach(input => { input.disabled = !custom; });
}

async function onDashboardClick(event) {
  const button = event.target.closest('button[data-action="refresh-pr-reviews"]');
  if (!button) return;
  event.preventDefault();
  event.stopImmediatePropagation();
  button.disabled = true;
  try {
    const result = await api.refreshPullReviews(10);
    toast(result.failedPullRequests
      ? `Revisões atualizadas em ${result.pullRequests - result.failedPullRequests} PRs. ${result.failedPullRequests} falharam.`
      : `Revisões atualizadas em ${result.pullRequests} PRs.`);
    await navigate(location.href, { history: "none" });
  } catch (error) {
    toast(error.message || "Não foi possível atualizar revisões de PRs.", true);
    button.disabled = false;
  }
}

function onDashboardSubmit(event) {
  if (!["dashboard-filter", "summary-filter"].includes(event.target.id)) return;
  event.preventDefault();
  event.stopImmediatePropagation();
  const form = event.target, values = new FormData(form), summary = form.id === "summary-filter";
  const url = new URL(summary ? "/manage/summaries" : "/manage/dashboard", location.origin);
  const period = values.get("period") || "30";
  url.searchParams.set("period", period);
  if (period === "custom") {
    const from = values.get("from"), to = values.get("to");
    const days = (Date.parse(to + "T12:00:00Z") - Date.parse(from + "T12:00:00Z")) / 86400000;
    if (!from || !to || from > to || days > 365) {
      toast("Informe um período válido de até 366 dias.", true);
      return;
    }
    url.searchParams.set("from", from);
    url.searchParams.set("to", to);
  }
  for (const name of ["team", "role", "member"]) {
    if ((!summary || values.get("preview") !== "demo") && values.get(name)) url.searchParams.set(name, values.get(name));
  }
  if (summary && values.get("preview") === "demo") url.searchParams.set("preview", "demo");
  go(url.pathname + url.search);
}

function inbox() {
  const page=state.data, items=pageItems(page), q=state.route.query;
  const selected=selectedEvidence(items,q.selected);
  const filtered=Boolean((q.status&&q.status!=="pending")||q.q||q.source||q.from||q.to);
  const countLabel={pending:"pendentes",accepted:"aceitas",dismissed:"dispensadas",all:"evidências"}[q.status||"pending"];
  const listBody=items.length
    ? `<ul class="inbox-items">${items.map(item=>inboxItem(item,selected)).join("")}</ul>`
    : `<div class="inbox-empty">${empty(filtered?"Nenhuma evidência corresponde aos filtros.":"Sua caixa de entrada está vazia.")}${filtered?"":'<a class="button primary" data-link href="/app/integrations/github/import">Importar PR</a>'}</div>`;
  const list=panel(`${page.total ?? items.length} ${countLabel}`,listBody,pager(page,"/app/inbox"),"fill inbox-list");
  return `${inboxFilters(page,q)}<div class="inbox-split ${selected?"":"is-empty"}"><div class="inbox-column">${list}</div>${selected?inboxPreview(selected):panel("Selecione uma evidência",empty("Escolha um item da lista para ler o detalhe."),"","fill preview-panel")}</div>`;
}

function inboxFilters(page,q) {
  const counts=page.counts||{},bucket=q.status||"pending";
  const states=[["pending","Pendentes"],["accepted","Aceitas"],["dismissed","Dispensadas"],["all","Todas"]];
  return `<section class="inbox-toolbar" aria-label="Busca e filtros da caixa"><form id="inbox-filter" class="inbox-filter-form">
    <label class="inbox-search"><span>Buscar evidências</span><input type="text" inputmode="search" name="q" value="${esc(q.q||"")}" placeholder="Buscar evidências" maxlength="120"></label>
    <div class="inbox-buckets" role="group" aria-label="Estado da caixa">${states.map(([value,label])=>{const params=new URLSearchParams(state.route.query);params.set("status",value);params.delete("selected");params.set("page","1");return `<a data-link href="/app/inbox?${params}" ${bucket===value?'aria-current="page"':''}>${label} <span>${counts[value]??0}</span></a>`}).join("")}</div>
    <div class="inbox-filter-controls"><label>Fonte<input name="source" value="${esc(q.source||"")}" placeholder="Todas" maxlength="120"></label><label>De<input type="date" name="from" value="${esc(q.from||"")}"></label><label>Até<input type="date" name="to" value="${esc(q.to||"")}"></label><label>Ordenar<select name="sort"><option value="newest" ${sel(q.sort||"newest","newest")}>Mais recentes</option><option value="oldest" ${sel(q.sort,"oldest")}>Mais antigas</option></select></label><button class="button" type="submit">Aplicar filtros</button></div>
    <input type="hidden" name="status" value="${esc(bucket)}"></form></section>`;
}

function textFilter(name,label,placeholder="",attributes="") {
  return {name,label,render:value=>`<input name="${name}" value="${esc(value||"")}" placeholder="${esc(placeholder)}" ${attributes}>`};
}

function selectFilter(name,label,options) {
  return {name,label,render:value=>`<select name="${name}">${options.map(([optionValue,optionLabel])=>`<option value="${esc(optionValue)}" ${sel(value,optionValue)}>${esc(optionLabel)}</option>`).join("")}</select>`};
}

function dateFilterFields() {
  return ["from","to"].map((name,index)=>({name,label:index?"Data final":"Data inicial",render:value=>`<input type="date" name="${name}" value="${esc(value||"")}">`}));
}

function compactFilterField(field,value) {
  return `<div class="compact-filter-field" data-active-filter="${field.name}"><label>${esc(field.label)}${field.render(value)}</label><button class="compact-filter-remove" type="button" data-action="remove-compact-filter" data-filter-field="${field.name}" aria-label="Remover filtro ${esc(field.label)}">×</button></div>`;
}

function compactFilters(fields,id,q,{defaults={},extra=""}={}) {
  const active=fields.filter(field=>q[field.name]!=null&&q[field.name]!==""&&String(q[field.name])!==String(defaults[field.name]??""));
  const count=active.length;
  return `<div class="compact-filter-toolbar">
    <button class="button compact-filter-toggle" type="button" data-action="toggle-compact-filters" aria-controls="${id}" aria-expanded="false"><svg width="16" height="16" viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" aria-hidden="true"><path d="M2 3.5h12M4 8h8M6 12.5h4"/></svg> Filtros${count?` <span class="compact-filter-count" aria-label="${count} ${count===1?"filtro ativo":"filtros ativos"}">${count}</span>`:""}</button>
    <form class="compact-filter-popover" id="${id}" aria-label="Filtrar resultados" hidden>
      <p class="compact-filter-caption">Adicionar filtro</p>
      <div class="compact-filter-options">${fields.map(field=>`<button type="button" class="compact-filter-option" data-action="add-compact-filter" data-filter-field="${field.name}" ${active.includes(field)?"hidden":""}>+ ${esc(field.label)}</button>`).join("")}</div>
      <div class="compact-active-filters" aria-live="polite">${active.map(field=>compactFilterField(field,q[field.name])).join("")}</div>
      ${fields.map(field=>`<template data-filter-template="${field.name}">${compactFilterField(field,defaults[field.name]??"")}</template>`).join("")}${extra}
      <div class="compact-filter-actions"><button class="button primary" type="submit">Aplicar</button><button class="button" type="button" data-action="clear-compact-filters">Limpar</button></div>
    </form>
  </div>`;
}

function inboxItem(item,selected) {
  const display=evidenceDisplay(item),active=Number(item.id)===Number(selected?.id);
  return `<li><button class="inbox-item ${active?"selected":""}" type="button" data-action="select-inbox-evidence" data-evidence-id="${esc(item.id)}" aria-pressed="${active}" aria-controls="inbox-preview">
    <span class="inbox-item-top"><strong>${esc(display.title)}</strong><span class="badge ${item.status==="PENDING"?"purple":item.currentReviewStatus==="ACCEPTED"?"green":""}">${item.currentReviewStatus==="ACCEPTED"?"Aceita":labelStatus(item.status)}</span></span>
    <span class="inbox-item-tags">${(item.tags||[]).map(tag=>`<span class="tag">${esc(tag)}</span>`).join("")}</span>
    <small>${esc(item.source||"Fonte não informada")} · ${dateTime(item.occurredAt||item.capturedAt)}</small>
  </button></li>`;
}

function inboxPreview(item) {
  const display=evidenceDisplay(item),ownerId=encodeURIComponent(String(state.user.id)),id=encodeURIComponent(String(item.id));
  const external=item.sourceUrl?safeExternal(item.sourceUrl):"#";
  const metadata=`${item.author?`<div><dt>Autor</dt><dd>${esc(item.author)}</dd></div>`:""}${item.mergedAt?`<div><dt>Merge</dt><dd>${dateTime(item.mergedAt)}</dd></div>`:""}<div><dt>Origem</dt><dd>${esc(display.origin)}</dd></div><div><dt>Ocorrência</dt><dd>${dateTime(item.occurredAt||item.capturedAt)}</dd></div>`;
  const analysis=item.analysisId?`<a class="button" data-link href="/workspace/people/${ownerId}/analyses/${encodeURIComponent(String(item.analysisId))}">Abrir análise</a>`:"";
  return `<section class="panel fill preview-panel" id="inbox-preview" data-evidence-id="${esc(item.id)}" aria-label="Prévia da evidência"><div class="panel-body">
    <div class="inbox-preview-heading"><div class="inbox-preview-origin"><span>${esc(display.origin)}</span><span class="badge ${item.status==="PENDING"?"purple":item.currentReviewStatus==="ACCEPTED"?"green":""}">${item.currentReviewStatus==="ACCEPTED"?"Aceita":labelStatus(item.status)}</span></div><h3 tabindex="-1">${esc(display.title)}</h3><div class="inbox-preview-tags">${(item.tags||[]).map(tag=>`<span class="tag">${esc(tag)}</span>`).join("")}</div></div>
    <dl class="inbox-preview-facts">${metadata}</dl><div class="inbox-summary"><small>RESUMO DA FONTE</small><p>${esc(display.summary)}</p></div>
    <details class="inbox-technical"><summary data-action="load-inbox-details" data-evidence-id="${esc(item.id)}">Ver detalhes técnicos</summary><pre>Carregando fonte…</pre></details>
    ${external!=="#"?`<p><a href="${external}" target="_blank" rel="noopener noreferrer">Abrir fonte original ↗</a></p>`:""}
  </div><footer>${item.status==="PENDING"?`<button class="button" type="button" data-action="dismiss-inbox-evidence" data-evidence-id="${esc(item.id)}">Dispensar</button>`:""}${analysis}<a class="button primary" data-link href="/workspace/people/${ownerId}/evidence/${id}">Abrir evidência</a></footer></section>`;
}

function reconcileInboxSelection() {
  const selected=state.route.query.selected;
  if (!selected || pageItems(state.data).some(item=>Number(item.id)===Number(selected))) return;
  delete state.route.query.selected;
  const canonical=canonicalUrl(state.route);
  history.replaceState({},"",canonical);
  state.returnTo=canonical;
}

function analyses() {
  const page=state.data,items=pageItems(page),q=state.route.query;
  return `${compactFilters([...dateFilterFields(),textFilter("source","Fonte","Todas")],"analyses-filter",q)}<div class="section-actions"><a class="button" href="/app/analyses/reports" data-link>Relatórios</a><button type="button" class="button danger" data-action="clear-analyses" ${items.length?"":"disabled"}>Limpar histórico</button></div>${panel(`${page.total ?? items.length} análises salvas`,items.length?`<div class="row-list">${items.map(analysisRow).join("")}</div>`:empty("Nenhuma análise salva neste período."),pager(page,"/app/analyses"),"fill")}`;
}

function reports() {
  const [insights]=state.data,q=state.route.query,view=q.view||"sources";
  const values=view==="levels"?insights.estimatedLevelDistribution:view==="trend"?insights.recentTrend:insights.sourceDistribution;
  return `${compactFilters([selectFilter("view","Visualização",[["sources","Fontes"],["levels","Níveis estimados"],["trend","Tendência"]]),...dateFilterFields()],"reports-filter",q,{defaults:{view:"sources"}})}${panel("Distribuição no período", values?.length?`<div class="report-list">${values.map(x=>`<div><div><strong>${esc(x.label)}</strong><span>${x.count} análises · ${x.percentage ?? 0}%</span></div><div class="progress"><span style="width:${x.percentage ?? 0}%"></span></div></div>`).join("")}</div>`:empty("Ainda não há análises para este relatório."),"","fill")}`;
}

function framework() {
  const [insights,framework]=state.data,q=state.route.query,levels=framework.levels||[],defaultLevel=levels[0]?.id||levels[0]?.key,level=q.level||defaultLevel, coverage=(insights.criterionCoverage||[]).filter(c=>c.level===level),support=q.support||"all",shown=coverage.filter(c=>support==="all"||(support==="supported"?c.status==="SUPPORTED":c.status!=="SUPPORTED"));
  if(state.route.id==="criterion") { const c=(insights.criterionCoverage||[]).find(x=>x.criterionId===state.route.params.criterionId); return c?`${breadcrumbs([["/app/framework", "Framework"]])}${panel(esc(c.criterion),`<div class="statusline"><span class="badge purple">${esc(c.level)}</span><span class="badge">${c.status==="SUPPORTED"?"Com evidência":"Sem evidência"}</span></div><div class="reading"><p>${esc(c.description||"Sem descrição.")}</p><h3>Evidências de apoio</h3>${c.supportingEvidence?.length?c.supportingEvidence.map(ref=>`<a class="row" data-link href="/workspace/people/${state.user.id}/analyses/${ref.analysisId}"><span>${esc(ref.title||ref.id)}</span><span>›</span></a>`).join(""):empty("Nenhuma evidência associada. Isso não é uma avaliação negativa.")}</div>`,"","fill")}`:empty("Critério não encontrado no framework atual."); }
  return `${compactFilters([selectFilter("level","Nível",levels.map(l=>[l.id||l.key,`${l.id||l.key} · ${l.title||""}`])),selectFilter("support","Apoio",[["all","Todos"],["missing","Sem evidência"],["supported","Com evidência"]]),...dateFilterFields()],"framework-filter",q,{defaults:{level:defaultLevel,support:"all"}})}${panel(`${shown.length} critérios`,shown.length?`<div class="row-list">${shown.map(c=>`<a class="row" data-link href="/app/framework/criteria/${c.criterionId}?level=${c.level}"><div><strong>${esc(c.criterion)}</strong><small>${c.evidenceCount||0} evidências de apoio</small></div><span class="badge ${c.status==="SUPPORTED"?"green":""}">${c.status==="SUPPORTED"?"Com evidência":"Sem evidência"}</span><span>›</span></a>`).join("")}</div>`:empty("Nenhum critério corresponde ao filtro."),`<span class="note">Ausência de evidência não é avaliação negativa.</span>`,"fill")}`;
}

function careerPlan(manager) {
  const person=manager?(state.data.person||state.data[0]):null, profile=manager?(state.data.detail||state.data[1]):state.data, view=state.route.query.view||"objectives";
  if(manager&&["objective-new","objective"].includes(state.route.id)) return objectiveEditor(person,profile);
  const base=manager?`/manage/people/${person.id}/career-plan`:"/app/career-plan";
  const dir=manager?directorySuffix():"",viewLink=value=>`${base}?view=${value}${dir?`&${dir}`:""}`;
  return `${manager?personHeader(person)+personTabs(person,"career-plan"):""}${tabs([[viewLink("objectives"),"Objetivos"],[viewLink("context"),"Contexto"]],view)}${view==="context"?careerContext(profile,manager):objectives(profile,manager,person)}`;
}

function objectives(profile,manager,person) { const rows=(profile.objectives||[]).map(o=>manager?`<a class="row objective-row" data-link href="/manage/people/${person.id}/career-plan/objectives/${o.id}">${objectiveText(o)}<span>›</span></a>`:`<div class="row objective-row">${objectiveText(o)}</div>`).join(""); return panel("Objetivos de carreira",rows||empty("Nenhum objetivo definido."),manager?`<a class="button" data-link href="/manage/people/${person.id}/career-plan?view=context${directorySuffix()?`&${directorySuffix()}`:""}">Editar contexto</a><a class="button primary" data-link href="/manage/people/${person.id}/career-plan/objectives/new">Novo objetivo</a>`:`<span class="note">Seu gestor mantém este plano.</span>`,"fill"); }
function objectiveText(o){return `<div><strong>${esc(o.text)}</strong><small>${labelStatus(o.status)}${o.targetDate?` · Data alvo: ${date(o.targetDate)}`:""}</small></div><span class="badge">${labelStatus(o.status)}</span>`;}

function careerContext(profile,manager) { const role=profile.jobRole; if(!manager) return panel("Contexto de carreira",`<dl class="details"><div><dt>Cargo</dt><dd>${esc(role?.name||"Não definido")}</dd></div><div><dt>Progressão</dt><dd>${esc(profile.currentLevel||"—")} → ${esc(profile.targetLevel||"—")}</dd></div><div><dt>Características</dt><dd>${(profile.characteristics||[]).map(x=>`<span class="tag">${esc(x)}</span>`).join(" ")||"Não definidas"}</dd></div></dl><p class="note">Contexto atual do plano. Resultados históricos preservam o contexto do momento da análise.</p>`,"","fill");const saved=draft(planDraftKey())||{},characteristics=saved.characteristics??(profile.characteristics||[]).join("\n");return panel("Editar contexto",`<form id="plan-form" class="form-grid"><label>Cargo<select name="jobRoleId">${((state.data.settings||state.data[2]).activeRoles||[]).map(r=>`<option value="${r.id}" ${sel(saved.jobRoleId??role?.id,r.id)}>${esc(r.name)}</option>`).join("")}</select></label><label>Nível atual<select name="currentLevel">${levelOptions(profile,saved.currentLevel??profile.currentLevel)}</select></label><label>Nível alvo<select name="targetLevel">${levelOptions(profile,saved.targetLevel??profile.targetLevel)}</select></label><label class="wide">Características<textarea name="characteristics" rows="6" maxlength="1209">${esc(characteristics)}</textarea><small>Até 10 linhas, com no máximo 120 caracteres cada.</small></label><input type="hidden" name="expectedUpdatedAt" value="${esc(profile.updatedAt||"")}"></form>`,`<button class="button primary" type="submit" form="plan-form">Salvar contexto</button>`,"fill"); }

function objectiveEditor(person,profile){ const current=state.route.id==="objective"?(profile.objectives||[]).find(o=>o.id===state.route.params.objectiveId):null,key=objectiveDraftKey(),saved=draft(key)||{};if(state.route.id==="objective"&&!current)return `${breadcrumbs([[`/manage/people/${person.id}/career-plan`,person.name]])}${panel("Objetivo indisponível",empty("Este objetivo não existe ou não está mais disponível."),`<a class="button primary" data-link href="/manage/people/${person.id}/career-plan">Voltar ao plano</a>`,"fill")}`;return `${breadcrumbs([[`/manage/people/${person.id}/career-plan`,person.name]])}${panel(current?"Editar objetivo":"Novo objetivo",`<form id="objective-form" class="form-grid"><label class="wide">Objetivo<textarea name="text" rows="6" maxlength="1000" required>${esc(saved.text??current?.text??"")}</textarea><small>Máximo de 1.000 caracteres</small></label><label>Status<select name="status">${["ACTIVE","COMPLETED","ARCHIVED"].map(s=>`<option ${sel(saved.status??current?.status??"ACTIVE",s)} value="${s}">${labelStatus(s)}</option>`).join("")}</select></label><label>Data alvo<input type="date" name="targetDate" value="${esc(saved.targetDate??current?.targetDate??"")}"></label><input type="hidden" name="expectedUpdatedAt" value="${esc(current?.updatedAt||"")}"></form>`,`<a class="button" data-link href="/manage/people/${person.id}/career-plan">Cancelar</a><button class="button primary" type="submit" form="objective-form">Salvar objetivo</button>`,"fill")}`; }

function people(){return peopleWorkspace(null);}
function directoryQuery(){const q=state.route.query;return state.route.id==="people"?{q:q.q,role:q.role,level:q.level,page:q.page}:{q:q.dq,role:q.drole,level:q.dlevel,page:q.dpage};}
function directorySuffix(){const q=directoryQuery(),params=new URLSearchParams();if(q.q)params.set("dq",q.q);if(q.role)params.set("drole",q.role);if(q.level)params.set("dlevel",q.level);if(q.page)params.set("dpage",q.page);return params.toString();}
function personUrl(id,section="career-plan"){const suffix=directorySuffix();return `/manage/people/${id}/${section}${suffix?`?${suffix}`:""}`;}
function directoryPager(page){if(!page||page.total===undefined)return "";const q=directoryQuery(),current=page.page||1,max=Math.max(1,Math.ceil(page.total/page.pageSize));const href=n=>{const params=new URLSearchParams(state.route.query);params.set(state.route.id==="people"?"page":"dpage",n);return `${state.route.path}?${params}`};return `<span>${page.total?`${(current-1)*page.pageSize+1}–${Math.min(current*page.pageSize,page.total)} de ${page.total}`:"0 resultados"}</span><div class="actions"><a class="button ${current<=1?"disabled":""}" ${current>1?'data-link':''} href="${href(current-1)}">Anterior</a><a class="button ${current>=max?"disabled":""}" ${current<max?'data-link':''} href="${href(current+1)}">Próxima</a></div>`;}
function peopleWorkspace(detail){const base=state.route.id==="people",page=base?state.data[0]:state.data.directory,roles=base?state.data[1]:state.data.roles,items=pageItems(page),q=directoryQuery(),selectedId=state.route.params.employeeId;
  const form=`<form id="people-directory-filter" class="people-filters" aria-label="Filtrar diretório"><label>Nome ou e-mail<input name="q" type="search" value="${esc(q.q||"")}" placeholder="Buscar pessoa"></label><label>Cargo<select name="role"><option value="">Todos</option>${roles.filter(r=>r.status==="ACTIVE").map(r=>`<option value="${r.id}" ${sel(q.role,r.id)}>${esc(r.name)}</option>`).join("")}</select></label><label>Nível<input name="level" value="${esc(q.level||"")}" placeholder="Todos" maxlength="20"></label><button class="button" type="submit">Filtrar</button></form>`;
  const rows=items.length?`<div class="people-list">${items.map(p=>`<a class="people-row ${Number(p.id)===Number(selectedId)?"selected":""}" data-link href="${personUrl(p.id)}" ${Number(p.id)===Number(selectedId)?'aria-current="page"':''}><strong>${esc(p.name)}</strong><small>${esc(p.jobRoleName||"Cargo não definido")} · ${esc(p.currentLevel||"—")} → ${esc(p.targetLevel||"—")}</small></a>`).join("")}</div>`:empty("Nenhuma pessoa encontrada.");
  const directory=panel(`Pessoas · ${page.total??items.length}`,rows,directoryPager(page),"fill people-directory");
  const result=detail?`<div class="people-detail"><a class="people-back" data-link href="/manage/people?${new URLSearchParams({...(q.q?{q:q.q}:{}),...(q.role?{role:q.role}:{}),...(q.level?{level:q.level}:{}),...(q.page?{page:q.page}:{})})}">← Voltar à lista</a>${detail}</div>`:`<div class="people-placeholder panel"><div class="empty"><span aria-hidden="true">○</span><p>Selecione uma pessoa para consultar o contexto de carreira.</p></div></div>`;
  return `${form}<div class="people-workspace">${directory}${result}</div>`;
}
function personEvidence(){const person=state.data.person,page=state.data.detail,items=pageItems(page),q=state.route.query;return `${personHeader(person)}${personTabs(person,"evidence")}${compactFilters([selectFilter("status","Status",[["","Todos"],...["pending","dismissed","analyzed"].map(value=>[value,labelStatus(value.toUpperCase())])]),textFilter("source","Fonte","Todas"),...dateFilterFields()],"person-evidence-filter",q)}${panel(`${page.total??items.length} evidências`,items.length?`<div class="row-list">${items.map(e=>evidenceRow(e,person.id)).join("")}</div>`:empty("Nenhuma evidência encontrada."),pager(page,`/manage/people/${person.id}/evidence`),"fill")}`;}
function personAnalyses(){const person=state.data.person,page=state.data.detail,items=pageItems(page),q=state.route.query;return `${personHeader(person)}${personTabs(person,"analyses")}${compactFilters([selectFilter("review","Revisão",[["","Todas"],...["unreviewed","needs-context","accepted"].map(value=>[value,labelStatus(value.replace("needs-context","NEEDS_CONTEXT").toUpperCase())])]),textFilter("source","Fonte","Todas"),...dateFilterFields()],"person-analyses-filter",q)}${panel(`${page.total??items.length} análises`,items.length?`<div class="row-list">${items.map(a=>analysisRow(a,person.id)).join("")}</div>`:empty("Nenhuma análise encontrada."),pager(page,`/manage/people/${person.id}/analyses`),"fill")}`;}
function reviews(){const page=state.data,items=pageItems(page),q=state.route.query,counts=page.counts||{};return `${compactFilters([selectFilter("status","Status",[["unreviewed",`Não revisadas (${counts.UNREVIEWED||0})`],["needs-context",`Precisam de contexto (${counts.NEEDS_CONTEXT||0})`],["accepted",`Aceitas (${counts.ACCEPTED||0})`]]),textFilter("employee","Pessoa","ID",'inputmode="numeric"')],"reviews-filter",q,{defaults:{status:"unreviewed"}})}${panel(`${page.total??items.length} revisões`,items.length?`<div class="row-list">${items.map(i=>`<a class="row" data-link href="/workspace/people/${i.ownerId}/analyses/${i.analysisId}?view=review"><div><strong>${esc(i.employeeName)} · ${esc(i.sourceMeta||i.source)}</strong><small>${dateTime(i.createdAt)} · ${labelStatus(i.currentStatus)}</small></div><span class="badge">${labelStatus(i.currentStatus)}</span><span>›</span></a>`).join("")}</div>`:empty("Não há análises nesta fila."),pager(page,"/manage/reviews"),"fill")}`;}

function terminology(){const s=state.data,l=s.labels||{},saved=draft("terminology")||{};return `${configTabs("terminology")}${panel("Seis termos da organização",`<form id="terminology-form" class="form-grid">${Object.entries(l).map(([k,v])=>`<label>${esc(k)}<input name="${esc(k)}" value="${esc(saved[k]??v)}" maxlength="80" required></label>`).join("")}</form><aside class="note"><strong>Prévia</strong><p>${Object.values({...l,...saved}).map(esc).join(" · ")}</p></aside>`,`<button class="button primary" type="submit" form="terminology-form">Salvar terminologia</button>`,"fill")}`;}
function roles(){const [roles,framework]=state.data;if(state.route.id==="roles")return `${configTabs("roles")}${panel("Catálogo de cargos",`<div class="row-list">${roles.map(r=>`<a class="row" data-link href="/manage/career/roles/${r.id}"><div><strong>${esc(r.name)}</strong><small>${esc(r.description||"")} · ${(r.allowedLevelIds||[]).map(esc).join(", ")}</small></div><span class="badge">${r.status==="ACTIVE"?"Ativo":"Arquivado"}</span><span>›</span></a>`).join("")}</div>`,`<a class="button primary" data-link href="/manage/career/roles/new">Novo cargo</a>`,"fill")}`;const current=state.route.id==="role"?roles.find(r=>r.id===state.route.params.roleId):null;if(state.route.id==="role"&&!current)return empty("Cargo não encontrado.");const levels=framework.levels||[],saved=draft(roleDraftKey())||{},allowed=saved.allowedLevelIds??current?.allowedLevelIds??[];return `${configTabs("roles")}${panel(current?esc(current.name):"Novo cargo",`<form id="role-form" class="form-grid"><label>Nome<input name="name" value="${esc(saved.name??current?.name??"")}" maxlength="120" required ${current?.status==="ARCHIVED"?"disabled":""}></label><label class="wide">Descrição<textarea name="description" rows="5" maxlength="1000" ${current?.status==="ARCHIVED"?"disabled":""}>${esc(saved.description??current?.description??"")}</textarea></label><fieldset class="wide"><legend>Níveis permitidos</legend><div class="checks">${levels.map(l=>`<label><input type="checkbox" name="allowedLevelIds" value="${esc(l.key)}" ${allowed.includes(l.key)?"checked":""} ${current?.status==="ARCHIVED"?"disabled":""}>${esc(l.key)} · ${esc(l.title)}</label>`).join("")}</div></fieldset></form>`,current?.status==="ARCHIVED"?`<span class="note">Cargo arquivado: somente leitura.</span>`:`${current?'<button class="button danger" data-action="archive-role">Arquivar</button>':""}<button class="button primary" type="submit" form="role-form">Salvar cargo</button>`,"fill")}`;}
function managerFramework(){const f=state.data;return `${configTabs("framework")}${panel("Estrutura oficial",`<p class="note">Versão ${esc(f.version||"atual")} · somente leitura</p>${(f.levels||[]).map(l=>`<details><summary><strong>${esc(l.key)} · ${esc(l.title)}</strong> <span>${l.criteria?.length||0} critérios</span></summary><div class="reading">${(l.criteria||[]).map(c=>`<article><h3>${esc(c.key)}</h3><p>${esc(c.description||"")}</p><small>${esc(c.id)}</small></article>`).join("")}</div></details>`).join("")}`,"","fill")}`;}

function github(){const s=state.data,view=state.route.query.view||"connection",saved=draft("github-settings")||{};return `${tabs([["/app/integrations/github?view=connection","Conexão"],["/app/integrations/github?view=sync","Sincronização"]],view)}${view==="connection"?panel("Conexão com o GitHub",`<form id="github-form" class="form-grid"><label>Repositório<input name="repoSlug" value="${esc(saved.repoSlug??s.repoSlug??"")}" placeholder="organizacao/repositorio" required></label><label>Autor<input name="authorLogin" value="${esc(saved.authorLogin??s.authorLogin??"")}" placeholder="login-github" required></label></form><p class="note">A configuração é pessoal e usada apenas para localizar pull requests do autor informado.</p>`,`<button class="button danger" data-action="disconnect-github" ${s.configured?"":"disabled"}>Desconectar</button><button class="button" data-action="test-github">Testar conexão</button><button class="button primary" type="submit" form="github-form">Salvar</button>`,"fill"):panel("Sincronização do GitHub",`<dl class="details"><div><dt>Repositório</dt><dd>${esc(s.repoSlug||"Não configurado")}</dd></div><div><dt>Última sincronização</dt><dd>${s.lastSyncAt?dateTime(s.lastSyncAt):"Ainda não executada"}</dd></div><div><dt>Resultado</dt><dd>${esc(s.lastSyncOutcome||"Não informado")}</dd></div></dl>`,`<a class="button" data-link href="/app/inbox?status=pending">Ver caixa de entrada</a><button class="button primary" data-action="sync-github" ${s.configured?"":"disabled"}>Sincronizar agora</button>`,"fill")}`;}
function integrations(){const s=state.data;return panel("Integrações disponíveis",`<a class="integration-row" data-link href="/app/integrations/github"><div class="integration-mark">GH</div><div><strong>GitHub</strong><small>${s.configured?`Conectado a ${esc(s.repoSlug)}`:"Configuração pendente"}</small><p>Capture e sincronize pull requests do seu repositório.</p></div><span>›</span></a><p class="note">Registros históricos de Slack e Jira continuam legíveis; novas capturas integradas são feitas pelo GitHub.</p>`,"","fill");}
function githubImport(){const saved=draft("github-import")||{};return panel("Localizar pull requests",`<form id="github-import-form" class="form-grid"><label>Repositório<input name="repoSlug" value="${esc(saved.repoSlug||"")}" placeholder="organizacao/repositorio" required></label><label>Autor<input name="authorLogin" value="${esc(saved.authorLogin||"")}" placeholder="login-github" required></label></form><div id="github-results">${empty("Informe um repositório e autor para pesquisar.")}</div>`,`<button class="button primary" type="submit" form="github-import-form">Pesquisar</button>`,"fill");}

function evidenceDetail(){
  const e=state.data,owner=Number(state.route.params.ownerId)===Number(state.user.id)&&state.user.role==="EMPLOYEE";
  const observation=owner?draft("evidence:"+e.id)?.observation||"":"";
  const sourceLink=e.sourceUrl?`<p><a href="${safeExternal(e.sourceUrl)}" target="_blank" rel="noopener noreferrer">Abrir fonte original ↗</a></p>`:"";
  const ownerField=owner?`<label>Observação opcional<textarea id="observation" rows="10" maxlength="2000">${esc(observation)}</textarea><small data-count-for="observation">${observation.length}/2000</small><span class="note">Separada da fonte original.</span></label>`:`<aside class="note">Visualização gerencial somente leitura.</aside>`;
  const body=`<div class="statusline"><span class="badge purple">${labelStatus(e.status)}</span><span>Fonte imutável · ${dateTime(e.occurredAt||e.capturedAt)}</span></div><div class="grid-two"><div class="reading source-text"><h3>Conteúdo capturado</h3><p>${esc(e.content||"")}</p>${sourceLink}</div>${ownerField}</div>`;
  const footer=owner&&e.status==="PENDING"?`<button class="button danger" data-action="dismiss-evidence">Dispensar</button><button class="button primary" data-action="analyze-evidence">Analisar evidência</button>`:e.analysisId?`<a class="button primary" data-link href="/workspace/people/${e.ownerId}/analyses/${e.analysisId}">Abrir análise</a>`:`<span class="note">Nenhuma ação disponível.</span>`;
  return `${breadcrumbs([[owner?"/app/inbox?status=pending":`/manage/people/${e.ownerId}/evidence`,owner?"Caixa de entrada":"Evidências"]])}${panel(esc(evidenceDisplay(e).title),body,footer,"fill")}`;
}
function analysisDetail(){
  const [a,reviews]=state.data,view=state.route.query.view||"summary",manager=state.user.role==="MANAGER";
  const base=`/workspace/people/${a.ownerId}/analyses/${a.analysisId}`,history=reviews.history||[];
  let body;
  if(view==="source") body=`<div class="reading source-text"><h3>Fonte preservada</h3><p>${esc(a.evidence||"")}</p><h3>Observação no momento da análise</h3><p>${esc(a.userObservation||"Nenhuma observação registrada.")}</p></div>`;
  else if(view==="history") body=history.length?`<ol class="timeline">${history.map(r=>`<li><span class="badge">${labelStatus(r.status)}</span><small>${esc(r.reviewerName)} · ${dateTime(r.createdAt)}</small><p>${esc(r.comment||"Sem comentário.")}</p></li>`).join("")}</ol>`:empty("Esta análise ainda não foi revisada.");
  else if(view==="review"&&manager){const reviewDraft=draft("review:"+a.analysisId)?.comment||"";body=`<form id="review-form"><p>Esta decisão cria um novo evento; o histórico anterior não é editado.</p><label>Comentário opcional<textarea name="comment" rows="8" maxlength="2000">${esc(reviewDraft)}</textarea><small>${reviewDraft.length}/2.000 caracteres</small></label><input type="hidden" name="expectedLatestReviewId" value="${history.at(-1)?.id||0}"></form>`;}
  else body=`<div class="summary"><div><small>Impacto estimado</small><strong>${esc(a.impactLevel||"Não informado")}</strong></div><div><span class="badge">Confiança ${confidence(a.confidence)}</span><p>Contexto histórico: ${esc(a.currentLevel||"—")} → ${esc(a.targetLevel||"—")}</p></div></div><div class="reading"><h3>Justificativa</h3><p>${esc(a.justification||"Não informada.")}</p><h3>Competências identificadas</h3><div>${(a.competencies||[]).map(x=>`<span class="tag">${esc(x)}</span>`).join(" ")||"Não informadas"}</div><h3>Sugestões</h3><ul>${(a.suggestions||[]).map(x=>`<li>${esc(x)}</li>`).join("")}</ul><p class="note">A análise organiza sinais de impacto. Ela não decide uma promoção.</p></div>`;
  const views=[[`${base}?view=summary`,"Resumo"],[`${base}?view=source`,"Fonte"],[`${base}?view=history`,"Histórico"]];
  if(manager) views.push([`${base}?view=review`,"Revisar"]);
  const footer=view==="review"&&manager?`<button class="button" data-review="NEEDS_CONTEXT">Solicitar contexto</button><button class="button primary" data-review="ACCEPTED">Aceitar análise</button>`:`<span class="badge">${labelStatus(reviews.currentStatus)}</span>`;
  return `${breadcrumbs([[manager?`/manage/people/${a.ownerId}/analyses`:"/app/analyses",manager?"Análises da pessoa":"Análises"]])}${tabs(views,view)}${panel(esc(evidenceDisplay({content:a.evidence,sourceMeta:a.sourceMeta,source:a.source,analysisTitle:a.title}).title),body,footer,"fill")}`;
}

function onCompactFilterClick(event) {
  const button=event.target.closest('button[data-action="toggle-compact-filters"],button[data-action="add-compact-filter"],button[data-action="remove-compact-filter"],button[data-action="clear-compact-filters"]');
  if (!button) {
    if (!event.target.closest(".compact-filter-toolbar")) closeCompactFilters();
    return;
  }
  const toolbar=button.closest(".compact-filter-toolbar"),panel=toolbar?.querySelector(".compact-filter-popover"),toggle=toolbar?.querySelector(".compact-filter-toggle");
  if (!panel) return;
  event.stopImmediatePropagation();
  if (button.dataset.action==="toggle-compact-filters") {
    const open=panel.hidden;
    closeCompactFilters();
    panel.hidden=!open;
    toggle.setAttribute("aria-expanded",String(open));
    if (open) panel.querySelector('.compact-filter-option:not([hidden]),.compact-filter-field input,.compact-filter-field select,button[type="submit"]')?.focus();
    return;
  }
  if (button.dataset.action==="add-compact-filter") {
    const name=button.dataset.filterField;
    const template=panel.querySelector(`template[data-filter-template="${name}"]`);
    const field=template.content.firstElementChild.cloneNode(true);
    panel.querySelector(".compact-active-filters").append(field);
    button.hidden=true;
    field.querySelector("input,select")?.focus();
    return;
  }
  if (button.dataset.action==="remove-compact-filter") {
    const name=button.dataset.filterField;
    button.closest(".compact-filter-field").remove();
    const option=panel.querySelector(`[data-action="add-compact-filter"][data-filter-field="${name}"]`);
    option.hidden=false;
    option.focus();
    return;
  }
  if (button.dataset.action==="clear-compact-filters") return clearCompactFilters(panel);
}

function onInboxClick(event) {
  if (state.route?.id!=="inbox") return;
  const technical=event.target.closest('[data-action="load-inbox-details"]');
  if (technical) {
    event.stopImmediatePropagation();
    const details=technical.closest("details"),output=details.querySelector("pre");
    if (details.dataset.loaded) return;
    details.dataset.loaded="loading";
    api.evidence(state.user.id,Number(technical.dataset.evidenceId),false,state.controller?.signal)
      .then(item=>{if(output.isConnected){output.textContent=item.content||"A fonte não contém detalhes adicionais.";details.dataset.loaded="true";}})
      .catch(error=>{if(output.isConnected){output.textContent=error.name==="AbortError"?"Carregamento interrompido.":"Não foi possível carregar a fonte. Feche e abra novamente.";delete details.dataset.loaded;}});
    return;
  }
  const dismiss=event.target.closest('[data-action="dismiss-inbox-evidence"]');
  if (dismiss) {
    event.stopImmediatePropagation();
    const id=Number(dismiss.dataset.evidenceId);
    return confirmAction("Dispensar evidência", "A fonte será preservada e sairá das pendências. O fluxo atual não oferece restauração.", async()=>{
      await api.dismissEvidence(id);
      const url=new URL(location.href);url.searchParams.delete("selected");
      await navigate(url.href,{history:"replace"});
      toast("Evidência dispensada.");
    });
  }
  const button=event.target.closest('button[data-action="select-inbox-evidence"]');
  if (!button) return;
  event.stopImmediatePropagation();
  const selected=pageItems(state.data).find(item=>Number(item.id)===Number(button.dataset.evidenceId));
  if (!selected) return;
  closeCompactFilters();
  if (Number(state.route.query.selected)!==Number(selected.id)) {
    state.route.query.selected=Number(selected.id);
    const canonical=canonicalUrl(state.route);
    history.pushState({},"",canonical);
    state.returnTo=canonical;
  }
  document.querySelectorAll(".inbox-item").forEach(item=>{
    const active=Number(item.dataset.evidenceId)===Number(selected.id);
    item.classList.toggle("selected",active);
    item.setAttribute("aria-pressed",String(active));
  });
  document.querySelector("#inbox-preview").outerHTML=inboxPreview(selected);
  const preview=document.querySelector("#inbox-preview");
  if (matchMedia("(max-width: 899px)").matches) preview.scrollIntoView({block:"start",behavior:matchMedia("(prefers-reduced-motion: reduce)").matches?"auto":"smooth"});
  preview.querySelector("h3")?.focus({preventScroll:true});
}

function closeCompactFilters({restoreFocus=false}={}) {
  document.querySelectorAll(".compact-filter-popover:not([hidden])").forEach(panel=>{
    panel.hidden=true;
    const toggle=panel.closest(".compact-filter-toolbar")?.querySelector(".compact-filter-toggle");
    toggle?.setAttribute("aria-expanded","false");
    if (restoreFocus) toggle?.focus();
  });
}

function onCompactFilterKeydown(event) {
  if (event.key==="Escape"&&document.querySelector(".compact-filter-popover:not([hidden])")) {
    event.preventDefault();
    closeCompactFilters({restoreFocus:true});
  }
}

async function onClick(event){const link=event.target.closest("a[data-link]");if(link&&!event.defaultPrevented&&!event.metaKey&&!event.ctrlKey&&!event.shiftKey&&event.button===0){event.preventDefault();if(await allowNavigation())go(link.href);return;}const button=event.target.closest("button[data-action],button[data-review]");if(!button)return;const action=button.dataset.action,reviewButtons=button.dataset.review?[...document.querySelectorAll("button[data-review]")]:[];try{if(action==="open-menu")return openMenu(button);if(action==="close-menu")return closeMenu();if(action==="logout"){await logoutUser();clearAuthSession();state.user=null;state.returnTo=null;state.expiring=false;state.drafts.clear();return go("/", "replace");}if(action==="retry")return navigate(location.href,{history:"none"});if(action==="account")return showDialog("Sua conta",`<p><strong>${esc(state.user.name)}</strong><br>${esc(state.user.email)}<br>${state.user.role==="MANAGER"?"Gestor":"Funcionário"}</p>`,[{label:"Fechar"}]);if(action==="clear-analyses")return confirmAction("Limpar histórico", "As análises salvas e suas revisões serão removidas. As evidências capturadas serão preservadas.", async()=>{await api.clearAnalyses(state.route.query);toast("Histórico removido.");navigate(location.href,{history:"none"});});if(action==="dismiss-evidence")return confirmAction("Dispensar evidência", "A fonte será preservada e sairá das pendências. O fluxo atual não oferece restauração.", async()=>{await api.dismissEvidence(state.data.id);state.drafts.delete(`evidence:${state.data.id}`);go("/app/inbox?status=pending","replace");});if(action==="analyze-evidence"){disable(button,"Analisando…");const result=await api.analyzeEvidence(state.data.id,document.querySelector("#observation")?.value);state.drafts.delete(`evidence:${state.data.id}`);return go(`/workspace/people/${state.user.id}/analyses/${result.analysisId}`,"replace");}if(action==="sync-github"){disable(button,"Sincronizando…");const result=await api.syncGithub();toast(`${result.created} novas; ${result.existing} já existentes.`);return navigate(location.href,{history:"none"});}if(action==="test-github"){disable(button,"Testando…");await api.testGithub();toast("Conexão validada.");button.disabled=false;button.textContent="Testar conexão";}if(action==="disconnect-github")return confirmAction("Desconectar GitHub", "A configuração será removida. Evidências já capturadas permanecem.",async()=>{await api.clearGithub();state.drafts.delete("github-settings");navigate(location.href,{history:"none"});});if(action==="archive-role")return confirmAction("Arquivar cargo", "Se houver pessoas atribuídas, o servidor exigirá um cargo substituto compatível.",async()=>{await api.archiveRole(state.route.params.roleId,null);go("/manage/career/roles","replace");});if(button.dataset.review){reviewButtons.forEach(candidate=>disable(candidate,"Registrando…"));const form=new FormData(document.querySelector("#review-form"));await api.review(state.route.params.ownerId,state.route.params.analysisId,{status:button.dataset.review,comment:form.get("comment")||null,expectedLatestReviewId:Number(form.get("expectedLatestReviewId")),idempotencyKey:crypto.randomUUID()});state.drafts.delete(`review:${state.route.params.analysisId}`);toast("Revisão registrada.");return go(`${location.pathname}?view=history`,"replace");}}catch(error){(reviewButtons.length?reviewButtons:[button]).forEach(restoreButton);toast(error.message||"A operação não foi concluída.",true);}}

async function onSubmit(event){const form=event.target;if(form.method==="dialog")return;event.preventDefault();if(form.id==="plan-form"&&!validateCharacteristics(form.elements.characteristics))return;if(form.id==="role-form"&&!validateRoleLevels(form))return;const fd=new FormData(form);try{setFormBusy(form,true);if(form.dataset.authForm){const response=form.dataset.authForm==="login"?await loginUser({email:fd.get("email"),password:fd.get("password")}):await registerUser({name:fd.get("name"),email:fd.get("email"),password:fd.get("password")});saveAuthSession(response.token,response.user);state.user=response.user;state.validated=true;state.expiring=false;const requested=safeReturnTo(new URLSearchParams(location.search).get("returnTo"));state.returnTo=null;return go(requested&&canAccess(matchRoute(requested),state.user)?requested:roleHome(state.user),"replace");}if(["inbox-filter","analyses-filter","reports-filter","framework-filter","people-filter","people-directory-filter","reviews-filter","person-evidence-filter","person-analyses-filter"].includes(form.id))return applyFilters(form);if(form.id==="plan-form"){await api.updatePlan(state.route.params.employeeId,{jobRoleId:Number(fd.get("jobRoleId")),currentLevel:fd.get("currentLevel"),targetLevel:fd.get("targetLevel"),characteristics:String(fd.get("characteristics")||"").split("\n").map(x=>x.trim()).filter(Boolean),expectedUpdatedAt:fd.get("expectedUpdatedAt")||null});state.drafts.delete(planDraftKey());toast("Contexto salvo.");return navigate(location.href,{history:"none"});}if(form.id==="objective-form"){const body={text:fd.get("text"),status:fd.get("status"),targetDate:fd.get("targetDate")||null,expectedUpdatedAt:fd.get("expectedUpdatedAt")||null};if(state.route.id==="objective-new")await api.createObjective(state.route.params.employeeId,body);else await api.updateObjective(state.route.params.employeeId,state.route.params.objectiveId,body);state.drafts.delete(objectiveDraftKey());toast("Objetivo salvo.");return go(`/manage/people/${state.route.params.employeeId}/career-plan`,'replace');}if(form.id==="terminology-form"){await api.terminology(Object.fromEntries(fd));state.drafts.delete("terminology");toast("Terminologia salva.");return navigate(location.href,{history:"none"});}if(form.id==="role-form"){const body={name:fd.get("name"),description:fd.get("description"),allowedLevelIds:fd.getAll("allowedLevelIds")};if(state.route.id==="role-new")await api.createRole(body);else await api.updateRole(state.route.params.roleId,body);state.drafts.delete(roleDraftKey());toast("Cargo salvo.");return go("/manage/career/roles","replace");}if(form.id==="github-form"){await api.saveGithub({repoSlug:fd.get("repoSlug"),authorLogin:fd.get("authorLogin")});state.drafts.delete("github-settings");toast("Configuração salva.");return navigate(location.href,{history:"none"});}if(form.id==="github-import-form")return searchGithub(form,fd);}catch(error){toast(error.message||"Não foi possível salvar. Seus dados permanecem no formulário.",true);}finally{setFormBusy(form,false);}}

function onInput(event){const el=event.target,form=el.form;if(el.id==="observation"){state.drafts.set(`evidence:${state.data.id}`,{observation:el.value});count(el);}if(form?.id==="review-form")state.drafts.set(`review:${state.route.params.analysisId}`,{comment:form.elements.comment.value});if(form?.id==="objective-form")captureDraft(objectiveDraftKey(),form);if(form?.id==="plan-form"){el.setCustomValidity("");captureDraft(planDraftKey(),form);}if(form?.id==="terminology-form")captureDraft("terminology",form);if(form?.id==="role-form"){form.querySelector('input[name="allowedLevelIds"]')?.setCustomValidity("");captureDraft(roleDraftKey(),form);}if(form?.id==="github-form")captureDraft("github-settings",form);if(form?.id==="github-import-form")captureDraft("github-import",form);}
function filterQueryDefaults(id){return id==="inbox-filter"?{status:"pending"}:id==="reviews-filter"?{status:"unreviewed"}:{};}
function applyFilters(form){
  const url=new URL(location.href);
  if(form.id==="people-directory-filter"){
    const detail=state.route.id!=="people",keys=detail?["dq","drole","dlevel","dpage"]:["q","role","level","page"];
    keys.forEach(key=>url.searchParams.delete(key));
    new FormData(form).forEach((value,key)=>{if(value)url.searchParams.set(detail?{q:"dq",role:"drole",level:"dlevel"}[key]:key,value)});
    url.searchParams.set(detail?"dpage":"page","1");return go(url.pathname+url.search);
  }
  const directory=state.route.id.startsWith("person-")?directorySuffix():"";
  url.search="";new FormData(form).forEach((v,k)=>{if(v)url.searchParams.set(k,v)});
  Object.entries(filterQueryDefaults(form.id)).forEach(([key,value])=>{if(!url.searchParams.has(key))url.searchParams.set(key,value)});
  if(directory)new URLSearchParams(directory).forEach((v,k)=>url.searchParams.set(k,v));
  if(form.id==="inbox-filter"&&state.route.query.selected)url.searchParams.set("selected",String(state.route.query.selected));
  url.searchParams.set("page","1");go(url.pathname+url.search);
}
function clearCompactFilters(form){const url=new URL(location.href);url.search="";Object.entries(filterQueryDefaults(form.id)).forEach(([key,value])=>url.searchParams.set(key,value));if(form.id==="inbox-filter"&&state.route.query.selected)url.searchParams.set("selected",String(state.route.query.selected));go(url.pathname+url.search);}
function go(path,history="push"){return navigate(new URL(path,location.href).href,{history});}
function redirectToLogin(destination=state.returnTo){const preserved=safeReturnTo(destination),target=`/login${preserved?`?returnTo=${encodeURIComponent(preserved)}`:""}`;if(`${location.pathname}${location.search}`!==target)history.replaceState({},"",target);state.route=matchRoute(location);state.loading=false;render();}
function expireSession(){if(state.expiring)return;state.expiring=true;state.epoch+=1;state.controller?.abort();state.user=null;state.validated=false;state.data=null;state.drafts.clear();redirectToLogin();}
function renderForbidden(){const mount=document.querySelector("#application-root");mount.innerHTML=shell(`<section class="workspace">${publicState("Permissão necessária",state.error,false,"section")}</section>`);bindDrawerDialog();requestAnimationFrame(()=>document.querySelector("#route-title")?.focus({preventScroll:true}));}

function landing(){return `<div class="public-page"><header class="public-nav"><a class="brand" href="/" data-link><img src="/frontend/assets/promova-designer-symbol.png" alt=""><span>promova</span></a><nav><a href="#how">Como funciona</a><a class="button" href="/login" data-link>Entrar</a></nav></header><main><section class="public-hero"><div><p class="eyebrow">Evidências para conversas melhores</p><h1>Transforme trabalho real em contexto de carreira.</h1><p>Capture pull requests do GitHub, organize evidências e conecte resultados ao framework da sua organização.</p><div class="actions"><a class="button primary" href="/register" data-link>Criar conta</a><a class="button" href="#how">Conhecer o fluxo</a></div></div><div class="hero-preview" aria-label="Prévia do produto"><span class="badge purple">PR #7 · GitHub</span><h2>Evidência pronta para revisar</h2><p>Fonte preservada, observação separada e análise vinculada ao framework.</p><div class="progress"><span style="width:68%"></span></div></div></section><section id="how" class="public-section"><h2>Do trabalho à conversa de carreira</h2><div class="grid-three"><article><strong>1 · Capture</strong><p>Importe pull requests sem reescrever o trabalho.</p></article><article><strong>2 · Contextualize</strong><p>Acrescente sua observação sem alterar a fonte.</p></article><article><strong>3 · Converse</strong><p>Compartilhe análises e histórico de revisão.</p></article></div><p class="note">Hoje, a captura integrada disponível é GitHub. Registros históricos de outras fontes permanecem legíveis.</p></section></main><footer>Promova · Evidências com contexto, não decisões automáticas.</footer></div>`;}
function auth(){const login=state.route.id==="login",returnTo=safeReturnTo(new URLSearchParams(location.search).get("returnTo"));return `<div class="auth-page"><a class="brand" href="/" data-link><img src="/frontend/assets/promova-designer-symbol.png" alt=""><span>promova</span></a><main><section class="auth-card"><p class="eyebrow">${login?"Bem-vindo de volta":"Comece com seu trabalho real"}</p><h1 id="route-title" tabindex="-1">${login?"Entrar":"Criar conta"}</h1><p>${login?"Acesse seu espaço pessoal ou de gestão.":"Crie seu acesso para organizar evidências de carreira."}</p><form data-auth-form="${login?"login":"register"}">${login?"":'<label>Nome completo<input name="name" autocomplete="name" required></label>'}<label>E-mail<input type="email" name="email" autocomplete="email" required></label><label>Senha<input type="password" name="password" autocomplete="${login?"current-password":"new-password"}" minlength="6" required></label><button class="button primary" type="submit">${login?"Entrar":"Criar conta"}</button></form><p>${login?"Ainda não tem uma conta?":"Já tem uma conta?"} <a data-link href="/${login?"register":"login"}${returnTo?`?returnTo=${encodeURIComponent(returnTo)}`:""}">${login?"Criar conta":"Entrar"}</a></p></section></main></div>`;}

function primaryAction(){if(state.route.id==="inbox")return `<a class="button primary" data-link href="/app/integrations/github/import">Importar PR</a>`;if(state.route.id==="roles")return `<a class="button primary" data-link href="/manage/career/roles/new">Novo cargo</a>`;return "";}
function panel(title,body,footer="",cls=""){return `<section class="panel ${cls}"><header><h2>${title}</h2></header><div class="panel-body">${body}</div>${footer?`<footer>${footer}</footer>`:""}</section>`;}
function evidenceRow(e,ownerId=state.user.id){const display=evidenceDisplay(e);return `<a class="row" data-link href="/workspace/people/${ownerId}/evidence/${e.id}"><div><strong>${esc(display.title)}</strong><small>${esc(display.summary)}</small><small>${esc(e.source)} · ${dateTime(e.occurredAt||e.capturedAt)}</small></div><span class="badge ${e.status==="PENDING"?"purple":""}">${labelStatus(e.status)}</span><span>›</span></a>`;}
function analysisRow(a,ownerId=state.user.id){const display=evidenceDisplay({content:a.evidence,sourceMeta:a.sourceMeta,source:a.source,analysisTitle:a.title});return `<a class="row" data-link href="/workspace/people/${ownerId}/analyses/${a.analysisId||a.id}"><div><strong>${esc(display.title)}</strong><small>${esc(display.summary)}</small><small>${esc(a.source)} · Confiança ${confidence(a.confidence)} · Impacto ${esc(a.impactLevel||"não informado")} · ${dateTime(a.createdAt)}</small></div><span class="badge">${labelStatus(a.currentReviewStatus||"UNREVIEWED")}</span><span>›</span></a>`;}
function personHeader(p){return `<section class="person-head"><span class="avatar">${initials(p.name)}</span><div><strong>${esc(p.name)}</strong><small>${esc(p.email)}</small></div><div><span class="tag">${esc(p.jobRoleName||"Cargo não definido")}</span><span class="tag">${esc(p.currentLevel||"—")} → ${esc(p.targetLevel||"—")}</span></div></section>`;}
function personTabs(p,active){return tabs([[personUrl(p.id,"career-plan"),"Plano de carreira"],[personUrl(p.id,"evidence"),"Evidências"],[personUrl(p.id,"analyses"),"Análises"]],active);}
function configTabs(active){return tabs([["/manage/career/roles","Cargos"],["/manage/career/terminology","Terminologia"],["/manage/career/framework","Framework"]],active);}
function tabs(items,active){return `<nav class="tabs" aria-label="Seções">${items.map(([href,label])=>`<a data-link href="${href}" ${href.includes(active)||label.toLowerCase().startsWith(active)?'aria-current="page"':''}>${esc(label)}</a>`).join("")}</nav>`;}
function breadcrumbs(items){return `<nav class="breadcrumbs" aria-label="Navegação estrutural">${items.map(([href,label])=>`<a data-link href="${href}">← ${esc(label)}</a>`).join("")}</nav>`;}
function pager(page,base){if(!page||page.total===undefined)return "";const q=new URLSearchParams(state.route.query);const current=page.page||1,max=Math.max(1,Math.ceil(page.total/page.pageSize));const linkTo=n=>{q.set("page",n);return `${base}?${q}`};return `<span>${page.total?`${(current-1)*page.pageSize+1}–${Math.min(current*page.pageSize,page.total)} de ${page.total}`:"0 resultados"}</span><div class="actions"><a class="button ${current<=1?"disabled":""}" ${current>1?'data-link':''} href="${linkTo(current-1)}" aria-disabled="${current<=1}">Anterior</a><a class="button ${current>=max?"disabled":""}" ${current<max?'data-link':''} href="${linkTo(current+1)}" aria-disabled="${current>=max}">Próxima</a></div>`;}
function loading(){return `<section class="panel fill" aria-busy="true" aria-live="polite"><div class="loading"><span class="spinner"></span><h2>Carregando conteúdo</h2><p>Validando dados desta página…</p></div></section>`;}
function errorState(){return panel("Não foi possível carregar",`<div class="state"><div aria-hidden="true">!</div><h2>Tente novamente</h2><p>${esc(state.error)}</p><button class="button primary" data-action="retry">Tentar novamente</button></div>`,"","fill");}
function empty(message){return `<div class="empty"><span aria-hidden="true">◇</span><p>${esc(message)}</p></div>`;}
function publicState(title,message,retry=false,tag="main"){return `<${tag} class="public-state"><a class="brand" href="/" data-link><img src="/frontend/assets/promova-designer-symbol.png" alt=""><span>promova</span></a><section><h1 id="route-title" tabindex="-1">${esc(title)}</h1><p>${esc(message)}</p>${retry?'<button class="button primary" data-action="retry">Tentar novamente</button>':'<a class="button primary" href="/" data-link>Voltar ao início</a>'}</section></${tag}>`;}

function dialogMarkup(){return `<dialog id="action-dialog"><form method="dialog"><header><h2 id="dialog-title">Confirmar</h2><button type="submit" value="cancel" aria-label="Fechar">×</button></header><div id="dialog-body"></div><footer id="dialog-actions"></footer></form></dialog>`;}
function showDialog(title,body,actions){const d=document.querySelector("#action-dialog");state.dialogTrigger=document.activeElement;d.querySelector("#dialog-title").textContent=title;d.querySelector("#dialog-body").innerHTML=body;const foot=d.querySelector("#dialog-actions");foot.innerHTML="";actions.forEach(({label,value="cancel",className="button"})=>{const b=document.createElement("button");b.value=value;b.textContent=label;b.className=className;foot.append(b)});d.showModal();return d;}
function confirmAction(title,message,callback){const d=showDialog(title,`<p>${esc(message)}</p>`,[{label:"Cancelar"},{label:"Confirmar",value:"confirm",className:"button danger"}]);d.addEventListener("close",async function once(){d.removeEventListener("close",once);if(d.returnValue==="confirm")try{await callback();}catch(error){toast(error.message||"A operação não foi concluída.",true);}},{once:true});}
function onArchiveRole(event){const button=event.target.closest('[data-action="archive-role"]');if(!button)return;event.preventDefault();event.stopImmediatePropagation();showArchiveDialog(false);}
function showArchiveDialog(required,affectedCount){const [roles]=state.data;const currentId=state.route.params.roleId;const candidates=roles.filter(role=>role.status==="ACTIVE"&&role.id!==currentId);const explanation=required?`<p class="note">${affectedCount||"Uma ou mais"} pessoas usam este cargo. Selecione um cargo ativo compatível para substituí-lo.</p>`:"<p>O cargo ficará somente leitura e não poderá ser atribuído novamente. Se houver pessoas vinculadas, escolha uma substituição.</p>";const options=`<label>Cargo substituto<select id="replacement-role" ${required?"required":""}><option value="">${required?"Selecione um cargo":"Nenhum, se não houver pessoas"}</option>${candidates.map(role=>`<option value="${role.id}">${esc(role.name)}</option>`).join("")}</select></label>`;const dialog=showDialog("Arquivar cargo",explanation+options,[{label:"Cancelar"},{label:"Arquivar",value:"confirm",className:"button danger"}]);dialog.addEventListener("close",async()=>{if(dialog.returnValue!=="confirm")return;const replacement=numberOrNull(document.querySelector("#replacement-role")?.value);if(required&&!replacement){toast("Selecione um cargo substituto.",true);return showArchiveDialog(true,affectedCount);}try{await api.archiveRole(currentId,replacement);toast("Cargo arquivado.");go("/manage/career/roles","replace");}catch(error){if(error.status===409&&!replacement)return showArchiveDialog(true,error.details?.affectedCount);toast(error.message||"Não foi possível arquivar o cargo.",true);}},{once:true});}
async function allowNavigation(){if(!state.drafts.size)return true;return new Promise(resolve=>{const d=showDialog("Alterações não salvas","<p>Há texto não salvo nesta página. Continue editando ou descarte para sair.</p>",[{label:"Continuar editando"},{label:"Descartar e sair",value:"discard",className:"button danger"}]);d.addEventListener("close",()=>{if(d.returnValue==="discard")state.drafts.clear();resolve(d.returnValue==="discard")},{once:true});});}
async function onPopState(){const destination=location.href,previous=state.route?canonicalUrl(state.route):"/";if(await allowNavigation())return navigate(destination,{history:"none"});history.pushState({},"",previous);}
function bindDrawerDialog(){const sidebar=document.querySelector("#navigation");if(!sidebar)return;sidebar.addEventListener("keydown",event=>{if(event.key==="Escape")closeMenu();if(event.key==="Tab"&&matchMedia("(max-width: 899px)").matches){const nodes=[...sidebar.querySelectorAll("a,button")];const first=nodes[0],last=nodes.at(-1);if(event.shiftKey&&document.activeElement===first){event.preventDefault();last.focus();}else if(!event.shiftKey&&document.activeElement===last){event.preventDefault();first.focus();}}});}
function bindActionDialog(){const dialog=document.querySelector("#action-dialog");dialog.addEventListener("keydown",event=>{if(event.key!=="Tab")return;const nodes=[...dialog.querySelectorAll("button:not(:disabled),input:not(:disabled),select:not(:disabled),textarea:not(:disabled),a[href]")];const first=nodes[0],last=nodes.at(-1);if(event.shiftKey&&document.activeElement===first){event.preventDefault();last?.focus();}else if(!event.shiftKey&&document.activeElement===last){event.preventDefault();first?.focus();}});dialog.addEventListener("close",()=>state.dialogTrigger?.focus());}
function openMenu(trigger){const sidebar=document.querySelector("#navigation");sidebar.classList.add("open");sidebar.setAttribute("role","dialog");sidebar.setAttribute("aria-modal","true");document.querySelector(".shell-main")?.setAttribute("inert","");trigger.setAttribute("aria-expanded","true");state.menuTrigger=trigger;sidebar.querySelector("a")?.focus();document.body.classList.add("drawer-open");}
function closeMenu(returnFocus=true){const sidebar=document.querySelector("#navigation");sidebar?.classList.remove("open");sidebar?.removeAttribute("role");sidebar?.removeAttribute("aria-modal");document.querySelector(".shell-main")?.removeAttribute("inert");state.menuTrigger?.setAttribute("aria-expanded","false");if(returnFocus)state.menuTrigger?.focus();document.body.classList.remove("drawer-open");}
function onDrawerNavigation(event){if(event.target.closest("#navigation.open a[data-link]"))closeMenu(false);}
function toast(message,error=false){const el=document.querySelector("#toast");el.textContent=message;el.classList.toggle("error",error);el.classList.add("visible");clearTimeout(state.toastTimer);state.toastTimer=setTimeout(()=>el.classList.remove("visible"),5000);}
function disable(button,label){button.disabled=true;button.dataset.original=button.textContent;button.textContent=label;}
function restoreButton(button){button.disabled=false;if(button.dataset.original){button.textContent=button.dataset.original;delete button.dataset.original;}}
function setFormBusy(form,busy){if(form.classList.contains("compact-filter-popover")||["inbox-filter","people-directory-filter"].includes(form.id))return;form.querySelectorAll("button,input,select,textarea").forEach(el=>{if(el.type!=="hidden")el.disabled=busy});}
function count(el){const c=document.querySelector(`[data-count-for="${el.id}"]`);if(c)c.textContent=`${el.value.length}/2000`;}
function draft(key){return state.drafts.get(key);}
function captureDraft(key,form){const data={};for(const [name,value] of new FormData(form)){if(name==="expectedUpdatedAt")continue;if(name==="allowedLevelIds"){(data[name]??=[]).push(value);}else data[name]=value;}if(form.id==="role-form"&&!data.allowedLevelIds)data.allowedLevelIds=[];state.drafts.set(key,data);}
function objectiveDraftKey(){return `objective:${state.route.params.employeeId}:${state.route.params.objectiveId||"new"}`;}
function planDraftKey(){return `plan:${state.route.params.employeeId}`;}
function roleDraftKey(){return `role:${state.route.params.roleId||"new"}`;}
function validateCharacteristics(textarea){const lines=textarea.value.split("\n").map(value=>value.trim()).filter(Boolean);const message=lines.length>10?"Informe no máximo 10 características.":lines.some(value=>value.length>120)?"Cada característica deve ter no máximo 120 caracteres.":"";textarea.setCustomValidity(message);if(message){textarea.reportValidity();return false;}return true;}
function validateRoleLevels(form){const first=form.querySelector('input[name="allowedLevelIds"]'),valid=form.querySelectorAll('input[name="allowedLevelIds"]:checked').length>=2;first?.setCustomValidity(valid?"":"Selecione ao menos dois níveis permitidos.");if(!valid)first?.reportValidity();return valid;}
function pageItems(value){return Array.isArray(value)?value:value?.items||[];}
function sel(a,b){return String(a??"")===String(b??"")?"selected":"";}
function confidence(v){return ({HIGH:"alta",MEDIUM:"média",LOW:"baixa"}[String(v||"").toUpperCase()]||"não informada");}
function labelStatus(v){return ({PENDING:"Pendente",DISMISSED:"Dispensada",ANALYZED:"Analisada",UNREVIEWED:"Não revisada",ACCEPTED:"Aceita",NEEDS_CONTEXT:"Precisa de contexto",ACTIVE:"Ativo",COMPLETED:"Concluído",ARCHIVED:"Arquivado"}[v]||"Não informado");}
function date(v){return v?new Intl.DateTimeFormat("pt-BR",{dateStyle:"medium"}).format(new Date(`${v}T12:00:00`)):"—";}
function dateTime(v){return v?new Intl.DateTimeFormat("pt-BR",{dateStyle:"medium",timeStyle:"short"}).format(new Date(v)):"Data não informada";}
function initials(name=""){return name.split(/\s+/).slice(0,2).map(x=>x[0]).join("").toUpperCase()||"P";}
function safeExternal(value){try{const u=new URL(value);return ["http:","https:"].includes(u.protocol)?esc(u.href):"#";}catch{return "#";}}
function navActive(href){const route=matchRoute(new URL(href,location.href));if(route.id==="people")return state.route.id.startsWith("person-")||state.route.id.startsWith("objective")||state.route.id==="people";if(route.id==="roles")return ["roles","role","role-new","terminology","manager-framework"].includes(state.route.id);if(route.id==="integrations")return ["integrations","github","github-import"].includes(state.route.id);if(route.id==="analyses")return ["analyses","reports","analysis-detail"].includes(state.route.id)&&state.user?.role==="EMPLOYEE";return route.id===state.route.id;}
function navIcon(label){
  const paths={
    "Visão geral":`<path d="M3 11.5 12 3l9 8.5V21H3z"/><path d="M9 21v-7h6v7"/>`,
    "Visão da gestão":`<rect x="3" y="3" width="8" height="8" rx="1"/><rect x="13" y="3" width="8" height="8" rx="1"/><rect x="3" y="13" width="8" height="8" rx="1"/><rect x="13" y="13" width="8" height="8" rx="1"/>`,
    "Caixa de entrada":`<path d="M4 4h16l2 10v7H2v-7z"/><path d="M2 14h6l2 3h4l2-3h6"/>`,
    "Análises":`<path d="M4 20V11M10 20V5M16 20v-9M22 20V2"/>`,
    "Pessoas":`<circle cx="12" cy="8" r="4"/><path d="M4 21v-2a8 8 0 0 1 16 0v2"/>`,
    "Plano de carreira":`<path d="m4 20 5-.8L20 8a2 2 0 0 0-4-4L5 15z"/><path d="m14 6 4 4"/>`,
    "Framework":`<rect x="3" y="3" width="18" height="18" rx="2"/><path d="M3 9h18M9 3v18M15 9v12"/>`,
    "Revisões":`<path d="M7 3h10l4 4v14H3V3z"/><path d="M8 12h8M8 16h8"/>`,
    "Integrações":`<path d="M8 3v5M16 3v5M5 8h14v4a7 7 0 0 1-14 0zM12 19v3"/>`,
    "Equipes":`<circle cx="9" cy="8" r="3"/><circle cx="17" cy="9" r="2"/><path d="M2 20v-2a7 7 0 0 1 14 0v2M17 15a5 5 0 0 1 5 5"/>`
  };
  const path=paths[label]||`<circle cx="12" cy="12" r="8"/>`;
  return `<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${path}</svg>`;
}
function levelOptions(profile,selected){return (profile.levels||[]).map(l=>`<option value="${esc(l.id||l.key)}" ${sel(selected,l.id||l.key)}>${esc(l.id||l.key)} · ${esc(l.title)}</option>`).join("");}
function numberOrNull(v){return v?Number(v):null;}
async function searchGithub(form,fd,page=1){const parts=String(fd.get("repoSlug")).split("/").filter(Boolean);if(parts.length!==2)throw new Error("Use o formato organização/repositório.");const result=await api.searchGithub(parts[0],parts[1],`author:${fd.get("authorLogin")}`,page);const root=document.querySelector("#github-results"),items=result.items||[],current=result.page||page,pageSize=result.page_size||8,total=result.total_count??items.length,max=Math.max(1,Math.ceil(total/pageSize));root.innerHTML=items.length?`<div class="row-list">${items.map(pr=>`<button class="row" type="button" data-import-pr="${pr.number}" data-repo="${esc(fd.get("repoSlug"))}"><div><strong>PR #${pr.number} · ${esc(pr.title)}</strong><small>${esc(pr.user?.login||pr.authorLogin||"")}</small></div><span>Importar</span></button>`).join("")}</div><div class="pager"><span>${(current-1)*pageSize+1}–${Math.min(current*pageSize,total)} de ${total}</span><div class="actions"><button class="button" type="button" data-search-page="${current-1}" ${current<=1?"disabled":""}>Anterior</button><button class="button" type="button" data-search-page="${current+1}" ${current>=max?"disabled":""}>Próxima</button></div></div>`:empty("Nenhum pull request encontrado.");root.querySelectorAll("[data-search-page]").forEach(b=>b.addEventListener("click",()=>searchGithub(form,new FormData(form),Number(b.dataset.searchPage)).catch(error=>toast(error.message,true))));root.querySelectorAll("[data-import-pr]").forEach(b=>b.addEventListener("click",async()=>{disable(b,"Importando…");try{const e=await api.captureGithub({repo:b.dataset.repo,pullNumber:Number(b.dataset.importPr),usernameHint:fd.get("authorLogin")});state.drafts.delete("github-import");go(`/workspace/people/${state.user.id}/evidence/${e.id}`);}catch(error){restoreButton(b);toast(error.message,true);}}));}
