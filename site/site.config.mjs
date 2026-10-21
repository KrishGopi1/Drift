// The one file you edit per project. Everything else reads from here.
// Points at upstream. To preview on a fork's Pages, change owner.
const owner = "KrishGopi1";
const repoName = "Drift";

export default {
  name: "Drift",
  tagline: "Your sleep debt, energy curve and body clock, worked out on your phone.",
  description:
    "Drift is a free, open-source Android app. It reads the sleep you already track, runs published sleep models on the device, and has no internet permission.",
  owner,
  repoName,
  branch: "ui/visual-refresh",
  repoUrl: `https://github.com/${owner}/${repoName}`,
  releaseUrl: `https://github.com/${owner}/${repoName}/releases/latest`,
  // Project Pages live at https://OWNER.github.io/REPO/. For a custom domain
  // set site to https://docs.example.com and base to "".
  site: `https://${owner}.github.io`,
  base: `/${repoName}`,
  // Shown in the footer. State real constraints (synthetic data, alpha, etc.).
  footnote:
    "Screenshots come from Drift's own UI tests on synthetic sleep data. Drift is a wellness tool, not medical advice.",
  // Markdown rendered in place under /guide (repo-relative globs, "!" excludes).
  // No copies: a doc fixed in the repo is fixed on the site at the next build.
  guide: ["docs/README.md", "docs/science.md", "docs/data-sources.md", "docs/development.md", ".github/CONTRIBUTING.md"],
  guideRoot: "README.md", // becomes /guide/readme; docs/README.md becomes /guide/
};
