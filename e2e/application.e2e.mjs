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
const screenshotDir = path.join(root, "artifacts/frontend-modernization-2026-09-07/actual");
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
  await seedEvidences(employeeToken,500);
  const boundedPage=await api("/evidences?status=PENDING&page=1&pageSize=25",employeeToken);
  assert.ok(boundedPage.total>=500,"500-record fixture is visible through bounded pagination");
  assert.equal(boundedPage.items.length,25,"the browser never receives the full stress fixture");
  const people = await api("/manager/employees?page=1&pageSize=25",managerToken);
  const employee = people.items.find(person=>person.email==="joao.silva@empresa.com");
  assert.equal(employee.id,me.id);
  await api(`/manager/employees/${employee.id}/analyses/${analysis.analysisId}/reviews`,managerToken,"POST",{status:"NEEDS_CONTEXT",comment:"Inclua as métricas do período seguinte na próxima conversa.",idempotencyKey:"e2e-review"});

  await mkdir(screenshotDir,{recursive:true});
  browser = await chromium.launch({executablePath:chrome,headless:true});
  const context = await browser.newContext({viewport:{width:1440,height:1000},deviceScaleFactor:1});
  const page = await context.newPage();
  await capture(page,"/","01-public-landing.png");
  await capture(page,"/login","02-auth-login.png");
  await capture(page,"/register","03-auth-register.png");
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
  await page.locator('#inbox-filter input[name="source"]').fill("GitHub");
  await page.getByRole("button",{name:"Aplicar filtros"}).click();
  await page.waitForURL(url=>url.pathname==="/app/inbox"&&url.searchParams.get("status")==="pending"&&url.searchParams.get("source")==="GitHub"&&url.searchParams.get("page")==="1");
  await capture(page,`/workspace/people/${employee.id}/analyses/${analysis.analysisId}?view=summary`,"05-employee-analysis-summary.png",true);
  await capture(page,`/workspace/people/${employee.id}/analyses/${analysis.analysisId}?view=history`,"06-employee-review-history.png",true);
  await capture(page,"/app/framework","07-employee-framework.png",true);
  const criterionHref = await page.locator('.row-list a[href*="/criteria/"]').first().getAttribute("href");
  if (criterionHref) await capture(page,criterionHref,"08-employee-criterion.png",true);
  await capture(page,"/app/integrations/github","09-employee-github.png",true);
  await capture(page,"/app/career-plan?view=context","10-employee-career-context.png",true);
  await capture(page,"/app/inbox?status=pending","11-employee-inbox.png",true);
  await capture(page,`/workspace/people/${employee.id}/evidence/${pending.id}`,"12-employee-evidence.png",true);
  await capture(page,"/app/analyses","13-employee-analyses.png",true);
  await page.setViewportSize({width:390,height:844});
  await capture(page,"/app/inbox?status=pending","21-mobile-employee-inbox.png",true);
  await capture(page,`/workspace/people/${employee.id}/evidence/${pending.id}`,"22-mobile-employee-evidence.png",true);
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
  await loginUi(page,"manager@promova.com","manager123","/manage/people");
  await page.locator('#people-filter input[name="q"]').fill("joao.silva");
  await page.locator('#people-filter input[name="level"]').fill(employee.currentLevel);
  await page.getByRole("button",{name:"Aplicar filtros"}).click();
  await page.waitForURL(url=>url.pathname==="/manage/people"&&url.searchParams.get("q")==="joao.silva"&&url.searchParams.get("level")===employee.currentLevel&&url.searchParams.get("page")==="1");
  await capture(page,`/manage/people/${employee.id}/career-plan`,"14-manager-person-plan.png",true);
  for (const href of [`/manage/people/${employee.id}/career-plan`,`/manage/people/${employee.id}/evidence`,`/manage/people/${employee.id}/analyses`]) {
    assert.equal(await page.locator(`nav.tabs a[href="${href}"]`).count(),1,`person workspace tab ${href} is available from the career plan`);
  }
  await capture(page,`/manage/people/${employee.id}/evidence`,"15-manager-person-evidence.png",true);
  await capture(page,`/workspace/people/${employee.id}/evidence/${pending.id}`,"16-manager-evidence-detail.png",true);
  await capture(page,`/manage/people/${employee.id}/career-plan/objectives/new`,"17-manager-objective-editor.png",true);
  await capture(page,`/manage/people/${employee.id}/analyses`,"18-manager-person-analyses.png",true);
  await capture(page,`/workspace/people/${employee.id}/analyses/${analysis.analysisId}?view=review`,"19-manager-review.png",true);
  await capture(page,"/manage/career/roles","20-manager-career-settings.png",true);
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
  await page.goBack();
  await page.goForward();
  assert.equal(new URL(page.url()).pathname,"/manage/reviews");
  await page.reload();
  await page.getByRole("heading",{name:"Revisões",exact:true}).waitFor();

  await page.setViewportSize({width:320,height:740});
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
  await page.goto(`${appUrl}/workspace/people/999999/analyses/999999?view=summary`);
  await page.getByText(/não existe|não está disponível/).waitFor();
  await page.goto(`${appUrl}/app/overview`);
  assert.equal(new URL(page.url()).pathname,"/app/overview");

  await page.evaluate(()=>localStorage.setItem("promova.auth-token","expired-token"));
  await page.goto(`${appUrl}/manage/reviews?status=unreviewed`);
  await page.waitForURL("**/login?returnTo=**");
  assert.match(new URL(page.url()).searchParams.get("returnTo")||"",/^\/manage\/reviews/);

  const primaryWorkspace = routeTimings.find(({route})=>route==="/app/overview");
  assert.ok(primaryWorkspace?.durationMs<2_000,`cold primary workspace took ${primaryWorkspace?.durationMs}ms`);
  await writeFile(path.join(screenshotDir,"verification.json"),JSON.stringify({
    generatedAt:new Date().toISOString(),
    browser:"Google Chrome (Playwright)",
    productionBuild:true,
    roles:["EMPLOYEE","MANAGER"],
    screenshots:24,
    routeTimings,
    interactionTimings,
    viewports:["1440x1000","1280x720","768x1024","390x844","320x740","844x390","320 CSS px (1280px at 400% equivalent)"],
    checks:["deep-link fallback","missing asset 404","reload","back/forward","forbidden role","missing resource","drawer focus trap/return","200% text","zero horizontal overflow"],
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
async function resetScroll(page){await page.evaluate(()=>{window.scrollTo(0,0);document.querySelector(".workspace")?.scrollTo(0,0);});}
async function loginApi(email,password){const deadline=Date.now()+15_000;let error;while(Date.now()<deadline){try{return (await api("/auth/login",null,"POST",{email,password})).token;}catch(caught){error=caught;await new Promise(r=>setTimeout(r,300));}}throw error;}
async function api(pathname,token,method="GET",body){const response=await fetch(apiUrl+pathname,{method,headers:{...(token?{Authorization:`Bearer ${token}`}:{ }),...(body?{"Content-Type":"application/json"}:{})},body:body?JSON.stringify(body):undefined});if(!response.ok)throw new Error(`${method} ${pathname} returned ${response.status}: ${await response.text()}`);return response.status===204?null:response.json();}
function startProcess(command,args,options){const child=spawn(command,args,{...options,detached:process.platform!=="win32",stdio:["ignore","pipe","pipe"]});child.output="";const add=chunk=>child.output=(child.output+chunk).slice(-16000);child.stdout.on("data",add);child.stderr.on("data",add);return child;}
async function stopProcess(child){if(!child||child.exitCode!==null)return;try{process.kill(process.platform==="win32"?child.pid:-child.pid,"SIGTERM");}catch{return;}await Promise.race([new Promise(r=>child.once("exit",r)),new Promise(r=>setTimeout(r,8000))]);if(child.exitCode===null)try{process.kill(-child.pid,"SIGKILL");}catch{}}
async function waitFor(url,statuses,timeout){const end=Date.now()+timeout;let error;while(Date.now()<end){try{const response=await fetch(url);if(statuses.includes(response.status))return;error=new Error(`status ${response.status}`);}catch(e){error=e;}await new Promise(r=>setTimeout(r,250));}throw new Error(`Timed out waiting for ${url}: ${error?.message}`);}
async function seedEvidences(token,count){for(let start=0;start<count;start+=25){await Promise.all(Array.from({length:Math.min(25,count-start)},(_,offset)=>api("/evidences/github/pull-request",token,"POST",{repo:"acme/project",pullNumber:1000+start+offset,usernameHint:"joao"})));}}
async function startGithubStub(){return new Promise(resolve=>{const server=http.createServer((req,res)=>{const number=Number(req.url.match(/pulls\/(\d+)/)?.[1]||7);const now=new Date().toISOString();const payload={number,title:number===7?"Trusted server-owned analysis and resilient persistence":"Improve team delivery with documented recovery metrics",state:"closed",merged_at:now,closed_at:now,html_url:`https://github.com/acme/project/pull/${number}`,user:{login:"joao"},updated_at:now,created_at:now,body:"Refactor improve tests ownership leadership. "+"Long evidence context. ".repeat(480)+"\n"+"x".repeat(1200)};res.writeHead(200,{"Content-Type":"application/json"});res.end(JSON.stringify(payload));});server.listen(0,"127.0.0.1",()=>resolve(server));});}
