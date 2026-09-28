import { chromium } from 'playwright-core';
import { writeFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import path from 'node:path';

const directory = path.dirname(fileURLToPath(import.meta.url));
const appUrl = 'http://127.0.0.1:14176';
const browser = await chromium.launch({
  executablePath: '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome',
  headless: true,
  args: ['--disable-background-timer-throttling', '--disable-renderer-backgrounding', '--disable-backgrounding-occluded-windows'],
});
const sleep = ms => new Promise(resolve => setTimeout(resolve, ms));
let appPage, client, receiving = false, pointer = { x: 1020, y: 550 };

async function login(email, password) {
  const context = await browser.newContext({ viewport: { width: 1280, height: 720 }, deviceScaleFactor: 1 });
  const page = await context.newPage();
  await page.goto(`${appUrl}/login`);
  await page.locator('input[name="email"]').fill(email);
  await page.locator('input[name="password"]').fill(password);
  await page.getByRole('button', { name: 'Entrar' }).click();
  await page.waitForURL(url => url.pathname === (email.startsWith('manager') ? '/manage/dashboard' : '/app/overview'));
  return page;
}
async function settle() {
  await appPage.locator('[aria-busy="true"]').waitFor({ state: 'detached', timeout: 5000 }).catch(() => {});
  await sleep(220);
}

const recorderPage = await browser.newPage({ viewport: { width: 1600, height: 900 } });
await recorderPage.setContent(`<!doctype html><canvas id="film" width="1600" height="900"></canvas><script>
const canvas=document.querySelector('#film'),ctx=canvas.getContext('2d');
let image=null,pointer={x:1020,y:550},ring=0,recorder=null,chunks=[],stream=null;
function draw(){
  ctx.fillStyle='#0d1036';ctx.fillRect(0,0,1600,900);
  if(image)ctx.drawImage(image,0,0,1600,900);
  if(ring){let d=(performance.now()-ring)/500;if(d<1){ctx.save();ctx.globalAlpha=1-d;ctx.strokeStyle='#754efa';ctx.lineWidth=5;ctx.beginPath();ctx.arc(pointer.x*1.25,pointer.y*1.25,20+d*55,0,7);ctx.stroke();ctx.restore();requestAnimationFrame(draw)}else ring=0}
  ctx.save();ctx.translate(pointer.x*1.25,pointer.y*1.25);ctx.shadowColor='#0009';ctx.shadowBlur=11;ctx.shadowOffsetY=4;
  ctx.beginPath();ctx.moveTo(0,0);ctx.lineTo(0,29);ctx.lineTo(8,21);ctx.lineTo(16,36);ctx.lineTo(23,32);ctx.lineTo(15,18);ctx.lineTo(28,18);ctx.closePath();
  ctx.fillStyle='#fff';ctx.strokeStyle='#301b7c';ctx.lineWidth=3;ctx.fill();ctx.stroke();ctx.restore();
}
window.setFrame=async base64=>{const next=new Image();next.src='data:image/jpeg;base64,'+base64;await next.decode();image=next;draw()};
window.setPointer=point=>{pointer=point;draw()};
window.clickPulse=()=>{ring=performance.now();draw()};
window.startRecording=()=>{stream=canvas.captureStream(30);const mime=['video/mp4;codecs=avc1.42E01E','video/mp4','video/webm;codecs=vp9'].find(x=>MediaRecorder.isTypeSupported(x));recorder=new MediaRecorder(stream,{mimeType:mime,videoBitsPerSecond:9000000});recorder.ondataavailable=e=>{if(e.data.size)chunks.push(e.data)};recorder.start();return mime};
window.stopRecording=async()=>{const done=new Promise(resolve=>recorder.onstop=resolve);recorder.stop();await done;stream.getTracks().forEach(t=>t.stop());const blob=new Blob(chunks,{type:recorder.mimeType});const bytes=new Uint8Array(await blob.arrayBuffer());const parts=[];for(let i=0;i<bytes.length;i+=32768)parts.push(String.fromCharCode(...bytes.subarray(i,i+32768)));return{mime:recorder.mimeType,size:blob.size,base64:btoa(parts.join(''))}};
</script>`);

async function attach(page) {
  appPage = page;
  client = await page.context().newCDPSession(page);
  client.on('Page.screencastFrame', event => {
    client.send('Page.screencastFrameAck', { sessionId: event.sessionId }).catch(() => {});
    if (receiving) return;
    receiving = true;
    recorderPage.evaluate(base64 => window.setFrame(base64), event.data).catch(() => {}).finally(() => { receiving = false; });
  });
  const first = await page.screenshot({ type: 'jpeg', quality: 86 });
  await recorderPage.evaluate(base64 => window.setFrame(base64), first.toString('base64'));
  await client.send('Page.startScreencast', { format: 'jpeg', quality: 86, maxWidth: 1280, maxHeight: 720, everyNthFrame: 1 });
}
async function detach() {
  await client.send('Page.stopScreencast');
  await client.detach();
  while (receiving) await sleep(30);
}
async function move(locator, duration = 480) {
  await locator.scrollIntoViewIfNeeded();
  const box = await locator.boundingBox();
  if (!box) throw new Error(`Element is not visible: ${locator}`);
  const target = { x: box.x + box.width / 2, y: box.y + box.height / 2 };
  const steps = Math.max(8, Math.round(duration / 25));
  const from = pointer;
  for (let index = 1; index <= steps; index++) {
    const p = index / steps, eased = p * p * (3 - 2 * p);
    const next = { x: from.x + (target.x - from.x) * eased, y: from.y + (target.y - from.y) * eased };
    await appPage.mouse.move(next.x, next.y);
    await recorderPage.evaluate(point => window.setPointer(point), next);
    await sleep(duration / steps);
  }
  pointer = target;
}
async function click(locator, wait, pause = 400) {
  await move(locator);
  await recorderPage.evaluate(() => window.clickPulse());
  await locator.click();
  if (wait) await wait();
  await settle();
  await sleep(pause);
}

try {
  const employee = await login('joao.silva@empresa.com', 'senha123');
  const manager = await login('manager@promova.com', 'manager123');
  appPage = employee;
  await attach(employee);
  const mime = await recorderPage.evaluate(() => window.startRecording());
  await sleep(650);
  await click(appPage.getByRole('link', { name: 'Abrir caixa de entrada' }), () => appPage.waitForURL('**/app/inbox?status=pending'), 400);
  await click(appPage.locator('.inbox-item').first(), null, 220);
  await click(appPage.getByRole('link', { name: 'Abrir evidência' }), () => appPage.waitForURL(url => url.pathname.includes('/evidence/')), 500);
  await move(appPage.locator('#observation'));
  await appPage.locator('#observation').click();
  await appPage.locator('#observation').pressSequentially('Documentei o fluxo de integração e os pontos de decisão da equipe.', { delay: 38 });
  await sleep(320);
  await appPage.route('**/evidences/*/analysis', async route => {
    if (route.request().method() === 'POST') await sleep(1000);
    await route.continue();
  });
  await click(appPage.getByRole('button', { name: 'Analisar evidência' }), () => appPage.waitForURL(url => url.pathname.includes('/analyses/')), 750);
  await click(appPage.getByRole('link', { name: 'Fonte', exact: true }), null, 500);
  await click(appPage.getByRole('link', { name: 'Resumo', exact: true }), null, 650);

  await detach();
  appPage = manager;
  await manager.goto(`${appUrl}/manage/dashboard`);
  await settle();
  pointer = { x: 1140, y: 110 };
  await recorderPage.evaluate(point => window.setPointer(point), pointer);
  await attach(manager);
  await sleep(800);
  await click(appPage.getByRole('link', { name: 'Revisões', exact: true }), () => appPage.waitForURL(url => url.pathname === '/manage/reviews'), 450);
  const reviewRow = appPage.locator('.row-list a.row').filter({ hasText: 'João Silva' }).filter({ hasText: 'PR #503' }).first();
  await click(reviewRow, () => appPage.waitForURL(url => url.pathname.includes('/analyses/')), 500);
  await move(appPage.locator('#review-form textarea[name="comment"]'));
  await appPage.locator('#review-form textarea[name="comment"]').click();
  await appPage.locator('#review-form textarea[name="comment"]').pressSequentially('Evidência clara para a próxima conversa de carreira.', { delay: 40 });
  await sleep(260);
  await click(appPage.getByRole('button', { name: 'Aceitar análise' }), null, 700);
  await click(appPage.getByRole('link', { name: 'Pessoas', exact: true }), () => appPage.waitForURL(url => url.pathname === '/manage/people'), 350);
  await click(appPage.locator('.row-list a.row').filter({ hasText: 'João Silva' }).first(), () => appPage.waitForURL(url => url.pathname.includes('/career-plan')), 500);
  await click(appPage.getByRole('link', { name: /Ampliar impacto técnico/ }), () => appPage.waitForURL(url => url.pathname.includes('/objectives/')), 350);
  await move(appPage.locator('#objective-form select[name="status"]'), 350);
  await recorderPage.evaluate(() => window.clickPulse());
  await appPage.locator('#objective-form select[name="status"]').selectOption('COMPLETED');
  await sleep(400);
  await click(appPage.getByRole('button', { name: 'Salvar objetivo' }), () => appPage.waitForURL(url => url.pathname.endsWith('/career-plan')), 1000);
  await detach();
  const result = await recorderPage.evaluate(() => window.stopRecording());
  if (!result.size) throw new Error('Recording is empty.');
  const output = path.join(directory, `promova-demo-produto-30s.${mime.includes('mp4') ? 'mp4' : 'webm'}`);
  await writeFile(output, Buffer.from(result.base64, 'base64'));
  console.log(`${output} (${result.size} bytes)`);
} finally {
  await browser.close();
}
