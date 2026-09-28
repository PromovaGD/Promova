import { chromium } from 'playwright-core';
import { writeFile } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const dir=path.dirname(fileURLToPath(import.meta.url));
const browser=await chromium.launch({executablePath:'/Applications/Google Chrome.app/Contents/MacOS/Google Chrome',headless:true});
try{
  const page=await browser.newPage({viewport:{width:1600,height:900},deviceScaleFactor:1});
  page.setDefaultTimeout(0);
  page.on('pageerror',error=>console.error(`Browser: ${error.message}`));
  page.on('console',message=>{if(message.type()==='log')console.log(message.text())});
  await page.goto('http://127.0.0.1:14177/artifacts/promova-release-motion-2026-09-28/composition.html');
  const result=await page.evaluate(()=>window.renderMp4());
  if(!result.bytes)throw Error('Empty MP4 render');
  const output=path.join(dir,'promova-release-motion.mp4');
  await writeFile(output,Buffer.from(result.base64,'base64'));
  console.log(`${output}: ${result.bytes} bytes`);
}finally{await browser.close()}
