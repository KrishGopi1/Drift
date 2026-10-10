# Contributing to Drift

Drift is open source. Contributions are welcome, from a typo fix to a better sleep model.

## Before you start

- **Bugs:** open a [bug report](https://github.com/KrishGopi1/Sleep-Project/issues/new?template=bug_report.yml).
- **Feature ideas:** open a [feature request](https://github.com/KrishGopi1/Sleep-Project/issues/new?template=feature_request.yml).
- **Changes to sleep calculations or scientific models:** open a
  [scientific issue](https://github.com/KrishGopi1/Sleep-Project/issues/new?template=scientific_issue.yml) first,
  so the approach is agreed before you write code.
- **Questions and open-ended ideas:** use [Discussions](https://github.com/KrishGopi1/Sleep-Project/discussions).
  Issues are for things that need to be fixed or built; Discussions are for things that need conversation.
- **Security problems:** never in a public issue. See [SECURITY.md](SECURITY.md).

Looking for something to work on? Try issues labelled
[`good first issue`](https://github.com/KrishGopi1/Sleep-Project/labels/good%20first%20issue) or
[`help wanted`](https://github.com/KrishGopi1/Sleep-Project/labels/help%20wanted). Comment on the issue so
nobody else picks it up at the same time.

## What belongs in Drift

Drift is open to contributions, but not every change belongs in Drift.

The project prioritizes, in this order:

1. **Offline operation.** The app has no INTERNET permission and never will.
2. **Privacy.** No analytics, tracking, advertising, accounts or cloud dependencies.
3. **Scientific defensibility.** Every number should be traceable to a model, a parameter and a source.
4. **Deterministic analysis.** Same input + same engine version = same output.
5. **Accessibility.** Every screen works with TalkBack and large text.
6. **Maintainability.** Small, tested, readable changes.

A feature that makes the app more complicated without providing meaningful value may be rejected. A
scientifically interesting feature still needs evidence and testing. "Open source" doesn't mean every PR gets
merged, but a declined PR will always get an explanation.

## Development

1. Fork the repository.
2. Create a branch from the default branch (`fix/widget-description`, `feat/hindi-strings`, ...).
3. Make your changes.
4. Run the relevant tests (below).
5. Open a pull request and fill in the template.

Setup, the module map and the commands are in [docs/development.md](../docs/development.md). The short version:

```bash
source scripts/env.sh            # sets JAVA_HOME / ANDROID_HOME if you use the per-user toolchain
./gradlew assembleDebug          # build the app
./gradlew :core:engine:test      # test the module you changed
./gradlew driftChecks            # project rules: no INTERNET, no placeholders, module dependency rules
```

CI runs the full check (`scripts/ci.sh`) on every pull request, so you don't need to run everything locally.

## Important

- Changes to the analysis engine (`core/engine`) must include tests. If outputs change, update the golden
  files and bump the engine version in `EngineVersion.kt` and `ParameterSet.kt`.
- Changes to scientific calculations should include the reasoning and references behind the change. New or
  changed constants go in `ParameterSet.kt` with a `Citation`.
- `core/engine` stays pure: no Android imports, no I/O, no `Instant.now()`, no unseeded `Random`.
- Do not add network requests, analytics, tracking, advertisements, or cloud dependencies. Drift is designed to
  remain offline-first and privacy-focused. CI fails if the INTERNET permission appears.
- No TODO, FIXME, placeholder text, mock data or dead buttons in `src/main`. Test fakes live in `testing/` or `src/test`.
- Use `java.time` for all dates and times; store UTC instants plus a zone.
- Original wording and assets only. Don't copy text, icons, fonts or illustrations from other apps.

## Pull requests

Explain:

- What you changed
- Why you changed it
- How you tested it
- Whether it changes any calculations or user-visible behavior

Keep each PR to one change. UI changes need before/after screenshots. PRs need passing CI and a review before
they're merged.

## Code of conduct

By taking part you agree to follow the [Code of Conduct](CODE_OF_CONDUCT.md).
