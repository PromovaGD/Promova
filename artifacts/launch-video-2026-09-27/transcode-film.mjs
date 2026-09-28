import { chromium } from 'playwright-core';
import { writeFile } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const directory = path.dirname(fileURLToPath(import.meta.url));
const browser = await chromium.launch({
  executablePath: '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome',
  headless: true,
});

try {
  const page = await browser.newPage();
  page.on('console', message => { if (message.type() === 'log') console.log(message.text()); });
  await page.goto('http://127.0.0.1:14177/');
  const result = await page.evaluate(async () => {
    const media = await import('/node_modules/mediabunny/dist/bundles/mediabunny.min.mjs');
    const response = await fetch('/artifacts/launch-video-2026-09-27/promova-launch-video.webm');
    if (!response.ok) throw new Error(`Source unavailable: ${response.status}`);
    const input = new media.Input({ source: new media.BlobSource(await response.blob()), formats: media.ALL_FORMATS });
    const output = new media.Output({
      format: new media.Mp4OutputFormat({ fastStart: 'in-memory' }),
      target: new media.BufferTarget(),
    });
    const conversion = await media.Conversion.init({
      input,
      output,
      video: { codec: 'avc', quality: new media.Quality('high'), keyFrameInterval: 2 },
    });
    if (!conversion.isValid) throw new Error(`Conversion invalid: ${JSON.stringify(conversion.discardedTracks)}`);
    let announced = -1;
    conversion.onProgress = progress => {
      const decile = Math.floor(progress * 10);
      if (decile > announced) { announced = decile; console.log(`MP4 ${Math.min(100, decile * 10)}%`); }
    };
    await conversion.execute();
    const bytes = new Uint8Array(output.target.buffer);
    const parts = [];
    for (let i = 0; i < bytes.length; i += 32768) parts.push(String.fromCharCode(...bytes.subarray(i, i + 32768)));
    return { size: bytes.length, base64: btoa(parts.join('')) };
  });
  if (!result.size) throw new Error('The MP4 is empty');
  const output = path.join(directory, 'promova-launch-video.mp4');
  await writeFile(output, Buffer.from(result.base64, 'base64'));
  console.log(`${output} (${result.size} bytes)`);
} finally {
  await browser.close();
}
