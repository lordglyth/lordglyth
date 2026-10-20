"use strict";
/* SnoPlay Studio: all API calls go to your local server. */
let project=null, polling=null;
const $=s=>document.querySelector(s);
const esc=s=>String(s??"").replace(/[&<>"']/g,c=>({"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;","'":"&#39;"}[c]));
async function api(url,options={}){
  const res=await fetch(url,options),data=await res.json().catch(()=>({}));
  if(!res.ok){let msg=data.detail||"HTTP "+res.status;throw Error(Array.isArray(msg)?msg.map(x=>x.msg).join(", "):msg);}
  return data;
}
function notice(message,error=false){
  const t=$("#toast");t.textContent=message;t.style.background=error?"#5b263a":"#244264";t.classList.add("visible");
  clearTimeout(window._toastDelay);window._toastDelay=setTimeout(()=>t.classList.remove("visible"),5500);
}
async function attempt(task){try{await task()}catch(e){console.error(e);notice(e.message||String(e),true)}}
function loading(b,state,text){
  b.disabled=state;
  if(state){b.dataset.original=b.textContent;b.textContent=text;}
  else if(b.dataset.original){b.textContent=b.dataset.original;delete b.dataset.original;}
}
async function health(){
  try{
    const h=await api("/api/health");
    $("#serviceStatus").textContent="FFmpeg "+(h.ffmpeg?"✓":"✕")+" · Ollama "+(h.ollama?"✓":"—")+" · ComfyUI "+(h.comfyui?"✓":"—");
    $("#serviceStatus").title="Ollama model: "+h.model;
  }catch(e){$("#serviceStatus").textContent="Server unavailable"}
}
async function projects(){
  const p=await api("/api/projects");
  $("#projectList").innerHTML=p.length?p.map(x=>'<button data-open="'+esc(x.id)+'"><strong>'+esc(x.title)+'</strong><small>'+esc(x.seconds)+'s · '+esc(x.ratio)+'</small></button>').join(""):'<span class="muted">No projects yet.</span>';
}
function fileUrl(name){return "/api/projects/"+encodeURIComponent(project.id)+"/media/"+encodeURIComponent(name)}
function references(){
  const r=project?project.references:[];
  $("#refCount").textContent=r.length+" / 16";
  $("#refThumbs").innerHTML=r.map(n=>'<img src="'+fileUrl(n)+'" alt="Reference image" title="'+esc(n)+'">').join("");
}
function scenes(){
  if(!project){$("#scenes").innerHTML='<div class="empty">✧ Your scenes will appear here.</div>';return;}
  $("#storyInfo").textContent=project.scenes.length+" shots · "+project.seconds+" seconds · "+(project.planner==="ollama"?"Ollama-directed":"quick storyboard (Ollama unavailable)")+" · "+project.ratio;
  $("#scenes").innerHTML=project.scenes.map((s,i)=>{
    const choices=project.references.map((n,j)=>'<option value="'+j+'">'+esc(n)+'</option>').join("");
    const reference=project.references.length?'<select data-reference="'+i+'" aria-label="Reference for scene '+(i+1)+'"><option value="">Use a reference photo…</option>'+choices+'</select><button class="secondary" data-use-ref="'+i+'">Use reference</button>':"";
    return '<article class="scene" data-scene="'+i+'"><div class="scene-index">'+String(i+1).padStart(2,"0")+'</div><div><div class="scene-grid">'+
      '<div><label>Scene title</label><input data-field="title" value="'+esc(s.title)+'"></div>'+
      '<div><label>Duration (seconds)</label><input data-field="duration" type="number" min="0.4" max="90" step="0.1" value="'+esc(s.duration)+'"></div>'+
      '<div class="wide"><label>Visual and camera direction</label><textarea data-field="visual" rows="3">'+esc(s.visual)+'</textarea></div>'+
      '<div class="wide"><label>Narration / dialogue script</label><textarea data-field="narration" rows="2">'+esc(s.narration)+'</textarea></div>'+
      '</div><div class="scene-buttons">'+
      '<input aria-label="Media for scene '+(i+1)+'" data-upload="'+i+'" type="file" accept="image/*,video/mp4,video/webm,video/quicktime">'+
      '<button class="secondary" data-attach="'+i+'">Attach media</button>'+reference+
      '<button class="secondary" data-comfy="'+i+'">✦ Generate via ComfyUI</button>'+
      (s.media?'<span class="attached">✓ '+esc(s.media)+'</span>':'<span class="muted">Title card until media is added</span>')+
      '</div></div></article>';
  }).join("");
}
function show(){
  references();scenes();$("#videoDownload").classList.add("disabled");
  $("#preview").hidden=true;$("#renderState").textContent="Project loaded. Render whenever ready.";
}
function edits(){
  return [...document.querySelectorAll("[data-scene]")].map(n=>{
    const v={};n.querySelectorAll("[data-field]").forEach(el=>v[el.dataset.field]=el.value);return v;
  });
}
async function save(silent=false){
  if(!project)throw Error("Create a project first.");
  project=await api("/api/projects/"+project.id+"/scenes",{method:"PUT",headers:{"Content-Type":"application/json"},body:JSON.stringify({scenes:edits()})});
  if(!silent){scenes();notice("Scene edits saved.");}
}
async function load(pid){
  project=await api("/api/projects/"+pid);
  $("#projectTitle").value=project.title;$("#prompt").value=project.prompt;
  $("#ratio").value=project.ratio;$("#style").value=project.style;
  if([...$("#duration").options].some(o=>o.value===String(project.seconds)))$("#duration").value=project.seconds;
  show();await projects();$("#storyboard").scrollIntoView({behavior:"smooth"});
}
async function create(e){
  e.preventDefault();const b=$("#planButton");loading(b,true,"✦ Planning with local AI…");
  try{
    project=await api("/api/projects",{method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify({
      title:$("#projectTitle").value,prompt:$("#prompt").value,seconds:Number($("#duration").value),
      ratio:$("#ratio").value,style:$("#style").value
    })});
    show();await projects();$("#storyboard").scrollIntoView({behavior:"smooth"});
    notice(project.scenes.length+" shots created using "+project.planner+".");
  }finally{loading(b,false)}
}
async function upload(url,file){
  if(!file)throw Error("Select a file first.");
  const form=new FormData();form.append("file",file);
  return api(url,{method:"POST",body:form});
}
async function addRefs(){
  if(!project)throw Error("Create a project first.");
  const files=[...$("#refFiles").files];if(!files.length)throw Error("Choose reference photos.");
  for(const f of files)project=await upload("/api/projects/"+project.id+"/references",f);
  references();scenes();$("#refFiles").value="";notice(files.length+" reference photos saved locally.");
}
async function attach(i){
  project=await upload("/api/projects/"+project.id+"/scenes/"+i+"/media",$('[data-upload="'+i+'"]').files[0]);
  scenes();notice("Media attached to shot "+(i+1));
}
async function applyRef(i){
  const value=$('[data-reference="'+i+'"]').value;
  if(value==="")throw Error("Choose a reference photo first.");
  const filename=project.references[Number(value)],response=await fetch(fileUrl(filename));
  if(!response.ok)throw Error("Reference could not be loaded.");
  const blob=await response.blob();
  project=await upload("/api/projects/"+project.id+"/scenes/"+i+"/media",new File([blob],filename,{type:blob.type}));
  scenes();notice("Reference assigned to shot "+(i+1));
}
async function sound(kind,selector){
  if(!project)throw Error("Create a project first.");
  project=await upload("/api/projects/"+project.id+"/audio/"+kind,$(selector).files[0]);
  notice(kind+" uploaded.");
}
async function workflow(){
  const file=$("#workflowFile").files[0];if(!file)throw Error("Choose an API-format ComfyUI JSON workflow.");
  const body={workflow:JSON.parse(await file.text()),prompt_node:$("#promptNode").value.trim()};
  await api("/api/comfyui/config",{method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify(body)});
  notice("ComfyUI workflow configured.");
}
async function generate(i){
  if(!project)throw Error("Create a project first.");
  await save(true);
  await api("/api/projects/"+project.id+"/scenes/"+i+"/comfyui",{method:"POST"});
  notice("AI shot queued in ComfyUI.");watch();$("#assembly").scrollIntoView({behavior:"smooth"});
}
function watch(){
  if(polling)clearInterval(polling);
  const tick=async()=>{
    if(!project)return;
    try{
      const s=await api("/api/projects/"+project.id+"/status");
      $("#progressFill").style.width=s.progress+"%";
      $("#renderState").textContent=s.state==="error"?"Error: "+s.error:s.state==="running"?s.phase+" · "+s.progress+"%":s.state==="done"?"Finished.":"Ready.";
      if(s.state!=="running"){
        clearInterval(polling);polling=null;
        if(s.state==="done"){
          project=await api("/api/projects/"+project.id);scenes();
          const url="/api/projects/"+project.id+"/video";
          if((await fetch(url,{method:"HEAD"})).ok){
            $("#videoDownload").href=url;$("#videoDownload").classList.remove("disabled");
            $("#preview").src=url+"?v="+Date.now();$("#preview").hidden=false;
          }
        }else if(s.state==="error")notice(s.error,true);
      }
    }catch(e){console.error(e)}
  };
  tick();polling=setInterval(tick,1800);
}
async function render(){
  if(!project)throw Error("Create a project first.");
  await save(true);
  await api("/api/projects/"+project.id+"/render",{method:"POST"});
  $("#videoDownload").classList.add("disabled");notice("Rendering your full film.");watch();
}
$("#createForm").addEventListener("submit",e=>attempt(()=>create(e)));
$("#reloadProjects").addEventListener("click",()=>attempt(projects));
$("#projectList").addEventListener("click",e=>{
  const b=e.target.closest("[data-open]");if(b)attempt(()=>load(b.dataset.open));
});
$("#uploadRefs").addEventListener("click",()=>attempt(addRefs));
$("#saveScenes").addEventListener("click",()=>attempt(()=>save()));
$("#uploadMusic").addEventListener("click",()=>attempt(()=>sound("soundtrack","#musicFile")));
$("#uploadVoice").addEventListener("click",()=>attempt(()=>sound("voiceover","#voiceFile")));
$("#uploadWorkflow").addEventListener("click",()=>attempt(workflow));
$("#renderButton").addEventListener("click",()=>attempt(render));
$("#scenes").addEventListener("click",e=>{
  const a=e.target.closest("[data-attach]"),r=e.target.closest("[data-use-ref]"),c=e.target.closest("[data-comfy]");
  if(a)attempt(()=>attach(Number(a.dataset.attach)));
  else if(r)attempt(()=>applyRef(Number(r.dataset.useRef)));
  else if(c)attempt(()=>generate(Number(c.dataset.comfy)));
});
health();attempt(projects);
