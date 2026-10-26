# BlendCraft v1.4.0 — Glitch Lab

BlendCraft is SnoPlay's offline-first, native Android image editor (Java, Android SDK 36).
**The Glitch Lab is integrated into the existing Home / Photo Lab / Screenshot Studio / Meme Studio workflow.** It does not launch a separate demo or replace other editing tools.

## Original tools retained
- File import and Android Share-to-BlendCraft
- Photo editing (rotate, crop, color tuning), meme captions and overlay text
- Screenshot presentation frames
- Canvas annotations, editor undo/redo, My Creations gallery
- Unremovable **BlendCraft Free** watermark in the lower-right corner of exported images
- Saving and sharing edited images through Android MediaStore (Pictures/BlendCraft)
- Existing saved image exports are not migrated or deleted by this update.

## Glitch Lab — functional in this build
Ten actual, deterministic ARGB pixel processing algorithms in \`GlitchFx.java\`:
1. Pixel Sort — true thresholded contiguous-window pixel sorting, horizontal/vertical and brightness/saturation/red/green/blue ranking
2. RGB Split — displaced color channels and chromatic aberration
3. Data Smear — seeded horizontal displacement strips
4. VHS/CRT — scanlines, tracking offsets, flicker
5. Signal Noise — independent seeded RGB grain
6. Artifact Blocks — seeded tile displacement
7. Wave Distortion — horizontal tear and vertical wobble
8. Posterize — adjustable palette quantization
9. Ghosting — shifted blended image echo
10. Corrupt Streaks — seeded dragged pixel fragments

UI: **Effects, Presets, Controls, Masks, Brush, Layers, Export**. Controls include intensity, scale, threshold, direction, opacity, channel sort order, manual numeric seed and seed lock. Eight included presets are Signal Loss, Data Melt, Broken Neon, VHS Haunting, Cyber Static, Pixel Collapse, Corrupt Dream and Darkness Corruption. One user-generated custom preset can be saved and recalled locally.

There are up to six composited effect layers with Normal, Screen, Additive and Multiply blend modes. Mask choices: entire image, center ellipse, outside-center, and paintable brush regions; brush radius is adjustable. Center ellipse is a **geometric approximation, not automatic AI subject segmentation**. Undo/redo deep-copies the image-editing state and glitch configuration. Original-versus-edited comparison is available as a toggle.

The underlying imported source Bitmap remains unchanged. Previews are computed asynchronously on a worker thread and composited by Android's Canvas. The export path uses the same effect configuration and draws existing captions, annotations and mandatory free watermark **after** glitch processing.

## Export
- PNG: lossless, preserves transparency if the imported image contains it.
- JPG: lossy at quality 95, explicitly composited against white.
- WebP: lossless on Android 11+, otherwise user is warned that it is lossy.
- Export resolution follows the imported bitmap's dimensions / selected crop (screenshot presentation intentionally uses a square canvas). No silent downsampling occurs.
- Images greater than **12 megapixels** are rejected at import with a visible explanation rather than quietly resized. This memory guard is for low-memory Android devices.

## Not present (do not advertise as completed)
- Animated/keyframed glitches; GIF/MP4 export; adjustable animation speed and frequency
- AI subject segmentation or automatic background removal
- Multi-branch project saving, multi-slot custom preset library or cloud sync
- Desktop-browser/Web edition of Glitch Lab (the existing product is Android-native)
- Verified purchase entitlement / working Premium checkout and watermark removal
- Comprehensive on-device UI, memory-stress and MediaStore export instrumentation tests (still required before public release)

## Automated verification
\`.github/workflows/build-apk.yml\` runs deterministic JVM tests using a minimal stand-in Android Bitmap to exercise all ten pixel operations, eight presets, both sorting directions, reproducible random seeds, brush masking, alpha retention, original integrity and undo/deep-copy behavior. GitHub Actions then compiles both Android variants and the Play release bundle (target SDK 36, version code 6).

**Artifact signing:** The permanent private PKCS#12 signing key is **not** in this repository. Use the existing owner's stored keystore to sign production releases, and always increment \`versionCode\`. The Play edition is an AAB (\`com.snoplay.blendcraft\`), while the SnoPlay edition is an APK (\`com.snoplay.blendcraft.snoplay\`). Test APKs from GitHub Actions use ephemeral debug signing unless explicitly re-signed.

## Licensing
Copyright © 2026 SnoPlay. All rights reserved.
