# SnoPlay Video Studio ✦

**Local-first multishot video workshop.** Inspired by the workflow of prompt-to-film systems; original source code. This does NOT contain VideoGen's proprietary Model 1 or its weights.

One idea → Ollama storyboard (or offline fallback) → editable scenes → reference images → videos/images via uploads or your own ComfyUI workflow → optional music/narration → FFmpeg MP4.

## Requirements

- Windows 11 (or Linux/macOS) with Python 3.10+
- FFmpeg installed and available on PATH
- Optional Ollama running on http://127.0.0.1:11434 with the model mistral-nemo
- Optional ComfyUI running on http://127.0.0.1:8188 with a working installed video workflow
- No account, subscription or external API key required.

## Start on Windows PowerShell

Install FFmpeg and restart your terminal:

    winget install Gyan.FFmpeg

From this folder:

    py -m pip install -r requirements.txt
    py server.py

Open http://127.0.0.1:8765

To use another local Ollama model:

    $env:OLLAMA_MODEL = "devstral-abliterated-16k"
    py server.py

Without Ollama, a quick editable storyboard still works. Without AI-generated footage or user uploads, the studio produces a cinematic title-card video, not true animated footage.

## Story pipeline

1. Enter a complete story brief, recurring characters, camera direction, and look.
2. Choose a duration from 15 to 600 seconds and your aspect ratio.
3. Create the storyboard. Ollama may need a minute or more.
4. Upload up to 16 reference images. They stay local; attach a reference to the scene where it is needed.
5. Edit each scene title, description, narration text and duration. Attach a video/image or use ComfyUI to generate footage.
6. Add optional full-film soundtrack and recorded narration audio.
7. Render the whole film. The backend normalizes and concatenates shots to H.264 MP4.

## Optional ComfyUI connector

1. In ComfyUI export an ALREADY WORKING video workflow in API JSON format.
2. Import it in the Advanced ComfyUI panel. Optionally specify the positive prompt node ID.
3. The studio replaces {{PROMPT}}, {{WIDTH}}, {{HEIGHT}}, {{FRAMES}} strings anywhere in workflow inputs and sends the edited graph to your local ComfyUI.
4. Press Generate via ComfyUI on one shot. Output must include a video or image file retrievable from the ComfyUI /history API.
5. This is workflow-dependent: models, nodes, frame counts and memory requirements vary. Generate shots sequentially, especially on a 16 GB GPU.

Narration scripts are written into the storyboard but not voiced by the starter app; attach prerecorded/generated audio. Image references are attached manually to shots, not automatically ingested into every diffusion workflow.

## Scope and privacy

The API binds to 127.0.0.1. Projects and uploads stay in the ignored projects/ folder. Network requests go only to configured Ollama and ComfyUI endpoints. Maximum uploaded file size is 300 MB; extensions are allowlisted. Do not expose the server publicly without adding authentication.

**Works in v0.1:** persistent local projects, fallback planning, Ollama storyboarding, editable scenes, reference collection, media uploads, optional ComfyUI generation through API workflow, single soundtracks and voiceovers, MP4 rendering, progress polling.

**Planned:** scene reordering, voice casting and synthesis, character-memory injection, built-in retry system, more robust GPU queueing, and packaged Windows installer.

SnoPlay original code; respect the licenses of any generator models you choose to install.
