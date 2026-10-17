# goiv-lite

A lightweight Pokémon GO IV checker for Android — a streamlined fork of
[GoIV](https://github.com/farkam135/GoIV) (open source, archived upstream).

**Status: early work in progress — does not build yet.** The codebase is
being pruned and modernized in phases.

## v1 goal

Floating button → tap → screenshot → on-device OCR (CP, HP, name, power-up
cost) → IV % + range overlay. Nothing else.

## What's changing from GoIV

- **OCR:** Google ML Kit bundled text recognition replaces the 449 MB
  Tesseract + OpenCV native stack (~4 MB instead, still fully offline)
- **Cut:** clipboard rename engine, auto screen-watcher, appraisal reader,
  movesets, updater, share handler, settings bloat (~112 → ~33 Java files)
- **Modernized:** AndroidX, current target SDK, GitHub Actions debug builds

## Build

Every push to `main` triggers the CI workflow, which uploads a debug APK
as an artifact (download from the Actions tab). Sideload it on your phone —
no Play Store needed.
