import os
import sys
import subprocess
import time
import re
import uuid
import gc
import threading

print("=" * 60)
print("🚀 MEMULAI HITUT AI VIDEO BACKEND SERVER (GOOGLE COLAB)")
print("=" * 60)

# 1. Pastikan Dependensi Terpasang
print("\n⏳ [1/4] Memeriksa & memasang dependensi AI...")
subprocess.run([
    sys.executable, "-m", "pip", "install", "-q",
    "diffusers==0.30.3", "transformers==4.44.2", "accelerate",
    "imageio[ffmpeg]", "fastapi", "uvicorn", "pydantic", "Pillow"
], check=True)

# Pasang cloudflared binary langsung ke /usr/local/bin (tanpa dpkg)
if not os.path.exists("/usr/local/bin/cloudflared"):
    print("⏳ Mengunduh Cloudflare Tunnel binary...")
    subprocess.run([
        "curl", "-s", "-L",
        "https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-linux-amd64",
        "-o", "/usr/local/bin/cloudflared"
    ], check=True)
    subprocess.run(["chmod", "+x", "/usr/local/bin/cloudflared"], check=True)

# 2. Patch Transformers & Import Diffusers
import transformers.utils
if not hasattr(transformers.utils, "FLAX_WEIGHTS_NAME"):
    transformers.utils.FLAX_WEIGHTS_NAME = "flax_model.msgpack"

import torch
from fastapi import FastAPI, BackgroundTasks, HTTPException
from fastapi.responses import FileResponse
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel
from typing import Optional, Dict
from diffusers import DiffusionPipeline, DPMSolverMultistepScheduler
from diffusers.utils import export_to_video

# 3. Periksa GPU
print("\n🔍 [2/4] Memeriksa GPU...")
if not torch.cuda.is_available():
    print("⚠️ GPU CUDA tidak aktif. Disarankan pilih Runtime -> Change runtime type -> T4 GPU.")
    device = "cpu"
    dtype = torch.float32
else:
    print(f"✅ GPU Aktif: {torch.cuda.get_device_name(0)}")
    device = "cuda"
    dtype = torch.float16

# 4. Muat Model Video
print("\n🧠 [3/4] Mengunduh & memuat model video (Zeroscope v2)...")
os.makedirs("outputs", exist_ok=True)
model_id = "cerspense/zeroscope_v2_576w"

pipe = DiffusionPipeline.from_pretrained(model_id, torch_dtype=dtype)
pipe.scheduler = DPMSolverMultistepScheduler.from_config(pipe.scheduler.config)
if torch.cuda.is_available():
    pipe.enable_attention_slicing()
    pipe.enable_model_cpu_offload()
print("✅ Model video berhasil dimuat!")

# 5. Bangun Server API
app = FastAPI(title="HITUT AI Video Backend")
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

jobs: Dict[str, dict] = {}
job_lock = threading.Lock()

class TextToVideoRequest(BaseModel):
    prompt: str
    duration_seconds: Optional[int] = 4
    aspect_ratio: Optional[str] = "16:9"

class ImageToVideoRequest(BaseModel):
    image_base64: str
    motion_prompt: str
    duration_seconds: Optional[int] = 4
    aspect_ratio: Optional[str] = "16:9"

@app.get("/health")
def health():
    return {
        "status": "ONLINE",
        "gpu_name": torch.cuda.get_device_name(0) if torch.cuda.is_available() else "CPU",
        "active_jobs": len([j for j in jobs.values() if j["status"] == "PROCESSING"])
    }

def process_video_task(job_id: str, prompt: str, num_frames: int, height: int, width: int):
    with job_lock:
        try:
            jobs[job_id]["status"] = "PROCESSING"
            jobs[job_id]["progress_percent"] = 30
            video_frames = pipe(
                prompt,
                num_inference_steps=20,
                height=height,
                width=width,
                num_frames=num_frames
            ).frames[0]
            jobs[job_id]["progress_percent"] = 85
            out_path = f"outputs/{job_id}.mp4"
            export_to_video(video_frames, out_path, fps=8)
            jobs[job_id]["status"] = "COMPLETED"
            jobs[job_id]["progress_percent"] = 100
            jobs[job_id]["video_url"] = f"/videos/{job_id}.mp4"
        except Exception as e:
            jobs[job_id]["status"] = "FAILED"
            jobs[job_id]["error"] = str(e)
        finally:
            gc.collect()
            if torch.cuda.is_available():
                torch.cuda.empty_cache()

@app.post("/generate-text-to-video")
def generate_text_video(req: TextToVideoRequest, background_tasks: BackgroundTasks):
    job_id = str(uuid.uuid4())
    num_frames = 16 if req.duration_seconds <= 4 else 24
    width, height = (576, 320) if req.aspect_ratio == "16:9" else (320, 576) if req.aspect_ratio == "9:16" else (448, 448)
    jobs[job_id] = {"job_id": job_id, "status": "QUEUED", "progress_percent": 5, "prompt": req.prompt}
    background_tasks.add_task(process_video_task, job_id, req.prompt, num_frames, height, width)
    return {"job_id": job_id, "status": "QUEUED"}

@app.post("/generate-image-to-video")
def generate_image_video(req: ImageToVideoRequest, background_tasks: BackgroundTasks):
    job_id = str(uuid.uuid4())
    num_frames = 16 if req.duration_seconds <= 4 else 24
    width, height = (576, 320) if req.aspect_ratio == "16:9" else (320, 576)
    jobs[job_id] = {"job_id": job_id, "status": "QUEUED", "progress_percent": 5, "prompt": req.motion_prompt}
    background_tasks.add_task(process_video_task, job_id, req.motion_prompt, num_frames, height, width)
    return {"job_id": job_id, "status": "QUEUED"}

@app.get("/status/{job_id}")
def get_status(job_id: str):
    if job_id not in jobs:
        raise HTTPException(status_code=404, detail="Job not found")
    return jobs[job_id]

@app.get("/videos/{filename}")
def get_video_file(filename: str):
    path = f"outputs/{filename}"
    if not os.path.exists(path):
        raise HTTPException(status_code=404, detail="File not found")
    return FileResponse(path, media_type="video/mp4")

# 6. Nyalakan Server & Cloudflare Tunnel
print("\n🌐 [4/4] Membuka terowongan server publik Cloudflare...")
import uvicorn
def run_uvicorn():
    uvicorn.run(app, host="127.0.0.1", port=8000, log_level="warning")

server_thread = threading.Thread(target=run_uvicorn, daemon=True)
server_thread.start()
time.sleep(2)

cf_proc = subprocess.Popen(
    ["/usr/local/bin/cloudflared", "tunnel", "--url", "http://127.0.0.1:8000"],
    stdout=subprocess.PIPE,
    stderr=subprocess.PIPE,
    text=True
)

public_url = ""
for line in cf_proc.stderr:
    match = re.search(r"https://[a-zA-Z0-9-]+\.trycloudflare\.com", line)
    if match:
        public_url = match.group(0)
        break

print("\n" + "=" * 60)
print("🎉 SERVER BACKEND HITUT AI BERHASIL ONLINE!")
print("📌 SALIN LINK INI KE APLIKASI HITUT AI (Menu Backend):")
print(f"👉 {public_url}")
print("=" * 60 + "\n")

cf_proc.wait()
