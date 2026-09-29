from __future__ import annotations

import shutil
import tempfile
import base64
from functools import lru_cache
from pathlib import Path
from urllib.request import urlopen

import cv2
from fastapi import FastAPI, File, HTTPException, UploadFile
from fastapi.responses import RedirectResponse, Response
from pydantic import BaseModel

from .face_engine import FaceEngine, decode_image
from .recognizer import AnprRecognizer
from .schemas import RecognitionResponse


SERVICE_VERSION = "3.1-face-threshold"
app = FastAPI(title="Parking ANPR Service", version=SERVICE_VERSION)
IMAGE_EXTENSIONS = {".jpg", ".jpeg", ".png", ".bmp", ".webp"}
VIDEO_EXTENSIONS = {".mp4", ".avi", ".mov", ".mkv", ".wmv", ".m4v", ".webm"}


@lru_cache(maxsize=1)
def recognizer() -> AnprRecognizer:
    return AnprRecognizer()


face_engine = FaceEngine()


class CameraFaceRequest(BaseModel):
    registeredImageUrl: str
    cameraIndex: int = 0


@app.get("/health")
def health() -> dict[str, str | bool]:
    return {
        "status": "UP",
        "service": "parking-anpr",
        "version": SERVICE_VERSION,
        "modelLoaded": recognizer.cache_info().currsize > 0,
        "faceModelsReady": face_engine.models_ready,
    }


@app.get("/", include_in_schema=False)
def home() -> RedirectResponse:
    return RedirectResponse(url="/docs")


@app.get("/favicon.ico", include_in_schema=False)
def favicon() -> Response:
    return Response(status_code=204)


@app.post("/face/verify")
async def verify_face(registration: UploadFile = File(...), realtime: UploadFile = File(...)) -> dict:
    try:
        return face_engine.compare(decode_image(await registration.read()), decode_image(await realtime.read()))
    except ValueError as exc:
        raise HTTPException(422, str(exc)) from exc
    except RuntimeError as exc:
        raise HTTPException(503, str(exc)) from exc


@app.post("/face/verify-camera")
def verify_face_camera(request: CameraFaceRequest) -> dict:
    if not request.registeredImageUrl.startswith(("http://localhost:", "http://127.0.0.1:")):
        raise HTTPException(400, "Ảnh đăng ký phải được lấy từ Spring API cục bộ")
    camera = cv2.VideoCapture(request.cameraIndex, cv2.CAP_DSHOW)
    try:
        if not camera.isOpened():
            raise ValueError("Không mở được camera")
        frame = None
        for _ in range(12):
            ok, candidate = camera.read()
            if ok: frame = candidate
        if frame is None: raise ValueError("Camera không trả về hình ảnh")
        registered = decode_image(urlopen(request.registeredImageUrl, timeout=10).read())
        result = face_engine.compare(registered, frame)
        ok, encoded = cv2.imencode(".jpg", frame)
        result["realtimeImageBase64"] = base64.b64encode(encoded).decode() if ok else ""
        return result
    except ValueError as exc:
        raise HTTPException(422, str(exc)) from exc
    except RuntimeError as exc:
        raise HTTPException(503, str(exc)) from exc
    except Exception as exc:
        raise HTTPException(502, "Không tải được ảnh đăng ký: " + str(exc)) from exc
    finally:
        camera.release()


@app.post("/face/capture-camera")
def capture_face_camera() -> dict:
    camera = cv2.VideoCapture(0, cv2.CAP_DSHOW)
    try:
        if not camera.isOpened(): raise ValueError("Không mở được camera")
        frame = None
        for _ in range(12):
            ok, candidate = camera.read()
            if ok: frame = candidate
        if frame is None: raise ValueError("Camera không trả về hình ảnh")
        face_engine.analyze(frame, "Ảnh realtime")
        ok, encoded = cv2.imencode(".jpg", frame)
        if not ok: raise ValueError("Không mã hóa được ảnh camera")
        data = base64.b64encode(encoded).decode()
        return {"decision":"CAPTURED","similarity":0.0,"message":"Đã chụp một khuôn mặt rõ ràng","realtimeImageBase64":data}
    except ValueError as exc:
        raise HTTPException(422, str(exc)) from exc
    finally:
        camera.release()


@app.post("/recognize/image", response_model=RecognitionResponse, response_model_by_alias=True)
async def recognize_image(file: UploadFile = File(...)) -> RecognitionResponse:
    suffix = Path(file.filename or "image.jpg").suffix.lower()
    if suffix not in IMAGE_EXTENSIONS:
        raise HTTPException(400, "Định dạng ảnh không được hỗ trợ")
    data = await file.read()
    if len(data) > 25 * 1024 * 1024:
        raise HTTPException(413, "Ảnh vượt quá 25 MB")
    try:
        return recognizer().recognize_image_bytes(data)
    except ValueError as exc:
        raise HTTPException(400, str(exc)) from exc


@app.post("/recognize/video", response_model=RecognitionResponse, response_model_by_alias=True)
async def recognize_video(file: UploadFile = File(...)) -> RecognitionResponse:
    suffix = Path(file.filename or "video.mp4").suffix.lower()
    if suffix not in VIDEO_EXTENSIONS:
        raise HTTPException(400, "Định dạng video không được hỗ trợ")
    with tempfile.NamedTemporaryFile(delete=False, suffix=suffix) as temp:
        shutil.copyfileobj(file.file, temp)
        temp_path = Path(temp.name)
    try:
        if temp_path.stat().st_size > 250 * 1024 * 1024:
            raise HTTPException(413, "Video vượt quá 250 MB")
        return recognizer().recognize_video(temp_path)
    except ValueError as exc:
        raise HTTPException(400, str(exc)) from exc
    finally:
        temp_path.unlink(missing_ok=True)
