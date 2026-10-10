# Changelog

All notable changes to Drift are listed here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and versions follow
[Semantic Versioning](https://semver.org/). Engine output changes are noted with the engine version.

## [Unreleased]

### Added
- Community files: contributing guide, code of conduct, security policy, support guide, issue forms and a
  pull request template.
- Documentation: architecture, science, data sources, development, roadmap.
- GitHub Actions: CI on every pull request, and automatic APK releases from version tags.

## [0.1.0] - 2026-10-10

First public release. Engine 1.1.1.

### Added
- Sleep, Energy, Progress and Learn tabs.
- Energy timeline over 36 hours with drag-to-edit sleep, habit markers and the melatonin window.
- Sleep debt (14-night leaky accumulator), personal sleep need (Kalman filter), circadian phase (DLMO),
  Sleep Regularity Index, chronotype, energy potential and recommendations.
- Data from Health Connect, Sleep as Android and on-device phone detection, merged and deduplicated locally.
- Home-screen widget and reminders.
- Encrypted SQLCipher database with the key in Android Keystore; no INTERNET permission.

[Unreleased]: https://github.com/KrishGopi1/Sleep-Project/compare/v0.1.0...HEAD
[0.1.0]: https://github.com/KrishGopi1/Sleep-Project/releases/tag/v0.1.0
