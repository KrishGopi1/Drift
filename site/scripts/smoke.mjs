// Run against `npm run build` + `npm run preview`, including the Pages base path.
// Checks what breaks silently: overflow at 3 widths, both themes, no-JS,
// reduced motion, console errors, placeholder screenshots, guide + search.
import assert from "node:assert/strict";
import { mkdir } from "node:fs/promises";
import { existsSync } from "node:fs";
import path from "node:path";
import { chromium } from "playwright";
import cfg from "../site.config.mjs";

const url = process.env.SITE_URL ?? `http://127.0.0.1:4321${cfg.base}/`;
const origin = new URL(url).origin;
const output = path.resolve(process.env.QA_OUTPUT_DIR ?? "test-results/site");
await mkdir(output, { recursive: true });
const executablePath = process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH
  ?? (existsSync("/usr/bin/chromium") ? "/usr/bin/chromium" : undefined);
const browser = await chromium.launch({ executablePath, headless: true });

const overflow = (page) => page.evaluate(() => document.documentElement.scrollWidth > innerWidth);
let runs = 0;

try {
  for (const width of [375, 768, 1440]) {
    for (const colorScheme of ["light", "dark"]) {
      const label = `${width}-${colorScheme}`;
      const context = await browser.newContext({ viewport: { width, height: 900 }, colorScheme });
      const page = await context.newPage();
      const errors = [];
      page.on("pageerror", (e) => errors.push(e.message));
      assert.equal((await page.goto(url)).status(), 200);
      await page.evaluate(() => document.fonts.ready);
      assert.equal(await page.getByText("Screenshot pending").count(), 0, `${label}: placeholder screenshot`);
      for (const id of await page.locator(".chapter").evaluateAll((els) => els.map((e) => e.id))) {
        await page.locator(`#${id}`).scrollIntoViewIfNeeded();
        await page.waitForTimeout(80);
        assert.equal(await overflow(page), false, `${label}/${id}: horizontal overflow`);
      }
      await page.evaluate(() => scrollTo({ top: 0, behavior: "instant" }));
      await page.screenshot({ path: path.join(output, `${label}.png`), fullPage: true });

      await page.goto(`${origin}${cfg.base}/guide/`);
      assert.equal(await overflow(page), false, `${label}/guide: horizontal overflow`);
      if (width >= 960) {
        const term = (await page.locator(".guide__menu a").first().innerText()).split(/\s+/)[0];
        await page.locator(".pagefind-ui__search-input").fill(term);
        await page.locator(".pagefind-ui__result-link").first().waitFor();
        const href = await page.locator(".pagefind-ui__result-link").first().getAttribute("href");
        assert.ok(new URL(href, page.url()).pathname.startsWith(`${cfg.base}/guide/`), `search loses base: ${href}`);
      }
      assert.deepEqual(errors, [], `${label}: browser exceptions`);
      await context.close();
      runs++;
    }
  }
  for (const mode of ["no-js", "reduced-motion"]) {
    const context = await browser.newContext({ viewport: { width: 375, height: 812 }, javaScriptEnabled: mode !== "no-js", reducedMotion: "reduce" });
    const page = await context.newPage();
    await page.goto(url);
    assert.ok(await page.locator("h1").isVisible(), `${mode}: hero hidden`);
    assert.equal(await overflow(page), false, `${mode}: horizontal overflow`);
    await context.close();
    runs++;
  }
  console.log(`Site smoke passed: ${runs} runs (3 widths x 2 themes, no-js, reduced-motion), guide search checked.`);
} finally {
  await browser.close();
}
