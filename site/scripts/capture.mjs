// Drift is an Android app with no emulator in CI, so the site reuses the
// Roborazzi screenshots its own UI tests record on synthetic fixtures
// (feature/*/src/test/screenshots, 1x font scale, dark theme). This copies the
// chosen ones into src/assets/screens/, cropped to a phone-height viewport.
// Re-record them first with `./gradlew :feature:<name>:recordRoborazziDebug`.
// Usage: node scripts/capture.mjs
import sharp from "sharp";
import { mkdirSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const here = dirname(fileURLToPath(import.meta.url));
const repo = join(here, "../..");
const OUT = join(here, "../src/assets/screens");
mkdirSync(OUT, { recursive: true });

// site name -> repo screenshot. Tall screens are cut at 9:20, the top of the scroll.
const SHOTS = {
  "energy-timeline": "feature/energy/src/test/screenshots/energy_full_ist_dark_1x.png",
  "sleep-home": "feature/sleep/src/test/screenshots/sleep_ready_ist_dark_1x.png",
  "body-clock": "feature/sleep/src/test/screenshots/body_ready_ist_dark_1x.png",
  "history-week": "feature/sleep/src/test/screenshots/history_week_ist_dark_1x.png",
  "debt-why": "feature/sleep/src/test/screenshots/why_changes_ist_dark_1x.png",
  "methodology": "feature/settings/src/test/screenshots/methodology_expanded_dark_1x.png",
};

for (const [name, src] of Object.entries(SHOTS)) {
  const img = sharp(join(repo, src));
  const { width, height } = await img.metadata();
  const h = Math.min(height, Math.round((width * 20) / 9));
  await img.extract({ left: 0, top: 0, width, height: h }).png().toFile(join(OUT, `${name}.png`));
  console.log("shot", name, `${width}x${h}`);
}
