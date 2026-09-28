import { chromium } from 'playwright-core';
import { writeFile } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const directory = path.dirname(fileURLToPath(import.meta.url));
const browser = await chromium.launch({
  executablePath: '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome',
  headless: true,
  args: ['--disable-background-timer-throttling', '--disable-renderer-backgrounding'],
});

try {
  const page = await browser.newPage({ viewport: { width: 1600, height: 900 }, deviceScaleFactor: 1 });
  page.on('pageerror', error => console.error(`Browser: ${error.message}`));
  await page.goto('http://127.0.0.1:14177/artifacts/launch-video-2026-09-27/composition.html');
  const metadata = await page.evaluate(() => window.ready());
  console.log(`Rendering ${metadata.total.toFixed(2)}s with ${metadata.rawDuration.toFixed(2)}s raw footage`);
  await page.evaluate(() => window.startFilm());
  const result = await page.evaluate(() => window.finishFilm());
  if (!result.bytes) throw new Error('The recorded WebM is empty');
  const output = path.join(directory, 'promova-launch-video.webm');
  await writeFile(output, Buffer.from(result.base64, 'base64'));
  console.log(`${output} (${result.bytes} bytes)`);
} finally {
  await browser.close();
}
