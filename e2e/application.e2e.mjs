import assert from "node:assert/strict";
import { spawn } from "node:child_process";
import { mkdir, mkdtemp, rm, writeFile } from "node:fs/promises";
import http from "node:http";
import os from "node:os";
import path from "node:path";
import test from "node:test";
import { fileURLToPath } from "node:url";
import { chromium } from "playwright-core";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const chrome = process.env.PROMOVA_E2E_CHROME || "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome";
const backendPort = Number(process.env.PROMOVA_E2E_BACKEND_PORT || 18082);
const frontendPort = Number(process.env.PROMOVA_E2E_FRONTEND_PORT || 14175);
const apiUrl = `http://127.0.0.1:${backendPort}`;
const appUrl = `http://127.0.0.1:${frontendPort}`;
const screenshotDir = path.resolve(process.env.PROMOVA_E2E_SCREENSHOT_DIR || path.join(root, "artifacts/designer-ux-ui-2026-09-27/actual"));
const routeTimings = [];
const interactionTimings = [];

test("production application covers both roles, canonical resources, history and reflow", { timeout: 300_000 }, async (t) => {
  const temp = await mkdtemp(path.join(os.tmpdir(), "promova-application-e2e-"));
  const processes = [];
  let browser;
  const github = await startGithubStub();
  t.after(async () => {
    await browser?.close().catch(() => {});
    github.close();
    await Promise.all(processes.map(stopProcess));
    await rm(temp, { recursive: true, force: true });
  });

  const backend = startProcess("bash", ["./gradlew", "bootRun", `--args=--spring.profiles.active=dev --server.address=127.0.0.1 --server.port=${backendPort}`], {
    cwd: path.join(root, "backend"),
    env: {
      ...process.env,
      PROMOVA_DEV_DB_URL: `jdbc:h2:file:${path.join(temp, "promova")};DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE`,
      PROMOVA_CORS_ALLOWED_ORIGINS: appUrl,
      GITHUB_API_BASE_URL: `http://127.0.0.1:${github.address().port}`,
      GITHUB_API_TOKEN: "e2e-secret",
    },
  });
  const frontend = startProcess("node", ["scripts/dist-server.js"], { cwd:root, env:{...process.env,HOST:"127.0.0.1",PORT:String(frontendPort)} });
  processes.push(backend,frontend);
  await Promise.all([waitFor(`${apiUrl}/auth/me`,[401],90_000),waitFor(`${appUrl}/app/overview`,[200],30_000)]);
  assert.equal((await fetch(`${appUrl}/missing-module.js`)).status,404);
  assert.equal((await fetch(`${appUrl}/dashboard`,{headers:{Accept:"text/html"}})).status,404);
  assert.equal((await fetch(`${appUrl}/manager`,{headers:{Accept:"text/html"}})).status,404);
  assert.match((await (await fetch(`${appUrl}/app/overview`,{headers:{Accept:"text/html"}})).text()),/<div id="app"><\/div>/);

  const employeeToken = await loginApi("joao.silva@empresa.com","senha123");
  const managerToken = await loginApi("manager@promova.com","manager123");
  const me = await api("/auth/me",employeeToken);
  const pending = await api("/evidences/github/pull-request",employeeToken,"POST",{repo:"acme/project",pullNumber:8,usernameHint:"joao"});
  const dialogEvidence = await api("/evidences/github/pull-request",employeeToken,"POST",{repo:"acme/project",pullNumber:9,usernameHint:"joao"});
  const analyzedEvidence = await api("/evidences/github/pull-request",employeeToken,"POST",{repo:"acme/project",pullNumber:7,usernameHint:"joao"});
  const analysis = await api(`/evidences/${analyzedEvidence.id}/analysis`,employeeToken,"POST",{userObservation:"Liderei a implantação, documentei os resultados e reduzi o tempo de recuperação em 35%."});
  const concurrentEvidence = await api("/evidences/github/pull-request",employeeToken,"POST",{repo:"acme/project",pullNumber:10,usernameHint:"joao"});
  const concurrentAnalysis = await api(`/evidences/${concurrentEvidence.id}/analysis`,employeeToken,"POST",{userObservation:"Valida a primeira decisão concorrente."});
  await seedEvidences(employeeToken,500);
  const taggedInbox=await api("/evidences/inbox?bucket=pending&q=PR%20%238&page=1&pageSize=25",employeeToken);
  assert.equal(taggedInbox.items.find(item=>item.id===pending.id)?.author,"joao");
  assert.deepEqual(taggedInbox.items.find(item=>item.id===pending.id)?.tags,["backend","integração"]);
  const boundedPage=await api("/evidences?status=PENDING&page=1&pageSize=25",employeeToken);
  assert.ok(boundedPage.total>=500,"500-record fixture is visible through bounded pagination");
  assert.equal(boundedPage.items.length,25,"the browser never receives the full stress fixture");
  const people = await api("/manager/employees?page=1&pageSize=25",managerToken);
  const employee = people.items.find(person=>person.email==="joao.silva@empresa.com");
  assert.equal(employee.id,me.id);
  const concurrentReviewUrl=`${apiUrl}/manager/employees/${employee.id}/analyses/${concurrentAnalysis.analysisId}/reviews`;
  const concurrentReviews=await Promise.all(["ACCEPTED","NEEDS_CONTEXT"].map(status=>fetch(concurrentReviewUrl,{method:"POST",headers:{Authorization:`Bearer ${managerToken}`,"Content-Type":"application/json"},body:JSON.stringify({status,expectedLatestReviewId:0,idempotencyKey:`concurrent-${status}`})})));
  assert.deepEqual(concurrentReviews.map(response=>response.status).sort(),[201,409],"exactly one concurrent first review commits");
  const concurrentHistory=await api(`/manager/employees/${employee.id}/analyses/${concurrentAnalysis.analysisId}/reviews`,managerToken);
  assert.equal(concurrentHistory.history.length,1,"the concurrent review stream remains append-once");
  await api(`/manager/employees/${employee.id}/analyses/${analysis.analysisId}/reviews`,managerToken,"POST",{status:"NEEDS_CONTEXT",comment:"Inclua as métricas do período seguinte na próxima conversa.",idempotencyKey:"e2e-review"});

  await mkdir(screenshotDir,{recursive:true});
  browser = await chromium.launch({executablePath:chrome,headless:true});
  const context = await browser.newContext({viewport:{width:1440,height:1000},deviceScaleFactor:1});
  const page = await context.newPage();
  const pageErrors=[],consoleErrors=[],httpErrors=[];
  page.on("pageerror",error=>pageErrors.push(error.message));
  page.on("console",message=>{if(message.type()==="error")consoleErrors.push(message.text());});
  page.on("response",response=>{if(response.status()>=400)httpErrors.push({status:response.status(),url:response.url()});});
  await capture(page,"/","01-public-landing.png");
  await capture(page,"/login","02-auth-login.png");
  await capture(page,"/register","03-auth-register.png");
  const emptyEmail=`inbox-empty-${Date.now()}@example.test`;
  await api("/auth/register",null,"POST",{name:"Pessoa sem evidências",email:emptyEmail,password:"senha123"});
  const emptyContext=await browser.newContext({viewport:{width:1440,height:1000}});
  const emptyPage=await emptyContext.newPage();
  await loginUi(emptyPage,emptyEmail,"senha123","/app/inbox?status=pending");
  await emptyPage.getByText("Sua caixa de entrada está vazia.").waitFor();
  assert.equal(await emptyPage.locator('#inbox-preview').count(),0);
  await emptyPage.screenshot({path:path.join(screenshotDir,"29-inbox-empty.png")});
  await emptyPage.setViewportSize({width:390,height:844});
  await emptyPage.waitForTimeout(300);
  await emptyPage.screenshot({path:path.join(screenshotDir,"33-mobile-inbox-empty.png")});
  await emptyContext.close();
  await loginUi(page,"joao.silva@empresa.com","senha123","/app/overview");
  await capture(page,"/app/overview","04-employee-overview.png",true);
  await page.locator(".skip-link").focus();
  assert.equal(await page.locator(".skip-link").evaluate(node=>node===document.activeElement),true);
  await page.keyboard.press("Enter");
  assert.equal(new URL(page.url()).hash,"#main-content");
  const cachedStarted=performance.now();
  await page.locator('#navigation a[href="/app/inbox?status=pending"]').click();
  await page.waitForURL("**/app/inbox?status=pending");
  await page.locator('[aria-busy="true"]').waitFor({state:"detached"});
  const cachedNavigationMs=Math.round(performance.now()-cachedStarted);
  interactionTimings.push({name:"overview-to-inbox",durationMs:cachedNavigationMs});
  assert.ok(cachedNavigationMs<200,`cached navigation took ${cachedNavigationMs}ms`);
  assert.equal(await page.locator('#inbox-filter').isVisible(),true,"search and filters are available");
  assert.equal(await page.locator('.inbox-buckets a[aria-current="page"]').innerText().then(text=>text.includes("Pendentes")),true);
  await openInboxFilters(page);
  await addInboxFilter(page,"source");
  await page.locator('#inbox-filter input[name="source"]').fill("GitHub");
  await page.locator('#inbox-filter button[type="submit"]').click();
  await page.waitForURL(url=>url.pathname==="/app/inbox"&&url.searchParams.get("status")==="pending"&&url.searchParams.get("source")==="GitHub"&&url.searchParams.get("page")==="1");
  await capture(page,`/workspace/people/${employee.id}/analyses/${analysis.analysisId}?view=summary`,"05-employee-analysis-summary.png",true);
  await capture(page,`/workspace/people/${employee.id}/analyses/${analysis.analysisId}?view=history`,"06-employee-review-history.png",true);
  await capture(page,"/app/framework","07-employee-framework.png",true);
  await openFilters(page,"framework-filter");
  await addFilter(page,"framework-filter","support");
  await page.locator('#framework-filter select[name="support"]').selectOption("supported");
  await page.locator('#framework-filter button[type="submit"]').click();
  await page.waitForURL(url=>url.pathname==="/app/framework"&&url.searchParams.get("support")==="supported");
  await openFilters(page,"framework-filter");
  await page.locator('#framework-filter [data-action="clear-compact-filters"]').click();
  await page.waitForURL(url=>url.pathname==="/app/framework"&&!url.searchParams.has("support"));
  await page.locator('[aria-busy="true"]').waitFor({state:"detached"});
  const criterionHref = await page.locator('.row-list a[href*="/criteria/"]').first().getAttribute("href");
  if (criterionHref) await capture(page,criterionHref,"08-employee-criterion.png",true);
  await page.goto(`${appUrl}/app/analyses/reports`);
  await page.locator('[aria-busy="true"]').waitFor({state:"detached"});
  await openFilters(page,"reports-filter");
  await addFilter(page,"reports-filter","view");
  await page.locator('#reports-filter select[name="view"]').selectOption("levels");
  await page.locator('#reports-filter button[type="submit"]').click();
  await page.waitForURL(url=>url.pathname==="/app/analyses/reports"&&url.searchParams.get("view")==="levels");
  await openFilters(page,"reports-filter");
  await page.locator('#reports-filter [data-action="clear-compact-filters"]').click();
  await page.waitForURL(url=>url.pathname==="/app/analyses/reports"&&!url.searchParams.has("view"));
  await capture(page,"/app/integrations/github","09-employee-github.png",true);
  await page.locator('#github-form input[name="repoSlug"]').fill("acme/project");
  await page.locator('#github-form input[name="authorLogin"]').fill("joao");
  const githubSave=page.waitForResponse(response=>response.url().endsWith("/api/github/settings")&&response.request().method()==="PUT");
  await page.getByRole("button",{name:"Salvar",exact:true}).click();
  assert.equal((await githubSave).status(),200);
  await page.reload();
  await page.locator('[aria-busy="true"]').waitFor({state:"detached"});
  assert.equal(await page.locator('#github-form input[name="repoSlug"]').inputValue(),"acme/project");
  await capture(page,"/app/integrations/github/import","26-employee-github-search.png",true);
  await page.locator('#github-import-form input[name="repoSlug"]').fill("acme/project");
  await page.locator('#github-import-form input[name="authorLogin"]').fill("joao");
  await page.getByRole("button",{name:"Pesquisar"}).click();
  await page.getByRole("button",{name:"Próxima"}).waitFor();
  await page.getByRole("button",{name:"Próxima"}).click();
  await page.getByText("9–10 de 10").waitFor();
  await page.getByRole("button",{name:/Importar/}).first().click();
  await page.waitForURL(url=>url.pathname.startsWith(`/workspace/people/${employee.id}/evidence/`));
  await capture(page,"/app/career-plan?view=context","10-employee-career-context.png",true);
  await capture(page,"/app/inbox?status=pending","11-employee-inbox.png",true);
  for (const [width,height,name] of [[1600,1000,"34-inbox-1600.png"],[1280,900,"35-inbox-1280.png"],[768,1024,"36-inbox-tablet.png"]]) {
    await page.setViewportSize({width,height});
    await capture(page,"/app/inbox?status=pending",name,true);
  }
  await page.setViewportSize({width:1440,height:1000});
  await capture(page,"/app/inbox?status=pending","11-employee-inbox.png",true);
  assert.equal(await page.locator('.inbox-item[aria-pressed="true"]').count(),1,"the inbox has one selected record");
  assert.match(await page.locator('.inbox-item.selected strong').innerText(),/Improve team delivery/);
  assert.ok((await page.locator('#inbox-preview').innerText()).length<700,"the preview excludes the long source body");
  const selectedId=await page.locator('.inbox-item').nth(1).getAttribute('data-evidence-id');
  await openInboxFilters(page);
  await page.locator('.inbox-item').nth(1).click();
  await page.waitForURL(url=>url.pathname==="/app/inbox"&&url.searchParams.get("selected")===selectedId);
  assert.equal(await page.locator('#inbox-filter').isVisible(),true,"filters remain available after selection");
  assert.equal(await page.locator('.inbox-item[aria-pressed="true"]').count(),1);
  assert.equal(await page.locator('#inbox-preview').getAttribute('data-evidence-id'),selectedId);
  await page.screenshot({path:path.join(screenshotDir,"27-inbox-selected.png")});
  await openInboxFilters(page);
  await addInboxFilter(page,"source");
  await page.locator('#inbox-filter input[name="source"]').fill("GitHub");
  await page.locator('#inbox-filter button[type="submit"]').click();
  await page.waitForURL(url=>url.searchParams.get("source")==="GitHub"&&url.searchParams.get("selected")===selectedId);
  await page.locator('[aria-busy="true"]').waitFor({state:"detached"});
  assert.equal(await page.locator('#inbox-preview').getAttribute('data-evidence-id'),selectedId,"filtering keeps a visible selection");
  const inboxReloaded=page.waitForResponse(response=>response.url().includes("/evidences/inbox")&&response.status()===200);
  await page.reload();
  await inboxReloaded;
  await page.waitForFunction(id=>document.querySelector('#inbox-preview')?.getAttribute('data-evidence-id')===id&&!document.querySelector('[aria-busy="true"]'),selectedId);
  assert.equal(await page.locator('#inbox-preview').getAttribute('data-evidence-id'),selectedId,"reload keeps the selected record");
  const firstId=await page.locator('.inbox-item').first().getAttribute('data-evidence-id');
  await page.locator('.inbox-item').first().focus();
  await page.keyboard.press("Enter");
  assert.equal(await page.locator('#inbox-preview').getAttribute('data-evidence-id'),firstId,"keyboard selection updates the preview");
  await page.waitForFunction(() => document.querySelector('#inbox-preview h3') === document.activeElement);
  assert.equal(await page.locator('#inbox-preview h3').evaluate(node=>node===document.activeElement),true,"keyboard selection moves reading focus");
  await openInboxFilters(page);
  await page.locator('#inbox-filter input[name="source"]').fill("Fonte inexistente");
  await page.locator('#inbox-filter button[type="submit"]').click();
  await page.getByText("Nenhuma evidência corresponde aos filtros.").waitFor();
  await page.screenshot({path:path.join(screenshotDir,"31-inbox-no-results.png")});
  assert.equal(await page.locator('#inbox-preview').count(),0);
  assert.equal(new URL(page.url()).searchParams.has("selected"),false,"a filtered-out selection is cleared");
  await page.route('**/evidences/inbox?**',route=>route.abort('failed'));
  await page.goto(`${appUrl}/app/inbox?status=pending`);
  await page.getByRole('heading',{name:'Tente novamente'}).waitFor();
  await page.unroute('**/evidences/inbox?**');
  await page.getByRole('button',{name:'Tentar novamente'}).click();
  await page.locator('.inbox-item').first().waitFor();
  await openInboxFilters(page);
  await page.goto(`${appUrl}/app/inbox?status=pending`);
  await page.waitForURL(url=>url.pathname==="/app/inbox"&&url.searchParams.get("status")==="pending"&&!url.searchParams.has("source"));
  await page.locator('[aria-busy="true"]').waitFor({state:"detached"});
  const occurred=new Date(pending.occurredAt);
  const localDay=`${occurred.getFullYear()}-${String(occurred.getMonth()+1).padStart(2,"0")}-${String(occurred.getDate()).padStart(2,"0")}`;
  await openInboxFilters(page);
  await addInboxFilter(page,"from");
  await addInboxFilter(page,"to");
  await page.locator('#inbox-filter input[name="from"]').fill(localDay);
  await page.locator('#inbox-filter input[name="to"]').fill(localDay);
  await page.locator('#inbox-filter button[type="submit"]').click();
  await page.waitForURL(url=>url.searchParams.get("from")===localDay&&url.searchParams.get("to")===localDay);
  await page.locator('[aria-busy="true"]').waitFor({state:"detached"});
  assert.ok(await page.locator('.inbox-item').count()>0,"inclusive date filters retain same-day records");
  await openInboxFilters(page);
  await page.goto(`${appUrl}/app/inbox?status=pending`);
  await page.locator('[aria-busy="true"]').waitFor({state:"detached"});
  const acceptedPage=await api("/evidences/inbox?bucket=accepted&page=1&pageSize=25",employeeToken);
  const acceptedLoaded=page.waitForResponse(response=>response.url().includes("/evidences/inbox")&&response.url().includes("bucket=accepted"));
  await page.locator('.inbox-buckets a[href*="status=accepted"]').click();
  await page.waitForURL(url=>url.searchParams.get("status")==="accepted");
  await acceptedLoaded;
  await page.locator('[aria-busy="true"]').waitFor({state:"detached"});
  assert.equal(await page.locator('.inbox-item').count(),acceptedPage.items.length,"accepted tab mirrors current server reviews");
  assert.equal(acceptedPage.items.every(item=>item.currentReviewStatus==="ACCEPTED"),true);
  const allLoaded=page.waitForResponse(response=>response.url().includes("/evidences/inbox")&&response.url().includes("bucket=all"));
  await page.locator('.inbox-buckets a[href*="status=all"]').click();
  await page.waitForURL(url=>url.searchParams.get("status")==="all");
  await allLoaded;
  await page.waitForFunction(()=>document.querySelector(".inbox-list h2")?.textContent.includes("evidências"));
  await page.waitForTimeout(300);
  await page.locator('[aria-busy="true"]').waitFor({state:"detached"});
  assert.equal(await page.locator('.inbox-item[aria-pressed="true"]').count(),1);
  await page.locator('#inbox-filter input[name="q"]').click();
  assert.equal(await page.locator('#inbox-filter input[name="q"]').evaluate(node=>node===document.activeElement),true,`search focus: ${await page.evaluate(()=>document.activeElement?.outerHTML)}`);
  await page.keyboard.type("Improve team delivery");
  assert.equal(await page.locator('#inbox-filter input[name="q"]').inputValue(),"Improve team delivery","search retains typed text");
  await page.locator('#inbox-filter select[name="sort"]').selectOption("oldest");
  assert.equal(await page.locator('#inbox-filter input[name="q"]').inputValue(),"Improve team delivery");
  assert.equal(await page.locator('#inbox-filter').evaluate(form=>new FormData(form).get("q")),"Improve team delivery");
  await page.locator('#inbox-filter button[type="submit"]').click();
  await page.waitForURL(url=>url.searchParams.get("q")==="Improve team delivery"&&url.searchParams.get("sort")==="oldest",{timeout:5000}).catch(async()=>{throw new Error(`Inbox search URL: ${page.url()} · ${await page.locator("#toast").innerText()}`)});
  await page.locator('[aria-busy="true"]').waitFor({state:"detached"});
  assert.ok(await page.locator('.inbox-item').count()>0);
  const unsafeEvidence=await api("/evidences/github/pull-request",employeeToken,"POST",{repo:"acme/project",pullNumber:12,usernameHint:"joao"});
  await page.goto(`${appUrl}/app/inbox?status=pending&selected=${unsafeEvidence.id}`);
  await page.locator('[aria-busy="true"]').waitFor({state:"detached"});
  assert.match(await page.locator('.inbox-item.selected strong').innerText(),/<img src=x onerror=alert\(1\)>/);
  assert.equal(await page.locator('.inbox-item img, #inbox-preview img').count(),0,"source HTML remains text in list and preview");
  await capture(page,`/workspace/people/${employee.id}/evidence/${pending.id}`,"12-employee-evidence.png",true);
  await capture(page,"/app/analyses","13-employee-analyses.png",true);
  await openFilters(page,"analyses-filter");
  await addFilter(page,"analyses-filter","source");
  await page.locator('#analyses-filter input[name="source"]').fill("GitHub");
  await page.locator('#analyses-filter button[type="submit"]').click();
  await page.waitForURL(url=>url.pathname==="/app/analyses"&&url.searchParams.get("source")==="GitHub");
  await openFilters(page,"analyses-filter");
  await page.locator('#analyses-filter [data-action="clear-compact-filters"]').click();
  await page.waitForURL(url=>url.pathname==="/app/analyses"&&!url.searchParams.has("source"));
  await page.setViewportSize({width:390,height:844});
  await capture(page,"/app/inbox?status=pending","21-mobile-employee-inbox.png",true);
  await page.locator('.inbox-item').nth(1).click();
  const mobileSelectedId=await page.locator('.inbox-item').nth(1).getAttribute('data-evidence-id');
  await page.waitForFunction(()=>{const element=document.querySelector('#inbox-preview');const box=element?.getBoundingClientRect();return box&&box.top>=0&&box.top<innerHeight;});
  await page.waitForTimeout(550);
  assert.equal(await page.locator('#inbox-preview').isVisible(),true);
  assert.equal(await horizontalOverflow(page),0,"mobile inbox preview does not overflow horizontally");
  await page.screenshot({path:path.join(screenshotDir,"28-mobile-inbox-preview.png")});
  const sourceLoaded=page.waitForResponse(response=>response.url().endsWith(`/evidences/${mobileSelectedId}`));
  await page.locator("#inbox-preview .inbox-technical summary").click();
  await sourceLoaded;
  await page.waitForFunction(()=>document.querySelector("#inbox-preview .inbox-technical pre")?.textContent.includes("Long evidence context"));
  assert.equal(await horizontalOverflow(page),0,"expanded technical source stays within mobile width");
  await page.screenshot({path:path.join(screenshotDir,"37-mobile-inbox-technical.png")});
  await page.setViewportSize({width:320,height:740});
  await capture(page,"/app/inbox?status=pending","30-mobile-320-inbox.png",true);
  await page.setViewportSize({width:390,height:844});
  await capture(page,`/workspace/people/${employee.id}/evidence/${pending.id}`,"22-mobile-employee-evidence.png",true);
  await page.getByRole("button",{name:"Analisar evidência"}).click();
  await page.waitForURL(url=>url.pathname.startsWith(`/workspace/people/${employee.id}/analyses/`));
  await page.setViewportSize({width:1440,height:1000});
  await page.goto(`${appUrl}/workspace/people/${employee.id}/evidence/${dialogEvidence.id}`);
  await page.locator('[aria-busy="true"]').waitFor({state:"detached"});
  await page.getByRole("button",{name:"Dispensar"}).click();
  const evidenceDialog=page.locator("#action-dialog");
  await evidenceDialog.waitFor({state:"visible"});
  await evidenceDialog.getByRole("button",{name:"Cancelar"}).click();
  await evidenceDialog.waitFor({state:"hidden"});
  assert.equal(new URL(page.url()).pathname,`/workspace/people/${employee.id}/evidence/${dialogEvidence.id}`);
  await page.getByRole("button",{name:"Dispensar"}).click();
  await evidenceDialog.waitFor({state:"visible"});
  await evidenceDialog.getByRole("button",{name:"Confirmar"}).click();
  await page.waitForURL("**/app/inbox?status=pending");
  await page.getByRole("button",{name:"Sair"}).click();
  await page.goto(`${appUrl}/login`);
  await page.locator('input[name="email"]').fill("manager@promova.com");
  await page.locator('input[name="password"]').fill("manager123");
  await page.getByRole("button",{name:"Entrar"}).click();
  await page.waitForURL(url=>url.pathname==="/manage/dashboard");
  await page.getByRole("heading",{name:"Visão da gestão"}).waitFor();
  await page.locator('.dashboard-metrics article').first().waitFor();
  assert.equal(await page.locator('.dashboard-metrics article').count(),5);
  await assertDashboardPanelLayout(page);
  await capture(page,"/manage/dashboard","13-manager-dashboard.png",true);
  await page.locator('#dashboard-filter select[name="period"]').selectOption("90");
  await page.locator('#dashboard-filter button[type="submit"]').click();
  await page.waitForURL(url=>url.pathname==="/manage/dashboard"&&url.searchParams.get("period")==="90");
  await page.goBack();
  await page.waitForURL(url=>url.pathname==="/manage/dashboard"&&!url.searchParams.has("period"));
  await page.getByRole("link",{name:"Equipes",exact:true}).click();
  await page.waitForURL(url=>url.pathname==="/manage/teams");
  await page.locator('#create-team-form input[name="name"]').fill("Equipe de produto");
  await page.locator('#create-team-form button[type="submit"]').click();
  await page.waitForURL(url=>url.pathname==="/manage/teams"&&url.searchParams.has("team"));
  await page.locator('#assign-team-member-form select[name="employeeId"]').selectOption(String(employee.id));
  await page.locator('#assign-team-member-form button[type="submit"]').click();
  await page.getByRole("heading",{name:"Membros da equipe"}).waitFor();
  await page.getByText("João Silva",{exact:true}).first().waitFor();
  await page.locator('a.brand').click();
  await page.waitForURL(url=>url.pathname==="/manage/dashboard");
  await page.getByRole("link",{name:"Pessoas",exact:true}).click();
  await page.waitForURL(url=>url.pathname==="/manage/people");
  await capture(page,"/manage/people","32-manager-people-unselected.png",true);
  await page.locator('#people-directory-filter input[name="q"]').fill("joao.silva");
  await page.locator('#people-directory-filter input[name="level"]').fill(employee.currentLevel);
  await page.locator('#people-directory-filter button[type="submit"]').click();
  await page.waitForURL(url=>url.pathname==="/manage/people"&&url.searchParams.get("q")==="joao.silva"&&url.searchParams.get("level")===employee.currentLevel&&url.searchParams.get("page")==="1");
  await page.waitForFunction(()=>document.querySelectorAll('.people-row').length===1&&!document.querySelector('[aria-busy="true"]'));
  await page.locator('.people-row').first().click();
  await page.waitForURL(url=>url.pathname===`/manage/people/${employee.id}/career-plan`&&url.searchParams.get("dq")==="joao.silva"&&url.searchParams.get("dlevel")===employee.currentLevel);
  await page.reload();
  await page.locator('[aria-busy="true"]').waitFor({state:"detached"});
  assert.equal(await page.locator('.people-row.selected').count(),1,"direct person route restores directory selection");
  await page.setViewportSize({width:390,height:844});
  await page.locator('.people-back').click();
  await page.waitForURL(url=>url.pathname==="/manage/people"&&url.searchParams.get("q")==="joao.silva"&&url.searchParams.get("level")===employee.currentLevel);
  assert.equal(await page.locator('#people-directory-filter input[name="q"]').inputValue(),"joao.silva","mobile return keeps directory filters");
  await page.setViewportSize({width:1440,height:1000});
  await page.locator('#people-directory-filter input[name="q"]').fill("");
  await page.locator('#people-directory-filter input[name="level"]').fill("");
  assert.equal(await page.locator('#people-directory-filter input[name="q"]').inputValue(),"");
  await page.locator('#people-directory-filter button[type="submit"]').click();
  await page.waitForURL(url=>url.pathname==="/manage/people"&&!url.searchParams.has("q"));
  await capture(page,`/manage/people/${employee.id}/career-plan`,"14-manager-person-plan.png",true);
  for (const [width,height,name] of [[1600,1000,"38-people-1600.png"],[1280,900,"39-people-1280.png"],[768,1024,"40-people-tablet.png"],[390,844,"41-people-390.png"],[320,760,"42-people-320.png"]]) {
    await page.setViewportSize({width,height});
    await capture(page,`/manage/people/${employee.id}/career-plan`,name,true);
  }
  await page.setViewportSize({width:1440,height:1000});
  await capture(page,`/manage/people/${employee.id}/career-plan`,"14-manager-person-plan.png",true);
  for (const href of [`/manage/people/${employee.id}/career-plan`,`/manage/people/${employee.id}/evidence`,`/manage/people/${employee.id}/analyses`]) {
    assert.equal(await page.locator(`nav.tabs a[href="${href}"]`).count(),1,`person workspace tab ${href} is available from the career plan`);
  }
  await page.goto(`${appUrl}/manage/people/${employee.id}/career-plan?view=context`);
  await page.locator('[aria-busy="true"]').waitFor({state:"detached"});
  await page.locator('#plan-form textarea[name="characteristics"]').fill("Comunicação clara\nExecução consistente");
  const planSave=page.waitForResponse(response=>response.url().endsWith(`/manager/employees/${employee.id}/career-plan`)&&response.request().method()==="PUT");
  await page.getByRole("button",{name:"Salvar contexto"}).click();
  assert.equal((await planSave).status(),200);
  await page.reload();
  await page.locator('[aria-busy="true"]').waitFor({state:"detached"});
  assert.match(await page.locator('#plan-form textarea[name="characteristics"]').inputValue(),/Execução consistente/);
  await capture(page,`/manage/people/${employee.id}/evidence`,"15-manager-person-evidence.png",true);
  await openFilters(page,"person-evidence-filter");
  await addFilter(page,"person-evidence-filter","source");
  await page.locator('#person-evidence-filter input[name="source"]').fill("GitHub");
  await page.locator('#person-evidence-filter button[type="submit"]').click();
  await page.waitForURL(url=>url.pathname===`/manage/people/${employee.id}/evidence`&&url.searchParams.get("source")==="GitHub"&&url.searchParams.get("page")==="1");
  await capture(page,`/workspace/people/${employee.id}/evidence/${pending.id}`,"16-manager-evidence-detail.png",true);
  await capture(page,`/manage/people/${employee.id}/career-plan/objectives/new`,"17-manager-objective-editor.png",true);
  const objectiveText=page.locator('#objective-form textarea[name="text"]');
  await objectiveText.fill("Rascunho preservado durante a navegação");
  await page.getByRole("link",{name:"Cancelar"}).click();
  const draftDialog=page.locator("#action-dialog");
  await draftDialog.waitFor({state:"visible"});
  await draftDialog.getByRole("button",{name:"Continuar editando"}).click();
  assert.equal(await objectiveText.inputValue(),"Rascunho preservado durante a navegação");
  await page.getByRole("link",{name:"Cancelar"}).click();
  await draftDialog.waitFor({state:"visible"});
  await draftDialog.getByRole("button",{name:"Descartar e sair"}).click();
  await page.waitForURL(`**/manage/people/${employee.id}/career-plan`);
  await capture(page,`/manage/people/${employee.id}/career-plan/objectives/999999`,"25-manager-missing-objective.png",true);
  await page.getByText("Este objetivo não existe ou não está mais disponível.").waitFor();
  await page.goto(`${appUrl}/manage/people/${employee.id}/career-plan/objectives/new`);
  await page.locator('[aria-busy="true"]').waitFor({state:"detached"});
  await page.locator('#objective-form textarea[name="text"]').fill("Objetivo criado pelo teste de produção");
  const objectiveSave=page.waitForResponse(response=>response.url().endsWith(`/manager/employees/${employee.id}/career-plan/objectives`)&&response.request().method()==="POST");
  await page.getByRole("button",{name:"Salvar objetivo"}).click();
  assert.equal((await objectiveSave).status(),200);
  await page.waitForURL(`**/manage/people/${employee.id}/career-plan`);
  await page.getByText("Objetivo criado pelo teste de produção").waitFor();
  await capture(page,`/manage/people/${employee.id}/analyses`,"18-manager-person-analyses.png",true);
  await openFilters(page,"person-analyses-filter");
  await addFilter(page,"person-analyses-filter","review");
  await page.locator('#person-analyses-filter select[name="review"]').selectOption("needs-context");
  await page.locator('#person-analyses-filter button[type="submit"]').click();
  await page.waitForURL(url=>url.pathname===`/manage/people/${employee.id}/analyses`&&url.searchParams.get("review")==="needs-context"&&url.searchParams.get("page")==="1");
  await capture(page,`/workspace/people/${employee.id}/analyses/${analysis.analysisId}?view=review`,"19-manager-review.png",true);
  const reviewEndpoint=`**/manager/employees/${employee.id}/analyses/${analysis.analysisId}/reviews`;
  await page.route(reviewEndpoint,async route=>{if(route.request().method()==="POST")await new Promise(resolve=>setTimeout(resolve,300));await route.continue();});
  const needsContext=page.getByRole("button",{name:"Solicitar contexto"});
  await needsContext.click();
  await page.waitForTimeout(50);
  assert.equal(await page.locator("button[data-review]:disabled").count(),2,"both review decisions lock during a mutation");
  await page.waitForURL(url=>url.pathname===`/workspace/people/${employee.id}/analyses/${analysis.analysisId}`&&url.searchParams.get("view")==="history");
  await page.unroute(reviewEndpoint);
  await capture(page,"/manage/career/roles","20-manager-career-settings.png",true);
  await page.goto(`${appUrl}/manage/career/terminology`);
  await page.locator('[aria-busy="true"]').waitFor({state:"detached"});
  const terminologyInput=page.locator("#terminology-form input").first(),terminologyValue=await terminologyInput.inputValue();
  await terminologyInput.fill(`${terminologyValue} E2E`.slice(0,80));
  const terminologySave=page.waitForResponse(response=>response.url().endsWith("/manager/settings/terminology")&&response.request().method()==="PUT");
  await page.getByRole("button",{name:"Salvar terminologia"}).click();
  assert.equal((await terminologySave).status(),200);
  await page.reload();
  await page.locator('[aria-busy="true"]').waitFor({state:"detached"});
  assert.equal(await page.locator("#terminology-form input").first().inputValue(),`${terminologyValue} E2E`.slice(0,80));
  await page.goto(`${appUrl}/manage/career/roles/new`);
  await page.locator('[aria-busy="true"]').waitFor({state:"detached"});
  await page.locator('#role-form input[name="name"]').fill("Cargo de validação E2E");
  await page.locator('#role-form textarea[name="description"]').fill("Confirma persistência e limites do editor canônico.");
  await page.locator('#role-form input[name="allowedLevelIds"]').nth(0).check();
  await page.locator('#role-form input[name="allowedLevelIds"]').nth(1).check();
  const roleSave=page.waitForResponse(response=>response.url().endsWith("/manager/settings/job-roles")&&response.request().method()==="POST");
  await page.getByRole("button",{name:"Salvar cargo"}).click();
  assert.equal((await roleSave).status(),200);
  await page.waitForURL("**/manage/career/roles");
  await page.getByText("Cargo de validação E2E").waitFor();
  await page.setViewportSize({width:390,height:844});
  await capture(page,`/workspace/people/${employee.id}/analyses/${analysis.analysisId}?view=review`,"23-mobile-manager-review.png",true);
  await page.setViewportSize({width:1440,height:1000});

  const account=page.locator('button[data-action="account"]');
  await account.click();
  const accountDialog=page.locator("#action-dialog");
  await accountDialog.waitFor({state:"visible"});
  const firstDialogControl=accountDialog.getByRole("button",{name:"Fechar"}).first();
  const lastDialogControl=accountDialog.getByRole("button",{name:"Fechar"}).last();
  await firstDialogControl.focus();
  await page.keyboard.press("Shift+Tab");
  assert.equal(await lastDialogControl.evaluate(node=>node===document.activeElement),true,"native dialog contains focus");
  await lastDialogControl.click();
  await accountDialog.waitFor({state:"hidden"});
  assert.equal(await account.evaluate(node=>node===document.activeElement),true,"dialog close button returns focus to trigger");
  await account.click();
  await accountDialog.waitFor({state:"visible"});
  await page.keyboard.press("Escape");
  await accountDialog.waitFor({state:"hidden"});
  assert.equal(await account.evaluate(node=>node===document.activeElement),true,"dialog returns focus to trigger");

  await page.goto(`${appUrl}/manage/reviews?status=needs-context`);
  await page.getByRole("heading",{name:"Revisões",exact:true}).waitFor();
  await page.locator('[aria-busy="true"]').waitFor({state:"detached"});
  assert.match(await page.locator("body").innerText(),/Precisa de contexto|Precisam de contexto/);
  assert.doesNotMatch(await page.locator("body").innerText(),/undefined/);
  await page.goBack();
  await page.goForward();
  assert.equal(new URL(page.url()).pathname,"/manage/reviews");
  await page.reload();
  await page.getByRole("heading",{name:"Revisões",exact:true}).waitFor();
  await openFilters(page,"reviews-filter");
  await addFilter(page,"reviews-filter","employee");
  await page.locator('#reviews-filter input[name="employee"]').fill(String(employee.id));
  await page.locator('#reviews-filter button[type="submit"]').click();
  await page.waitForURL(url=>url.pathname==="/manage/reviews"&&url.searchParams.get("status")==="needs-context"&&url.searchParams.get("employee")===String(employee.id));
  await openFilters(page,"reviews-filter");
  await page.locator('#reviews-filter [data-action="clear-compact-filters"]').click();
  await page.waitForURL(url=>url.pathname==="/manage/reviews"&&url.searchParams.get("status")==="unreviewed"&&!url.searchParams.has("employee"));
  const defaultReviews=page.waitForResponse(response=>response.url().includes("/manager/reviews?")&&new URL(response.url()).searchParams.get("status")==="unreviewed");
  await page.goto(`${appUrl}/manage/reviews`);
  assert.equal((await defaultReviews).status(),200,"a direct reviews URL keeps the default status");

  await page.setViewportSize({width:320,height:740});
  await page.goto(`${appUrl}/manage/dashboard`);
  await page.locator('.dashboard-metrics article').first().waitFor();
  await assertDashboardPanelLayout(page);
  assert.equal(await horizontalOverflow(page),0,"dashboard fits 320px viewport");
  await page.screenshot({path:path.join(screenshotDir,"24-mobile-320-manager-dashboard.png"),fullPage:false});
  await page.goto(`${appUrl}/manage/career/roles`);
  await page.getByRole("heading",{name:"Cargos",exact:true}).waitFor();
  assert.equal(await horizontalOverflow(page),0);
  await resetScroll(page);
  await page.screenshot({path:path.join(screenshotDir,"24-mobile-320-manager-roles.png"),fullPage:false});
  const menu=page.getByRole("button",{name:/Menu/});
  await menu.focus(); await menu.press("Enter");
  await page.locator("#navigation.open").waitFor();
  assert.equal(await page.locator("#navigation").getAttribute("aria-modal"),"true");
  const firstDrawerLink=page.locator("#navigation a").first();
  const lastDrawerControl=page.locator("#navigation a, #navigation button").last();
  await firstDrawerLink.focus();
  await page.keyboard.press("Shift+Tab");
  assert.equal(await lastDrawerControl.evaluate((node)=>node===document.activeElement),true,"drawer focus wraps backwards");
  await page.keyboard.press("Escape");
  assert.equal(await menu.getAttribute("aria-expanded"),"false");
  assert.equal(await menu.evaluate((node)=>node===document.activeElement),true,"drawer returns focus to trigger");
  await menu.click();
  await page.locator("#navigation.open").waitFor();
  await page.locator("#navigation").getByRole("link",{name:"Pessoas",exact:true}).click();
  await page.waitForURL(url=>url.pathname==="/manage/people");
  await page.goto(`${appUrl}/workspace/people/${employee.id}/analyses/${analysis.analysisId}?view=source`);
  await page.getByRole("heading",{name:"Análise salva"}).waitFor();
  assert.equal(await horizontalOverflow(page),0);

  for (const viewport of [{width:768,height:1024},{width:1280,height:720},{width:844,height:390}]) {
    await page.setViewportSize(viewport);
    await page.goto(`${appUrl}/manage/reviews?status=needs-context`);
    await page.locator('[aria-busy="true"]').waitFor({state:"detached"});
    assert.equal(await horizontalOverflow(page),0,`overflow at ${viewport.width}x${viewport.height}`);
  }
  await page.setViewportSize({width:320,height:740});
  await page.addStyleTag({content:"html{font-size:200%}"});
  assert.equal(await horizontalOverflow(page),0,"overflow at 200% root text size");

  await page.goto(`${appUrl}/app/overview`);
  await page.getByRole("heading",{name:"Permissão necessária"}).waitFor();
  assert.equal(await page.locator("main main").count(),0,"forbidden state does not nest main landmarks");
  assert.equal(await page.getByRole("button",{name:/Menu/}).count(),1,"forbidden shell remains interactive");
  await page.goto(`${appUrl}/workspace/people/999999/analyses/999999?view=summary`);
  await page.getByText(/não existe|não está disponível/).waitFor();
  await page.goto(`${appUrl}/app/overview`);
  assert.equal(new URL(page.url()).pathname,"/app/overview");

  await page.evaluate(()=>localStorage.setItem("promova.auth-token","expired-token"));
  await page.goto(`${appUrl}/manage/people/${employee.id}/career-plan?view=context`);
  await page.waitForURL("**/login?returnTo=**");
  assert.equal(new URL(page.url()).searchParams.get("returnTo"),`/manage/people/${employee.id}/career-plan?view=context`,"concurrent 401 responses preserve one canonical destination");

  assert.deepEqual(pageErrors,[],"application raises no uncaught page errors");
  assert.deepEqual(consoleErrors.filter(message=>!message.includes("Failed to load resource")),[],"application logs no JavaScript console errors");
  const unexpectedHttp=httpErrors.filter(({status,url})=>!(status===401||status===404&&url.includes("/manager/employees/999999/analyses/999999")));
  assert.deepEqual(unexpectedHttp,[],`unexpected failed responses: ${JSON.stringify(unexpectedHttp)}`);

  const primaryWorkspace = routeTimings.find(({route})=>route==="/app/overview");
  assert.ok(primaryWorkspace?.durationMs<2_000,`cold primary workspace took ${primaryWorkspace?.durationMs}ms`);
  await writeFile(path.join(screenshotDir,"verification.json"),JSON.stringify({
    generatedAt:new Date().toISOString(),
    browser:"Google Chrome (Playwright)",
    productionBuild:true,
    roles:["EMPLOYEE","MANAGER"],
    screenshots:26,
    routeTimings,
    interactionTimings,
    viewports:["1440x1000","1280x720","768x1024","390x844","320x740","844x390","320 CSS px (1280px at 400% equivalent)"],
    checks:["deep-link fallback","missing asset 404","reload","back/forward","forbidden role","missing resource","missing objective","draft navigation guard","persisted plan/objective/terminology/role/GitHub mutations","review mutation lock","concurrent first review","manager filters","GitHub search pagination/import","concurrent session expiry","uncaught browser errors","unexpected HTTP errors","drawer focus trap/return","200% text","zero horizontal overflow"],
  },null,2)+"\n","utf8");
});

async function capture(page,route,name,authenticated=false){
  const started=performance.now();
  await page.goto(appUrl+route,{waitUntil:"networkidle"});
  if(authenticated) await page.locator("#route-title").waitFor();
  if(await page.locator('[aria-busy="true"]').count()) await page.locator('[aria-busy="true"]').waitFor({state:"detached"});
  assert.equal(await horizontalOverflow(page),0,`horizontal overflow at ${route}`);
  const durationMs=Math.round(performance.now()-started);
  const browserMetrics=await page.evaluate(()=>{const nav=performance.getEntriesByType("navigation").at(-1);const api=performance.getEntriesByType("resource").filter(entry=>entry.name.includes(":18082/")).map(entry=>Math.round(entry.duration));return {domContentLoadedMs:Math.round(nav?.domContentLoadedEventEnd||0),apiMaxMs:api.length?Math.max(...api):0,apiRequests:api.length,viewport:`${innerWidth}x${innerHeight}`};});
  routeTimings.push({route,durationMs,...browserMetrics});
  assert.ok(durationMs<10_000,`route ${route} exceeded 10s verification ceiling`);
  await resetScroll(page);
  await page.screenshot({path:path.join(screenshotDir,name),fullPage:false});
}
async function loginUi(page,email,password,returnTo){await page.goto(`${appUrl}/login?returnTo=${encodeURIComponent(returnTo)}`);await page.locator('input[name="email"]').fill(email);await page.locator('input[name="password"]').fill(password);await page.getByRole("button",{name:"Entrar"}).click();try{await page.waitForURL(`**${returnTo}`,{timeout:10_000});}catch(error){const details=await page.evaluate(()=>({url:location.href,body:document.body.innerText.slice(0,2000),token:Boolean(localStorage.getItem("promova.auth-token")),user:localStorage.getItem("promova.auth-user")}));throw new Error(`UI login failed: ${JSON.stringify(details)}`,{cause:error});}await page.locator("#route-title").waitFor();}
async function horizontalOverflow(page){return page.evaluate(()=>Math.max(0,document.documentElement.scrollWidth-document.documentElement.clientWidth));}
async function assertDashboardPanelLayout(page){
  const panels=await page.locator('.manager-dashboard > .panel').evaluateAll(elements=>elements.map(element=>{
    const rect=element.getBoundingClientRect();
    return {top:rect.top,bottom:rect.bottom,height:rect.height,footerPosition:element.querySelector('footer')?getComputedStyle(element.querySelector('footer')).position:null};
  }));
  assert.equal(panels.length,2,"dashboard renders PR and people panels");
  assert.ok(panels.every(panel=>panel.height>100),"dashboard panels reserve space for their content");
  assert.ok(panels[1].top>=panels[0].bottom,"people panel does not overlap the PR panel");
  assert.equal(panels[1].footerPosition,"static","people pagination stays inside its panel");
}
async function resetScroll(page){await page.evaluate(()=>{window.scrollTo(0,0);document.querySelector(".workspace")?.scrollTo(0,0);});}
async function loginApi(email,password){const deadline=Date.now()+15_000;let error;while(Date.now()<deadline){try{return (await api("/auth/login",null,"POST",{email,password})).token;}catch(caught){error=caught;await new Promise(r=>setTimeout(r,300));}}throw error;}
async function api(pathname,token,method="GET",body){const response=await fetch(apiUrl+pathname,{method,headers:{...(token?{Authorization:`Bearer ${token}`}:{ }),...(body?{"Content-Type":"application/json"}:{})},body:body?JSON.stringify(body):undefined});if(!response.ok)throw new Error(`${method} ${pathname} returned ${response.status}: ${await response.text()}`);return response.status===204?null:response.json();}
function startProcess(command,args,options){const child=spawn(command,args,{...options,detached:process.platform!=="win32",stdio:["ignore","pipe","pipe"]});child.output="";const add=chunk=>child.output=(child.output+chunk).slice(-16000);child.stdout.on("data",add);child.stderr.on("data",add);return child;}
async function stopProcess(child){if(!child||child.exitCode!==null)return;try{process.kill(process.platform==="win32"?child.pid:-child.pid,"SIGTERM");}catch{return;}await Promise.race([new Promise(r=>child.once("exit",r)),new Promise(r=>setTimeout(r,8000))]);if(child.exitCode===null)try{process.kill(-child.pid,"SIGKILL");}catch{}}
async function waitFor(url,statuses,timeout){const end=Date.now()+timeout;let error;while(Date.now()<end){try{const response=await fetch(url);if(statuses.includes(response.status))return;error=new Error(`status ${response.status}`);}catch(e){error=e;}await new Promise(r=>setTimeout(r,250));}throw new Error(`Timed out waiting for ${url}: ${error?.message}`);}
async function openFilters(page,id){await page.locator(`#${id}`).locator('xpath=..').locator('[data-action="toggle-compact-filters"]').click();await page.locator(`#${id}`).waitFor({state:"visible"});}
async function addFilter(page,id,name){await page.locator(`#${id} [data-action="add-compact-filter"][data-filter-field="${name}"]`).click();}
async function openInboxFilters(page){await page.locator("#inbox-filter").waitFor({state:"visible"});}
async function addInboxFilter(page,name){await page.locator(`#inbox-filter [name="${name}"]`).waitFor({state:"visible"});}
async function seedEvidences(token,count){for(let start=0;start<count;start+=25){await Promise.all(Array.from({length:Math.min(25,count-start)},(_,offset)=>api("/evidences/github/pull-request",token,"POST",{repo:"acme/project",pullNumber:1000+start+offset,usernameHint:"joao"})));}}
async function startGithubStub(){return new Promise(resolve=>{const server=http.createServer((req,res)=>{const number=Number(req.url.match(/pulls\/(\d+)/)?.[1]||7);const now=new Date().toISOString();const payload={number,title:number===7?"Trusted server-owned analysis and resilient persistence":number===12?"<img src=x onerror=alert(1)> Verify escape":"Improve team delivery with documented recovery metrics",state:"closed",merged_at:now,closed_at:now,html_url:`https://github.com/acme/project/pull/${number}`,user:{login:"joao"},labels:number===8?[{name:"backend"},{name:"integração"}]:[],updated_at:now,created_at:now,body:"Refactor improve tests ownership leadership. "+"Long evidence context. ".repeat(480)+"\n"+"x".repeat(1200)};const response=req.url.startsWith("/search/issues")?{total_count:10,incomplete_results:false,items:[payload]}:payload;res.writeHead(200,{"Content-Type":"application/json"});res.end(JSON.stringify(response));});server.listen(0,"127.0.0.1",()=>resolve(server));});}
