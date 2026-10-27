# BlendCraft v1.8.0 — PAINTABLE Glitch Lab + Expanded Canvas

This release corrects the earlier interaction mistake. Selecting one of the 31 glitch effects now **arms its paint brush**, it does not apply the effect to the whole image. The selected brush automatically opens a larger painting workspace with minimal controls.

## Basic flow
1. Import a photo and open **Glitch Lab**.
2. Tap **Effects**, then tap a thumbnail to choose the effect brush.
3. The app opens expanded Canvas mode. Use **one finger to paint the effect onto the photo**. The artwork remains original anywhere you did not paint.
4. Tap **ERASE** to erase brush strokes without affecting other effect layers. Tap PAINT to brush the same effect again; later paint can cover erased portions.
5. **Two fingers pinch to zoom and pan**. FIT shows the entire image, FILL zooms until the editing area is covered, +/− zoom buttons offer precise control.
6. Press EFFECTS to choose a new effect brush. Existing painted areas remain on independent layers. Up to 12 effect layers are available.
7. The original image remains unchanged. Undo/Redo and full-size PNG/JPG/WebP export preserve the individual paint masks.

The **Presets** tab also loads the chosen preset into a grouped paint brush, rather than applying it across the whole picture. Manual painted masks are the default; applying a whole-image, ellipse or outside-ellipse mask is an **optional explicitly confirmed action**. The Shifting Runes Curse brush retains its HSV color wheel and shifting phase controls.

## Canvas and painting
- **⤢ CANVAS / EXPAND:** collapses the bulky inspector and leaves more screen for the photo.
- **FILL:** makes a landscape image fill a portrait viewport by cropping the visible edges. Two-finger pan moves to those edges. FIT restores the entire uncropped view.
- **Pinch:** zoom ranges from 0.75× to 8× while viewport pan persists.
- **Brush cursor:** live preview of brush radius. Paint and eraser marks are stored in image-normalized coordinates and exported at original resolution.
- Brush marks are timestamp-ordered within each layer. Eraser subtraction and new painting work without altering earlier unrelated effect strokes.
- High-resolution images above the existing 12MP safety cap are rejected explicitly, not silently downsampled.

## Retained features
31 working image-glitch algorithms, rune shift with color wheel, preset effects, color/crop/text/meme editor, screenshot framing, history, layer blending, original-image comparison, custom preset saving, local My Creations, and the mandatory free-version bottom-right BlendCraft watermark.

## Not built
MP4 encoding, AI background subject masking, desktop browser support, and live paid-Premium entitlement verification are still not available.

## Android builds
- Play bundle: package com.snoplay.blendcraft, Android API 36, versionCode **10**.
- SnoPlay APK: package com.snoplay.blendcraft.snoplay, Android API 36, versionCode **8**.
- The owner's **existing** private PKCS#12 signing key must be reused for production packages. This key MUST NOT be committed to public GitHub.
- Continuous integration tests effect selection as a no-op, per-layer painted masks, erasing, re-painting, undo/deep-copy behavior, original integrity, all 31 algorithms, runic shift and seeded reproducibility.
- Algorithm regression tests and compilation do not substitute for an actual physical phone UI test.

Copyright © 2026 SnoPlay. All rights reserved.

## v1.7.0: Multi-Brush and Touch Precision Patch

- Dedicated source-to-output coordinate mapping prevents brush placement drifting on square/portrait crops or framed screenshots, including after FIT/FILL/pan/zoom.
- Each newly selected effect is independently brush-painted; prior painted layers stay intact. Existing layers are accessible from a LAYERS shortcut even while Canvas is expanded.
- Brush interpolation is sampled in original image pixels, with proportional spacing to avoid thousands of redundant dabs that blocked effect rendering.
- An immediate visible stroke overlay and correctly scaled brush outline track the finger while the effect renderer works on a capped-size preview.
- Two-finger pinch zoom anchors the pixel beneath your fingers while moving, instead of jumping relative to the viewport center.
- Automated tests cover touch positions for landscape/portrait crops, framed screenshots, viewport pan/zoom, mask independence and undo.
- Original source remains untouched; full-size static exports use the stored per-effect masks and retain the mandatory free watermark.

This build has automated Java pixel and coordinate tests and Android compiler checks. Touch behavior must also be confirmed on the physical phone.

## v1.8.0: PNG / GIF Everywhere

Photo Studio, Screenshot Studio, Meme Studio and Glitch Lab now share a **Save As** dialog supporting full-resolution PNG or true GIF89a (plus JPG and WebP). The Glitch Lab Export tab also has an explicit **GIF LOOP** action. Share uses the same format picker.

- GIF animations contain up to **18 frames** with **140ms delay** (approximately **2.52 seconds**, looping continuously). A still image without active painted effects produces a valid single-frame GIF.
- **Shifting Runes Curse** advances its procedural rune phase (speed-dependent); other active glitches vary their phase/scale/random seed deterministically across frames.
- GIF output max dimension is **640 pixels** to cap memory and processing time on phones. PNG preserves the existing original-size output behavior.
- This GIF encoder supports **256 colors with no transparency**. Transparent pixels are composited against white instead of silently discarded. The app warns about the size/quality difference in the Export panel.
- Captions, drawn annotations, frames, blend adjustments, layered brushes and the **non-optional Free watermark** are drawn over every GIF frame using the same Canvas renderer as PNG.
- GIF processing runs off the UI thread; failed exports clean up partially written Android MediaStore files.
- GIF generation uses an offline pure-Java Apache 2.0 implementation, `com.squareup:gifencoder:0.10.1` (<https://github.com/square/gifencoder>), with no API key.
- No MP4 video export, AI subject segmentation or working paid-entitlement validation yet.

CI tests check GIF frame planning, seed/rune phase progression, immutable editing state and original image retention. Android compilation tests verify the actual GIF encoder can be referenced. On-device export/playback validation is still required.
