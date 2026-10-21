// Repo-relative markdown path <-> guide route. Shared by the content loader
// and the remark link rewriter so the two can never disagree.
import cfg from "../../site.config.mjs";

export const REPO_URL = cfg.repoUrl;
export const BASE = cfg.base;
export const RAW_URL = `https://raw.githubusercontent.com/${cfg.owner}/${cfg.repoName}/${cfg.branch}`;
export const GUIDE_PATTERNS = cfg.guide;

const SPECIAL = { "docs/README.md": "index", [cfg.guideRoot]: "readme", ".github/CONTRIBUTING.md": "contributing" };

/** "docs/adr/0001-x.md" -> "adr/0001-x"; "docs/foo/README.md" -> "foo". */
export function slugFor(repoPath) {
  if (SPECIAL[repoPath]) return SPECIAL[repoPath];
  return repoPath
    .replace(/\.md$/, "")
    .replace(/^docs\//, "")
    .replace(/\/README$/, "")
    .toLowerCase();
}

/** Mirrors GUIDE_PATTERNS for a single path (supports "dir/**\/*.md", exact files and "!" excludes). */
export function isGuidePath(repoPath) {
  if (!repoPath.endsWith(".md")) return false;
  const match = (glob) => {
    const re = new RegExp("^" + glob.replace(/[.+^${}()|[\]\\]/g, "\\$&").replace(/\*\*\//g, "(?:.*/)?").replace(/\*/g, "[^/]*") + "$");
    return re.test(repoPath);
  };
  const include = GUIDE_PATTERNS.filter((p) => !p.startsWith("!")).some(match);
  const exclude = GUIDE_PATTERNS.filter((p) => p.startsWith("!")).some((p) => match(p.slice(1)));
  return include && !exclude;
}
