import { chromium } from 'playwright-core';
import { writeFile } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const folder=path.dirname(fileURLToPath(import.meta.url));
const fps=30,duration=21.4,count=Math.round(fps*duration);
const browser=await chromium.launch({executablePath:'/Applications/Google Chrome.app/Contents/MacOS/Google Chrome',headless:true});
try{
  const page=await browser.newPage({viewport:{width:1600,height:900},deviceScaleFactor:1});
  page.setDefaultTimeout(0);
  page.on('pageerror',error=>console.error(`Browser: ${error.message}`));
  await page.goto('http://127.0.0.1:14177/artifacts/promova-motion-rebuild-2026-09-28/dist/promova-release.html?render');
  await page.evaluate(()=>document.fonts.ready);
  await page.evaluate(async()=>{
    const media=await import('/node_modules/mediabunny/dist/bundles/mediabunny.min.mjs');
    const canvas=document.createElement('canvas');canvas.width=1600;canvas.height=900;
    const context=canvas.getContext('2d',{alpha:false});
    const output=new media.Output({format:new media.Mp4OutputFormat({fastStart:'in-memory'}),target:new media.BufferTarget()});
    const video=new media.CanvasSource(canvas,{codec:'avc',quality:new media.Quality('high'),keyFrameInterval:2});
    const audio=new media.AudioBufferSource({codec:'aac',quality:new media.Quality('high')});
    output.addVideoTrack(video);output.addAudioTrack(audio);await output.start();
    const offline=new OfflineAudioContext(2,Math.ceil(21.48*48000),48000);
    const beat=[0.08,2.7,5.47,6.4,9.15,10,12.42,13.6,16.08,17.1];
    const tone=(at,hz,length,volume,type='sine',pan=0)=>{
      const osc=offline.createOscillator(),gain=offline.createGain(),stereo=offline.createStereoPanner();
      osc.type=type;osc.frequency.value=hz;stereo.pan.value=pan;
      gain.gain.setValueAtTime(.0001,at);gain.gain.exponentialRampToValueAtTime(volume,at+.018);
      gain.gain.exponentialRampToValueAtTime(.0001,at+length);
      osc.connect(gain).connect(stereo).connect(offline.destination);osc.start(at);osc.stop(at+length+.02);
    };
    const roots=[146.83,174.61,130.81,196,146.83,174.61];
    for(let bar=0;bar<6;bar++){
      const tm=bar*3.55,root=roots[bar];
      tone(tm+.02,root/2,3.4,.09,'sine',-.15);
      tone(tm+.04,root*1.5,2.85,.028,'triangle',.18);
      for(let n=0;n<7;n++)tone(tm+.22+n*.46,root*[1,1.5,2,1.5,2.5,2,1.5][n],.28,.018,'sine',n%2?-.22:.22);
    }
    for(const tm of beat){tone(tm,82,.42,.095,'sine');tone(tm+.035,475,.18,.014,'triangle',.45)}
    const sound=await offline.startRendering();
    const audioTask=audio.add(sound);
    window.__encode={context,output,video,audio,audioTask};
  });
  const begin=Date.now();
  for(let frame=0;frame<count;frame++){
    await page.evaluate(t=>seek(t),frame/fps);
    const shot=await page.screenshot({type:'jpeg',quality:88});
    await page.evaluate(async ({data,frame,fps})=>{
      const bin=atob(data),bytes=new Uint8Array(bin.length);
      for(let i=0;i<bin.length;i++)bytes[i]=bin.charCodeAt(i);
      const image=await createImageBitmap(new Blob([bytes],{type:'image/jpeg'}));
      const {context,video}=window.__encode;
      context.drawImage(image,0,0,1600,900);image.close();
      await video.add(frame/fps,1/fps);
    },{data:shot.toString('base64'),frame,fps});
    if(frame%90===0)console.log(`${frame}/${count} frames · ${Math.round((Date.now()-begin)/1000)}s`);
  }
  const result=await page.evaluate(async()=>{
    const {output,video,audio,audioTask}=window.__encode;
    video.close();await audioTask;audio.close();await output.finalize();
    const data=new Uint8Array(output.target.buffer),parts=[];
    for(let i=0;i<data.length;i+=32768)parts.push(String.fromCharCode(...data.subarray(i,i+32768)));
    return{size:data.length,base64:btoa(parts.join(''))};
  });
  const target=path.join(folder,'promova-release-morph.mp4');
  await writeFile(target,Buffer.from(result.base64,'base64'));
  console.log(`${target}: ${result.size} bytes`);
}finally{await browser.close()}
