import { chromium } from 'playwright-core';
import { mkdir, writeFile } from 'node:fs/promises';
import { fileURLToPath, pathToFileURL } from 'node:url';
import path from 'node:path';
const dir=path.dirname(fileURLToPath(import.meta.url));
await mkdir(path.join(dir,'previews'),{recursive:true});
const browser=await chromium.launch({executablePath:'/Applications/Google Chrome.app/Contents/MacOS/Google Chrome',headless:true});
const page=await browser.newPage({viewport:{width:1440,height:1000}});
const errors=[];page.on('pageerror',e=>errors.push(e.message));
const base=pathToFileURL(path.join(dir,'wireframes.html')).href;
await page.goto(base);
const scenes=await page.locator('#scenario option').evaluateAll(xs=>xs.map(x=>x.value));
const results=[];
const captures=new Set(['overview','inbox','evidence','analysis','history','plan','people','review','roles','loading','empty','error','managerframework']);
for(const width of [1440,390,320]){
 await page.setViewportSize({width,height:width===1440?1000:844});
 for(const scene of scenes){
  await page.goto(base+'#'+scene);
  await page.waitForTimeout(30);
  await page.evaluate(()=>document.activeElement?.blur());
  const state=await page.evaluate(()=>({bodyWidth:document.body.scrollWidth,innerWidth,bodyHeight:document.body.scrollHeight,innerHeight,h1:document.querySelector('h1')?.textContent,dialog:!!document.querySelector('dialog[open]')}));
  results.push({scene,width,...state});
  if((width===1440&&captures.has(scene))||(width===390&&['inbox','mobile','evidence','review'].includes(scene)))await page.screenshot({path:path.join(dir,'previews',`${width}-${scene}.png`),fullPage:false});
 }
}
// Local concept behavior checks, without touching the product.
await page.setViewportSize({width:390,height:844});
await page.goto(base+'#inbox');
await page.locator('#menu-trigger').click();
if(!await page.locator('#navigation').isVisible())throw Error('drawer failed');
await page.keyboard.press('Escape');
if(await page.locator('#navigation').isVisible())throw Error('drawer close failed');
await page.goto(base+'#error');await page.getByRole('link',{name:'Tentar novamente'}).click();
if(!page.url().endsWith('#inbox'))throw Error('retry link failed');
await page.goto(base+'#evidence');await page.locator('#observation').fill('Contexto ilustrativo');
if(await page.locator('#observation-count').textContent()!=='20/2000')throw Error('observation count');
await page.goto(base+'#review');await page.locator('#review-comment').fill('Conferido');await page.getByRole('button',{name:'Aceitar análise',exact:true}).click();
if(!(await page.locator('#review-history').textContent()).includes('Aceita'))throw Error('review simulation failed');
await page.goto(base+'#overview');await page.getByRole('link',{name:'Abrir caixa de entrada'}).click();await page.goBack();if(!page.url().endsWith('#overview'))throw Error('history failed');
// Stress the declared list/body boundaries and keep the task action reachable.
await page.setViewportSize({width:1440,height:1000});await page.goto(base+'#inbox');
const stress=await page.evaluate(()=>{const list=document.getElementById('inbox-rows');const row=list.firstElementChild.outerHTML;list.innerHTML=Array(500).fill(row).join('');list.scrollTop=list.scrollHeight;const action=document.querySelector('.preview-pane .primary').getBoundingClientRect();return {rows:list.children.length,listScrolls:list.scrollHeight>list.clientHeight,bodyDoesNotGrow:document.body.scrollHeight<=innerHeight,actionVisible:action.bottom<=innerHeight};});
if(!stress.listScrolls||!stress.bodyDoesNotGrow||!stress.actionVisible)throw Error('long list bounds failed');
await page.goto(base+'#evidence');
const longText=await page.evaluate(()=>{const source=document.querySelector('.source');source.textContent=('Long evidence / '+ 'a'.repeat(1000)+'\n').repeat(30);const action=document.querySelector('[data-analyze]').getBoundingClientRect();return {noHorizontalOverflow:document.body.scrollWidth<=innerWidth,bodyDoesNotGrow:document.body.scrollHeight<=innerHeight,actionVisible:action.bottom<=innerHeight};});
if(!longText.noHorizontalOverflow||!longText.bodyDoesNotGrow||!longText.actionVisible)throw Error('long evidence bounds failed');
await page.setViewportSize({width:390,height:844});await page.goto(base+'#review');
const mobileDecision=await page.getByRole('button',{name:'Aceitar análise',exact:true}).boundingBox();
if(!mobileDecision||mobileDecision.y+mobileDecision.height>844)throw Error('mobile decision visibility');
await page.locator('#review-facet').click();await page.locator('#review-facet').click();
await writeFile(path.join(dir,'verification.json'),JSON.stringify({testedAt:new Date().toISOString(),errors,results,stress,longText,mobileDecision,interactionChecks:8},null,2));
await browser.close();
console.log(JSON.stringify({scenes:scenes.length,viewportChecks:results.length,errors,horizontalOverflow:results.filter(x=>x.bodyWidth>x.innerWidth),desktopBodyGrowth:results.filter(x=>x.width===1440&&x.bodyHeight>x.innerHeight),interactionChecks:8,stress,longText},null,2));
