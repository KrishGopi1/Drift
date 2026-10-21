// Rewrites relative links inside repo markdown rendered under /guide:
// a link to another guide doc becomes a site route, anything else in the
// repo (code, configs, archived docs) becomes a GitHub URL. Docs stay
// written for GitHub; the site adapts to them, not the other way round.
import path from "node:path";
import { fileURLToPath } from "node:url";
import cfg from "../../site.config.mjs";
import { BASE, RAW_URL, REPO_URL, isGuidePath, slugFor } from "./slug.mjs";

const REPO_ROOT = path.resolve(fileURLToPath(new URL("../../..", import.meta.url)));

function walk(node, fn) {
  fn(node);
  if (node.children) for (const child of node.children) walk(child, fn);
}

export function rewriteRepoLinks() {
  return (tree, file) => {
    const source = file.path ?? file.history?.[0];
    if (!source || !path.resolve(source).startsWith(REPO_ROOT)) return;
    const fromDir = path.dirname(path.relative(REPO_ROOT, path.resolve(source)));

    walk(tree, (node) => {
      if (node.type === "html") {
        // Raw <img src="./x"> (GitHub README banners) needs the same raw-bytes URL.
        node.value = node.value.replace(/(<img\b[^>]*?\ssrc=")(?!\w+:|\/\/|#)([^"]+)"/gi, (_, pre, src) => {
          const repoPath = path.posix.normalize(path.posix.join(fromDir.split(path.sep).join("/"), src));
          return repoPath.startsWith("..") ? _ : `${pre}${RAW_URL}/${repoPath}"`;
        });
        return;
      }
      if (node.type !== "link" && node.type !== "definition" && node.type !== "image") return;
      const url = node.url;
      if (!url || /^[a-z]+:|^\/\/|^#/i.test(url)) return;

      const [target, hash] = url.split("#");
      const repoPath = path.posix.normalize(path.posix.join(fromDir.split(path.sep).join("/"), target));
      if (repoPath.startsWith("..")) return;
      const anchor = hash ? `#${hash}` : "";

      if (node.type === "image") {
        // Markdown image URLs need file bytes rather than GitHub's HTML viewer.
        node.url = `${RAW_URL}/${repoPath}`;
      } else if (isGuidePath(repoPath)) {
        const slug = slugFor(repoPath);
        node.url = `${BASE}/guide/${slug === "index" ? "" : slug}${anchor}`;
      } else {
        const kind = target.endsWith("/") || !path.posix.extname(repoPath) ? "tree" : "blob";
        node.url = `${REPO_URL}/${kind}/${cfg.branch}/${repoPath.replace(/\/$/, "")}${anchor}`;
      }
    });
  };
}
