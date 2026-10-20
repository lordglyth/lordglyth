"""SnoPlay Video Studio — local-first storyboard and multishot video composer.

Uses Ollama for optional planning, ComfyUI for optional AI shots, and ffmpeg for
deterministic video assembly. Keeps every project and image on the local machine.
"""
from __future__ import annotations

import json
import os
import random
import re
import shutil
import subprocess
import threading
import time
import uuid
from pathlib import Path
from urllib import request, parse, error

from fastapi import FastAPI, File, HTTPException, UploadFile
from fastapi.responses import FileResponse
from fastapi.staticfiles import StaticFiles
from pydantic import BaseModel, Field
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parent
DATA = ROOT / "projects"
DATA.mkdir(exist_ok=True)
WEB = ROOT / "web"
OLLAMA = os.getenv("OLLAMA_URL", "http://127.0.0.1:11434").rstrip("/")
COMFY = os.getenv("COMFYUI_URL", "http://127.0.0.1:8188").rstrip("/")
MODEL = os.getenv("OLLAMA_MODEL", "mistral-nemo")
FFMPEG = os.getenv("FFMPEG", "ffmpeg")
MAX_FILE = 300 * 1024 * 1024
LOCK = threading.RLock()
TASKS = {}
app = FastAPI(title="SnoPlay Video Studio", version="0.1.0")
app.mount("/web", StaticFiles(directory=WEB), name="web")

class CreateProject(BaseModel):
    title: str = Field(min_length=1, max_length=120)
    prompt: str = Field(min_length=3, max_length=30000)
    seconds: int = Field(ge=5, le=600)
    ratio: str = Field(pattern=r"^(16:9|9:16|1:1)$")
    style: str = Field(default="cinematic", max_length=140)

class ScenePatch(BaseModel):
    scenes: list[dict]

class ComfyConfig(BaseModel):
    workflow: dict
    prompt_node: str = ""
    negative_node: str = ""

def directory(pid: str) -> Path:
    if not re.fullmatch(r"[0-9a-f]{32}", pid):
        raise HTTPException(404, "Unknown project")
    path = DATA / pid
    if not path.is_dir():
        raise HTTPException(404, "Unknown project")
    return path

def read_project(pid: str) -> dict:
    return json.loads((directory(pid) / "project.json").read_text(encoding="utf-8"))

def save_project(p: dict) -> None:
    path = directory(p["id"]) / "project.json"
    tmp = path.with_suffix(".tmp")
    tmp.write_text(json.dumps(p, indent=2, ensure_ascii=False), encoding="utf-8")
    tmp.replace(path)

def ollama_plan(prompt: str, target: int, style: str) -> list[dict] | None:
    count = min(60, max(2, round(target / 8)))
    rules = (
        f"You are a film director. Make EXACTLY {count} sequential shots for "
        f"a {target}-second {style} video. Maintain character, costume and setting continuity. "
        'Return ONLY JSON: {"scenes":[{"title":"short heading","visual":"specific camera and action prompt",'
        '"narration":"one brief voiceover line"}]}. Visuals must be distinct, not repetitive. '
        "No markdown, no extra keys. Film brief:\n" + prompt
    )
    payload = json.dumps({"model": MODEL, "prompt": rules, "stream": False, "format": "json",
                          "options": {"temperature": 0.55, "num_predict": min(8500, count * 125)}}).encode()
    try:
        req = request.Request(OLLAMA + "/api/generate", payload, {"Content-Type": "application/json"})
        with request.urlopen(req, timeout=150) as r:
            txt = json.loads(r.read())["response"]
        data = json.loads(txt)
        scenes = data.get("scenes", [])
        if not isinstance(scenes, list) or len(scenes) < 2:
            return None
        return scenes[:60]
    except (OSError, ValueError, KeyError, TypeError):
        return None

def fallback_plan(prompt: str, target: int, style: str) -> list[dict]:
    count = min(60, max(2, round(target / 8)))
    moves = ["establishing wide shot", "gentle tracking shot", "medium character shot",
             "detailed close-up", "over-the-shoulder view", "slow push-in",
             "atmospheric cutaway", "dramatic side view"]
    return [{"title": f"Scene {i+1}", "visual": f"{style}, {moves[i%len(moves)]}: {prompt}",
             "narration": ""} for i in range(count)]

def distribute_duration(scenes: list, total: int) -> list:
    # Allocate tenths of a second exactly; never silently exceed target duration.
    n = min(len(scenes), max(2, total // 2))
    scenes = scenes[:n]
    units = total * 10
    for i, s in enumerate(scenes):
        share = (units * (i+1) // n) - (units * i // n)
        s["duration"] = round(share / 10, 1)
        s["title"] = str(s.get("title") or f"Scene {i+1}")[:100]
        s["visual"] = str(s.get("visual") or "")[:3000]
        s["narration"] = str(s.get("narration") or "")[:2000]
        s["media"] = None
    return scenes

def ffmpeg_run(args: list[str]):
    result = subprocess.run([FFMPEG, "-hide_banner", "-loglevel", "error", "-y", *args],
                            capture_output=True, text=True)
    if result.returncode:
        raise RuntimeError("FFmpeg failed: " + result.stderr[-1500:])

def font(size: int):
    for name in ["C:/Windows/Fonts/segoeuib.ttf", "C:/Windows/Fonts/arialbd.ttf",
                 "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"]:
        try:
            return ImageFont.truetype(name, size)
        except OSError:
            pass
    return ImageFont.load_default()

def wrap(text: str, max_chars: int = 42) -> list[str]:
    words, lines, line = text.split(), [], ""
    for word in words:
        if len(line) + len(word) + 1 > max_chars and line:
            lines.append(line)
            line = ""
        line = (line + " " + word).strip()
    if line:
        lines.append(line)
    return lines

def title_card(path: Path, title: str, visual: str, ratio: str, idx: int):
    w, h = {"16:9": (1280,720), "9:16": (720,1280), "1:1": (960,960)}[ratio]
    rng = random.Random(idx * 78 + len(visual))
    base = [(13,18,45),(27,12,58),(9,41,49),(35,16,40)][idx%4]
    img = Image.new("RGB", (w,h))
    d = ImageDraw.Draw(img)
    for y in range(h):
        t = y / h
        d.line((0,y,w,y), fill=tuple(min(255,int(c+20*t)) for c in base))
    for _ in range(55):
        x,y = rng.randrange(w),rng.randrange(h)
        r = rng.randrange(1,4)
        d.ellipse((x-r,y-r,x+r,y+r), fill=(80,102,138))
    pad = int(w*.08)
    d.rounded_rectangle((pad,h*.28,w-pad,h*.73),radius=22,fill=(9,12,30),
                        outline=(96,65,152),width=3)
    d.text((pad+25,h*.32),"SNOPLAY  /  VIDEO STUDIO",fill=(70,220,244),font=font(max(17,w//45)))
    d.text((pad+25,h*.41),"\n".join(wrap(title,25)[:3]),font=font(max(26,w//28)),fill=(245,240,255),spacing=7)
    linecount = 3 if ratio == "9:16" else 2
    d.multiline_text((pad+25,h*.59),"\n".join(wrap(visual,43)[:linecount]),
                     font=font(max(14,w//58)), fill=(183,189,221),spacing=5)
    img.save(path)

def output_size(ratio: str):
    return {"16:9": (1280,720), "9:16": (720,1280), "1:1": (960,960)}[ratio]

def job(pid: str, kind: str, fn):
    with LOCK:
        old = TASKS.get(pid, {})
        if old.get("state") == "running":
            raise HTTPException(409, "This project already has a running job")
        TASKS[pid] = {"state": "running", "phase": kind, "progress": 0, "error": ""}
    def runner():
        try:
            fn()
            with LOCK:
                TASKS[pid].update(state="done",progress=100)
        except Exception as exc:
            with LOCK:
                TASKS[pid].update(state="error",error=str(exc)[:1200])
    threading.Thread(target=runner, daemon=True).start()

def render(pid: str):
    p = read_project(pid)
    project_dir = directory(pid)
    work = project_dir / "render_parts"
    work.mkdir(exist_ok=True)
    w,h = output_size(p["ratio"])
    for i, scene in enumerate(p["scenes"]):
        source = project_dir / scene["media"] if scene.get("media") else None
        if not source or not source.is_file():
            source = work / f"card_{i:03d}.png"
            title_card(source,scene["title"],scene["visual"],p["ratio"],i)
        duration = max(0.4,float(scene["duration"]))
        out = work / f"shot_{i:03d}.mp4"
        extension = source.suffix.lower()
        args = ["-stream_loop","-1"] if extension in {".mp4",".mov",".webm",".mkv"} else ["-loop","1"]
        args += ["-i",str(source),"-t",str(duration), "-an",
                 "-vf",f"scale={w}:{h}:force_original_aspect_ratio=increase,crop={w}:{h},fps=24,format=yuv420p",
                 "-c:v","libx264","-preset","veryfast","-crf","23","-movflags","+faststart",str(out)]
        ffmpeg_run(args)
        with LOCK:
            TASKS[pid]["progress"] = round(85*(i+1)/len(p["scenes"]))
            TASKS[pid]["phase"] = f"Rendering scene {i+1}/{len(p['scenes'])}"
    concat = work / "concat.txt"
    concat.write_text("".join("file '"+str((work/f"shot_{i:03d}.mp4").resolve()).replace("'","'\\''")+"'\n"
                              for i in range(len(p["scenes"]))),encoding="utf-8")
    silent = work / "silent.mp4"
    ffmpeg_run(["-f","concat","-safe","0","-i",str(concat),"-c","copy",str(silent)])
    sound = project_dir / p["soundtrack"] if p.get("soundtrack") else None
    voice = project_dir / p["voiceover"] if p.get("voiceover") else None
    sound = sound if sound and sound.is_file() else None
    voice = voice if voice and voice.is_file() else None
    final = project_dir / "movie.mp4"
    with LOCK:
        TASKS[pid]["phase"]="Final assembly"
        TASKS[pid]["progress"]=90
    if sound and voice:
        ffmpeg_run(["-i",str(silent),"-stream_loop","-1","-i",str(sound),"-i",str(voice),
                    "-filter_complex","[1:a]volume=0.18[a1];[a1][2:a]amix=inputs=2:duration=first:dropout_transition=2[a]",
                    "-map","0:v","-map","[a]","-t",str(p["seconds"]),"-c:v","copy",
                    "-c:a","aac","-b:a","192k","-movflags","+faststart",str(final)])
    elif sound or voice:
        source = sound or voice
        ffmpeg_run(["-i",str(silent),"-stream_loop","-1","-i",str(source),
                    "-map","0:v","-map","1:a","-t",str(p["seconds"]),
                    "-c:v","copy","-c:a","aac","-b:a","192k","-movflags","+faststart",str(final)])
    else:
        shutil.copy2(silent,final)

def http_json(url: str, obj=None, timeout=15):
    payload = None if obj is None else json.dumps(obj).encode()
    req = request.Request(url,payload,{"Content-Type":"application/json"} if payload is not None else {})
    with request.urlopen(req,timeout=timeout) as r:
        return json.loads(r.read())

def comfy_generate(pid: str, i: int):
    p = read_project(pid)
    if i < 0 or i >= len(p["scenes"]):
        raise ValueError("Invalid scene")
    config_path = ROOT / "comfy_workflow.json"
    if not config_path.exists():
        raise ValueError("Upload an API-format ComfyUI workflow first")
    conf = json.loads(config_path.read_text())
    workflow = conf["workflow"]
    scene = p["scenes"][i]
    w,h=output_size(p["ratio"])
    def recurse(x):
        if isinstance(x,str):
            return x.replace("{{PROMPT}}",scene["visual"]).replace("{{WIDTH}}",str(w)).replace("{{HEIGHT}}",str(h)).replace("{{FRAMES}}",str(round(scene["duration"]*24)))
        if isinstance(x,list):
            return [recurse(v) for v in x]
        if isinstance(x,dict):
            return {k:recurse(v) for k,v in x.items()}
        return x
    workflow = recurse(workflow)
    node_id=conf.get("prompt_node","")
    if node_id:
        if node_id not in workflow or "text" not in workflow[node_id].get("inputs",{}):
            raise ValueError("The selected prompt node has no text input")
        workflow[node_id]["inputs"]["text"]=scene["visual"]
    else:
        candidates=[node for node in workflow.values()
                    if node.get("class_type")=="CLIPTextEncode" and isinstance(node.get("inputs",{}).get("text"),str)]
        if candidates:
            candidates[0]["inputs"]["text"]=scene["visual"]
    with LOCK:
        TASKS[pid]["phase"]="ComfyUI generating scene"
        TASKS[pid]["progress"]=5
    submission=http_json(COMFY+"/prompt",{"prompt":workflow,"client_id":"snoplay-"+pid},timeout=30)
    prompt_id=submission["prompt_id"]
    history=None
    for tick in range(600):
        time.sleep(2)
        value=http_json(COMFY+"/history/"+parse.quote(prompt_id),timeout=20)
        history=value.get(prompt_id)
        if history:
            break
        with LOCK:
            TASKS[pid]["progress"]=min(92,5+tick//6)
    if not history:
        raise RuntimeError("ComfyUI timed out after 20 minutes")
    output_files=[]
    for entry in history.get("outputs",{}).values():
        for key in ("videos","gifs","images"):
            for f in entry.get(key,[]):
                output_files.append((key,f))
    if not output_files:
        raise RuntimeError("ComfyUI workflow produced no downloadable videos or images")
    key,asset=next(((key,a) for key,a in output_files if key in ("videos","gifs")),output_files[0])
    filename=asset["filename"]
    ext=Path(filename).suffix.lower()
    if ext not in {".mp4",".webm",".mov",".gif",".png",".jpg",".jpeg"}:
        raise RuntimeError("Unsupported ComfyUI output: "+ext)
    url=COMFY+"/view?"+parse.urlencode({"filename":filename,"subfolder":asset.get("subfolder",""),
                                         "type":asset.get("type","output")})
    with request.urlopen(url,timeout=180) as response:
        content=response.read(MAX_FILE+1)
    if len(content)>MAX_FILE:
        raise RuntimeError("ComfyUI video is too large")
    destination=directory(pid)/f"scene_{i:03d}_comfy{ext}"
    destination.write_bytes(content)
    with LOCK:
        p=read_project(pid)
        p["scenes"][i]["media"]=destination.name
        save_project(p)
        TASKS[pid]["progress"]=100

@app.get("/")
def home():
    return FileResponse(WEB/"index.html")

@app.get("/api/health")
def health():
    def reachable(url):
        try:
            with request.urlopen(url,timeout=1.5) as r:
                return r.status==200
        except Exception:
            return False
    return {"ffmpeg": shutil.which(FFMPEG) is not None,"ollama":reachable(OLLAMA+"/api/tags"),
            "comfyui":reachable(COMFY+"/system_stats"),"model":MODEL}

@app.post("/api/projects")
def create(req: CreateProject):
    pid=uuid.uuid4().hex
    path=DATA/pid
    path.mkdir()
    proposed=ollama_plan(req.prompt,req.seconds,req.style)
    scenes=distribute_duration(proposed or fallback_plan(req.prompt,req.seconds,req.style),req.seconds)
    p={"id":pid,"title":req.title,"prompt":req.prompt,"style":req.style,
       "seconds":req.seconds,"ratio":req.ratio,"planner":"ollama" if proposed else "fallback",
       "scenes":scenes,"references":[],"soundtrack":None,"voiceover":None}
    save_project(p)
    return p

@app.get("/api/projects")
def projects():
    result=[]
    for f in sorted(DATA.glob("*/project.json"),key=lambda x:x.stat().st_mtime,reverse=True):
        try:
            p=json.loads(f.read_text(encoding="utf-8"))
            result.append({"id":p["id"],"title":p["title"],"seconds":p["seconds"],"ratio":p["ratio"]})
        except (OSError,KeyError,ValueError):
            continue
    return result

@app.get("/api/projects/{pid}")
def get_project(pid:str):
    return read_project(pid)

@app.put("/api/projects/{pid}/scenes")
def edit_scenes(pid:str, patch:ScenePatch):
    with LOCK:
        p=read_project(pid)
        if len(patch.scenes)!=len(p["scenes"]):
            raise HTTPException(400,"Scene count cannot change in this build")
        allowed={"title","visual","narration","duration"}
        for old,new in zip(p["scenes"],patch.scenes):
            for k in allowed:
                if k in new:
                    old[k]=str(new[k])[:3000] if k!="duration" else max(.4,min(90,float(new[k])))
        p["seconds"]=round(sum(s["duration"] for s in p["scenes"]),1)
        save_project(p)
    return p

async def receive_file(upload:UploadFile, folder:Path, prefix:str, extensions:set[str]):
    original=upload.filename or ""
    ext=Path(original).suffix.lower()
    if ext not in extensions:
        raise HTTPException(400,"Unsupported format; allowed: "+", ".join(sorted(extensions)))
    destination=folder/(prefix+ext)
    size=0
    try:
        with destination.open("wb") as dest:
            while block:=await upload.read(1024*1024):
                size+=len(block)
                if size>MAX_FILE:
                    raise HTTPException(413,"Upload exceeds 300 MB")
                dest.write(block)
    except Exception:
        destination.unlink(missing_ok=True)
        raise
    if not size:
        destination.unlink(missing_ok=True)
        raise HTTPException(400,"Empty file")
    return destination.name

@app.post("/api/projects/{pid}/references")
async def add_reference(pid:str,file:UploadFile=File(...)):
    with LOCK:
        p=read_project(pid)
        if len(p["references"])>=16:
            raise HTTPException(400,"Maximum 16 reference images")
        idx=len(p["references"])
    name=await receive_file(file,directory(pid),f"reference_{idx:02d}",{".png",".jpg",".jpeg",".webp"})
    with LOCK:
        p=read_project(pid)
        p["references"].append(name)
        save_project(p)
    return p

@app.post("/api/projects/{pid}/scenes/{idx}/media")
async def add_scene_media(pid:str,idx:int,file:UploadFile=File(...)):
    with LOCK:
        p=read_project(pid)
        if idx<0 or idx>=len(p["scenes"]):
            raise HTTPException(404,"Scene not found")
    name=await receive_file(file,directory(pid),f"scene_{idx:03d}",{".mp4",".mov",".webm",".mkv",".png",".jpg",".jpeg",".webp"})
    with LOCK:
        p=read_project(pid)
        p["scenes"][idx]["media"]=name
        save_project(p)
    return p

@app.post("/api/projects/{pid}/audio/{kind}")
async def add_audio(pid:str,kind:str,file:UploadFile=File(...)):
    if kind not in {"soundtrack","voiceover"}:
        raise HTTPException(400,"Choose soundtrack or voiceover")
    name=await receive_file(file,directory(pid),kind,{".mp3",".wav",".m4a",".ogg",".flac"})
    with LOCK:
        p=read_project(pid)
        p[kind]=name
        save_project(p)
    return p

@app.post("/api/projects/{pid}/render")
def begin_render(pid:str):
    directory(pid)
    if not shutil.which(FFMPEG):
        raise HTTPException(400,"FFmpeg missing; install FFmpeg and restart the server")
    job(pid,"Preparing",lambda:render(pid))
    return {"started":True}

@app.get("/api/projects/{pid}/status")
def status(pid:str):
    directory(pid)
    with LOCK:
        return dict(TASKS.get(pid,{"state":"idle","phase":"","progress":0,"error":""}))

@app.get("/api/projects/{pid}/video")
def download_video(pid:str):
    f=directory(pid)/"movie.mp4"
    if not f.exists():
        raise HTTPException(404,"Render the project first")
    return FileResponse(f,media_type="video/mp4",filename="SnoPlay-"+pid[:8]+".mp4")

@app.get("/api/projects/{pid}/media/{filename}")
def get_media(pid:str,filename:str):
    if not re.fullmatch(r"[a-zA-Z0-9_.-]+",filename) or filename.startswith("."):
        raise HTTPException(400,"Bad filename")
    f=directory(pid)/filename
    if not f.is_file():
        raise HTTPException(404,"Not found")
    return FileResponse(f)

@app.post("/api/comfyui/config")
def configure_comfy(config:ComfyConfig):
    if len(json.dumps(config.workflow))>5_000_000:
        raise HTTPException(413,"Workflow too large")
    if not any(isinstance(node,dict) and "class_type" in node for node in config.workflow.values()):
        raise HTTPException(400,"Export the ComfyUI workflow in API format")
    (ROOT/"comfy_workflow.json").write_text(json.dumps(config.model_dump()),encoding="utf-8")
    return {"saved":True}

@app.post("/api/projects/{pid}/scenes/{idx}/comfyui")
def start_comfy(pid:str,idx:int):
    p=read_project(pid)
    if idx<0 or idx>=len(p["scenes"]):
        raise HTTPException(404,"Scene not found")
    if not (ROOT/"comfy_workflow.json").exists():
        raise HTTPException(400,"Import a ComfyUI API workflow first")
    job(pid,f"ComfyUI scene {idx+1}",lambda:comfy_generate(pid,idx))
    return {"started":True}

if __name__=="__main__":
    import uvicorn
    uvicorn.run(app,host="127.0.0.1",port=8765)
