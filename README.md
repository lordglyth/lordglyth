# BlendCraft for Android
A real, offline-first Android image editor from SnoPlay.

## What works in v1
- Home screen with Photo, Screenshot and Meme studios
- Import images from device using Android's document picker; accept image shares
- Crop presets (original / square / portrait / story / landscape), rotate
- Brightness, contrast and saturation adjustment
- Meme captions, custom text, emojis, optional text watermark
- Screenshot-ready gradient framing
- Finger drawing annotations with multiple colors
- Undo and redo during editing
- Save full-size PNG to Pictures/BlendCraft and share using Android sharesheet
- Recent creations browser for exported images
- No account, no tracking, no API key, no ads, no internet permission

## Build
Requires JDK 17, Android SDK 35, Gradle 8.11.1.
Run `gradle :app:assembleDebug`.
An automated GitHub Actions workflow builds and uploads the APK on each push to the `blendcraft-android` branch. Download the `BlendCraft-debug-APK` artifact from Actions to install.

## Scope
This is a functioning starter editor, not the full enterprise BlendCraft design document.
Not yet included: AI captions, object/background removal, true multilayer editable project files, team collaboration, marketplace, subscription billing, animated/video export, or systemwide screen capture. For screenshots, use Android's built-in screenshot button and Share > BlendCraft.

## License
Copyright 2026 SnoPlay. All rights reserved.
