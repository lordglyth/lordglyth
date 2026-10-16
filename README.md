# BlendCraft for Android
A real, offline-first Android image editor from SnoPlay.

## What works in v1
- Home screen with Photo, Screenshot and Meme studios
- Import images from device using Android's document picker; accept image shares
- Crop presets (original / square / portrait / story / landscape), rotate
- Brightness, contrast and saturation adjustment
- Meme captions, custom text, emojis, mandatory non-removable lower-right BlendCraft brand mark in every free export
- Screenshot-ready gradient framing
- Finger drawing annotations with multiple colors
- Undo and redo during editing
- Save full-size PNG to Pictures/BlendCraft and share using Android sharesheet
- Recent creations browser for exported images
- No account, no tracking, no API key, no ads, no internet permission
- Free version watermark is drawn LAST by the central Canvas renderer, so both PNG exports and Android Share always include it
- Branding control explains the free watermark but provides no disable, movement, opacity, resizing or text setting

## Build
Requires JDK 17, Android SDK 35, Gradle 8.11.1.
Run `gradle :app:assembleDebug`.
An automated GitHub Actions workflow builds and uploads the APK on each push to the `blendcraft-android` branch. Download the `BlendCraft-debug-APK` artifact from Actions to install.

## Scope
This is a functioning starter editor, not the full enterprise BlendCraft design document.
Not yet included: premium purchase processing and verified premium entitlements, AI captions, object/background removal, true multilayer editable project files, team collaboration, marketplace, subscription billing, animated/video export, or systemwide screen capture. For screenshots, use Android's built-in screenshot button and Share > BlendCraft.

## Premium billing plan
The current APK is the **Free** edition, so the lower-right BlendCraft watermark is always applied. A normal PayPal.me link is not sufficient to securely verify purchases. Store-based billing (Google Play Billing) or an external checkout with backend purchase validation must be added before enabling watermark-free Premium. There is no hidden local premium toggle in this build.

## License
Copyright 2026 SnoPlay. All rights reserved.
