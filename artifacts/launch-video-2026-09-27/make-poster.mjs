import { chromium } from 'playwright-core';
import { writeFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
const directory=path.dirname(fileURLToPath(import.meta.url));
const browser=await chromium.launch({executablePath:'/Applications/Google Chrome.app/Contents/MacOS/Google Chrome',headless:true});
try{
  const page=await browser.newPage({viewport:{width:1600,height:900},deviceScaleFactor:1});
  await page.goto('http://127.0.0.1:14177/artifacts/launch-video-2026-09-27/');
  await page.waitForFunction(()=>document.querySelector('#launch').readyState>=1);
  for(const [second,file] of [[2,'assets/poster.png'],[13,'/tmp/promova-launch-frame-13.png'],[29,'/tmp/promova-launch-frame-29.png']]){
    const base64=await page.evaluate(async second=>{
      const v=document.querySelector('#launch');
      v.pause();
      await new Promise(resolve=>{v.addEventListener('seeked',resolve,{once:true});v.currentTime=second});
      if(v.readyState<2)await new Promise(resolve=>v.addEventListener('loadeddata',resolve,{once:true}));
      const canvas=document.createElement('canvas');canvas.width=v.videoWidth;canvas.height=v.videoHeight;
      canvas.getContext('2d').drawImage(v,0,0);
      return canvas.toDataURL('image/png').split(',')[1];
    },second);
    const output=path.isAbsolute(file)?file:path.join(directory,file);
    await writeFile(output,Buffer.from(base64,'base64'));
    console.log(`Frame ${second}s: ${output}`);
  }
}finally{await browser.close()}
