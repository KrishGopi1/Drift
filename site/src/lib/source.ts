import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import cfg from "../../site.config.mjs";

// npm runs site scripts from site/. import.meta.url moves when Astro bundles
// this module into its prerender output, so it cannot locate repository files.
const root = resolve(process.cwd(), "..");

/** Build-time source excerpts keep the site's code identical to the repository. */
export function sourceExcerpt(file: string, first: number, last: number): string {
  return readFileSync(resolve(root, file), "utf8").split("\n").slice(first - 1, last).join("\n");
}

export const REPO = cfg.repoUrl;
/** GitHub URL for a repo-relative path, e.g. blob("src/app.py") */
export const blob = (path: string) => `${cfg.repoUrl}/blob/${cfg.branch}/${path}`;
export const withBase = (path = "") => `${cfg.base}/${path.replace(/^\//, "")}`.replace(/\/$/, "") || "/";
export { cfg };
