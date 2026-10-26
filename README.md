# BlendCraft v1.5.0 — Expanded Glitch Lab + Shifting Runes Curse

Native Android image editor (Android 10+, compile/target SDK 36).
The Glitch Lab is embedded in the existing Photo, Screenshot and Meme studios.
Free users retain the fixed lower-right **BLENDCRAFT** watermark on previews, PNG/JPG/WebP exports and Android shares. No payment mechanism or premium entitlement is enabled.

## 31 working glitch effects

The ten prior effects are preserved: Pixel Sort, RGB Split, Data Smear, VHS/CRT, Signal Noise, Artifact Blocks, Wave Distortion, Posterize, Ghosting, and Corrupt Streaks.

21 new procedural CPU image-processing effects are integrated, with real pixel transformations and photo-based thumbnails:
- **Glitch** — corrupted signal bands and sampled neon color streaks.
- **PNG Corruption** — strip displacement and simulated channel/bitstream glitches (does not actually damage file headers).
- **WebP Fracture** — pixel block quantization and palette damage (visual simulation).
- **Paintbrush** — colored drag strokes derived from the photograph.
- **Field** — local radial lens/bubble distortion.
- **Hacker** — monochrome-green screen tint and vertical data interference.
- **Triangle** — triangular strip offset tiles.
- **Delaunay** — procedural triangle-based low-polygon approximation (not mathematical Delaunay triangulation).
- **Drone** — edge emphasis and cold scan tint.
- **Pixel** — mosaic pixel blocks.
- **XOR** — genuine bitwise XOR channel corruption.
- **Scanner** — shifting scan bars.
- **Window** — offset vertical rectangular columns.
- **Burn** — overexposed warm-toned channel distortion.
- **Quake** — seeded row jitter.
- **Warp** — nonlinear sine-wave displacement.
- **3D** — red/blue stereo channel offsets.
- **Zalgo** — green randomized trailing glyph-like streaks.
- **Chromatic** — widened RGB color aberration.
- **Datamosh** — deterministic block motion-corruption approximation.
- **Shifting Runes Curse** — rendered pseudo-runic strokes with seeded positions, live shifting phase, user-adjustable speed, and a **real HSV color selection wheel**.

The original eight corruption presets remain, as do editable masks, custom preset storage, up to six effect layers, blend modes, seeded randomness, before/after comparison, undo/redo and adjustable intensity.

Effect selection uses a touch-friendly two-column grid of **thumbnails generated from the current imported photo and the actual effect algorithm**. Selecting Shifting Runes immediately opens Controls, where its interactive color wheel is displayed. The wheel adjusts rune hue and saturation, not merely a few preset swatches.

## Memory and export

- Preview image transformations use an explicit 1000-pixel preview copy to prevent rendering the entire photo on every slider adjustment. Static exports render at the source/crop resolution.
- Bitmap import refuses images larger than 12 megapixels with an explanation, rather than silently reducing quality.
- Static PNG and WebP can preserve incoming alpha. JPG flattens transparency and uses lossy quality 95.
- All free exports retain the mandatory BlendCraft lower-right watermark, drawn after the glitch and caption layers.
- Live rune shifting is an **animated preview of a still effect**, and saving a static image captures the current glyph arrangement.

## Not yet implemented

The reference screenshots also contain **Animation** and **Video** tools. These are distinct export/workflow modes; this release does **not** yet encode GIF or MP4, and should not pretend to. The Glitch Lab is native Android; no desktop browser edition is included. There is no AI-based automatic subject segmentation.

## Build, tests and signing

GitHub Actions runs JVM pixel tests using Android Bitmap test stub. Tests cover all 31 effects, alpha retention, source non-destruction, seed reproduction, sorting directions, brush masks and shifting rune color/phase. It then builds test Android variants and the Google Play release **AAB**:
- Google Play: package com.snoplay.blendcraft, versionCode **7**, targetSdk **36**.
- SnoPlay direct: package com.snoplay.blendcraft.snoplay, versionCode **7**, targetSdk **36**.
- Production SnoPlay APK and Play AAB must use the owner's **existing permanent signing certificate**; never generate a replacement and never commit its private key or password to GitHub.
- Output formats: signed SnoPlay APK, signed Google Play AAB only after signing; GitHub CI test APKs use ephemeral debug signing.

Automated algorithm and build verification is not a substitute for physical device and full Android UI testing. More rendering stress tests and accessible UI checks are needed before wider release.

© 2026 SnoPlay — All rights reserved.
