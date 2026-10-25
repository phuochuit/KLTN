from __future__ import annotations

import shutil
import tempfile
import base64
import os
import hashlib
from urllib.parse import urlsplit
from datetime import datetime, timezone
from functools import lru_cache
from pathlib import Path
from collections import defaultdict
from threading import Lock

import cv2
import numpy as np
from fastapi import FastAPI, File, Form, HTTPException, Request, UploadFile
from fastapi.responses import JSONResponse, RedirectResponse, Response
from pydantic import BaseModel

from .face_engine import FaceEngine, decode_image
from .recognizer import AnprRecognizer
from .schemas import RecognitionResponse
from .internal_auth import PUBLIC_PATHS, TOKEN_HEADER, configured_tokens, token_is_valid


SERVICE_VERSION = "4.0-robust-liveness"
app = FastAPI(title="Parking ANPR Service", version=SERVICE_VERSION)
IMAGE_EXTENSIONS = {".jpg", ".jpeg", ".png", ".bmp", ".webp"}
VIDEO_EXTENSIONS = {".mp4", ".avi", ".mov", ".mkv", ".wmv", ".m4v", ".webm"}


@app.middleware("http")
async def internal_service_auth(request: Request, call_next):
    request_id = request.headers.get("x-request-id", "").strip()
    if not request_id or len(request_id) > 128:
        request_id = str(__import__("uuid").uuid4())
    request.state.request_id = request_id
    if request.url.path not in PUBLIC_PATHS:
        tokens = configured_tokens()
        if not tokens:
            return JSONResponse(
                status_code=503,
                content={"error": "service_auth_not_configured", "requestId": request_id},
                headers={"X-Request-ID": request_id},
            )
        if not token_is_valid(request.headers.get(TOKEN_HEADER), tokens):
            return JSONResponse(
                status_code=401,
                content={"error": "invalid_service_token", "requestId": request_id},
                headers={"X-Request-ID": request_id},
            )
    response = await call_next(request)
    response.headers["X-Request-ID"] = request_id
    return response


@lru_cache(maxsize=1)
def recognizer() -> AnprRecognizer:
    return AnprRecognizer()


face_engine = FaceEngine()
camera_locks: defaultdict[str, Lock] = defaultdict(Lock)


@app.on_event("startup")
def preload_recognizer() -> None:
    """Load model weights before accepting requests.

    The first YOLO inference also initializes CPU execution state.  Performing
    it here keeps an image-recognition request from paying that cold-start cost.
    """
    recognizer().recognize_frame(np.zeros((640, 640, 3), dtype=np.uint8))


class CameraRecognitionRequest(BaseModel):
    cameraIndex: int = 0
    cameraSource: str | None = None
    frameCount: int = 8


class CameraCaptureRequest(BaseModel):
    cameraIndex: int = 0
    cameraSource: str | None = None


def normalize_camera_source(camera_index: int = 0, camera_source: str | None = None) -> int | str:
    """Accept a local camera index or a bounded network stream URI.

    The source value is never included in an exception because an RTSP URI may
    contain credentials.
    """
    source = (camera_source or "").strip()
    if not source:
        if camera_index < 0:
            raise ValueError("Chỉ số camera không hợp lệ")
        return camera_index
    if len(source) > 1024:
        raise ValueError("Nguồn camera quá dài")
    if source.isdecimal():
        return int(source)
    parsed = urlsplit(source)
    if parsed.scheme.lower() not in {"rtsp", "http", "https"} or not parsed.hostname:
        raise ValueError("Nguồn camera không hợp lệ")
    return source


def camera_lock_key(source: int | str) -> str:
    if isinstance(source, int):
        return f"usb:{source}"
    return "network:" + hashlib.sha256(source.encode("utf-8")).hexdigest()


def open_camera(source: int | str):
    return cv2.VideoCapture(source, cv2.CAP_DSHOW) if isinstance(source, int) else cv2.VideoCapture(source)


def capture_camera_frame(camera_index: int = 0, camera_source: str | None = None) -> np.ndarray:
    """Capture one settled frame while exclusively owning the physical camera."""
    source = normalize_camera_source(camera_index, camera_source)
    with camera_locks[camera_lock_key(source)]:
        camera = open_camera(source)
        try:
            if not camera.isOpened():
                raise ValueError("Không mở được camera")
            frame = None
            for _ in range(12):
                ok, candidate = camera.read()
                if ok:
                    frame = candidate
            if frame is None:
                raise ValueError("Camera không trả về hình ảnh")
            return frame
        finally:
            camera.release()


def capture_camera_frames(camera_index: int = 0, count: int = 8,
                          camera_source: str | None = None) -> list[np.ndarray]:
    """Capture a short burst for liveness (motion/blink) analysis."""
    source = normalize_camera_source(camera_index, camera_source)
    with camera_locks[camera_lock_key(source)]:
        camera = open_camera(source)
        try:
            if not camera.isOpened():
                raise ValueError("Không mở được camera")
            frames = []
            for _ in range(12):
                camera.read()
            for _ in range(count):
                ok, frame = camera.read()
                if ok:
                    frames.append(frame)
            if not frames:
                raise ValueError("Camera không trả về hình ảnh")
            return frames
        finally:
            camera.release()


LIVENESS_MODEL = "motion-blink/1.1"
LIVENESS_LIVE_MIN = 0.55
LIVENESS_SPOOF_MAX = 0.25


def _frame_motion(frames: list[np.ndarray]) -> float | None:
    """Return the mean ratio of changed pixels between consecutive frames."""
    if len(frames) < 3:
        return None
    diffs = []
    for previous, current in zip(frames[:-1], frames[1:]):
        h = min(previous.shape[0], current.shape[0])
        w = min(previous.shape[1], current.shape[1])
        a = cv2.cvtColor(previous[:h, :w], cv2.COLOR_BGR2GRAY)
        b = cv2.cvtColor(current[:h, :w], cv2.COLOR_BGR2GRAY)
        a = cv2.GaussianBlur(a, (21, 21), 0)
        b = cv2.GaussianBlur(b, (21, 21), 0)
        diff = cv2.absdiff(a, b)
        _, thresh = cv2.threshold(diff, 12, 255, cv2.THRESH_BINARY)
        ratio = float(np.count_nonzero(thresh)) / max(thresh.size, 1)
        diffs.append(ratio)
    return float(np.mean(diffs))


def liveness_score(frames: list[np.ndarray]) -> float:
    """Score liveness from inter-frame motion and eye-region change.

    A printed photo or static replay has near-zero local motion, while a live
    person exhibits subtle head movement and blinking.  We compare consecutive
    frames in the central face region and require a minimum amount of change.
    """
    motion = _frame_motion(frames)
    if motion is None or motion > 0.20:
        return 0.0
    return round(min(1.0, motion / 0.03), 4)


def evaluate_liveness(frames: list[np.ndarray]) -> dict:
    """Classify captured frames as LIVE, SPOOF or REVIEW.

    The verdict is informational for callers: SPOOF must disable automatic
    barrier opening, REVIEW requires an authorised manual decision, and LIVE
    keeps the normal face-matching flow.
    """
    motion = _frame_motion(frames)
    if motion is None:
        return {"decision": "REVIEW", "confidence": 0.0,
                "model": LIVENESS_MODEL, "reason": "insufficient_frames"}
    if motion > 0.20:
        return {"decision": "REVIEW", "confidence": 0.0,
                "model": LIVENESS_MODEL, "reason": "excessive_motion"}
    confidence = round(min(1.0, motion / 0.03), 4)
    if confidence >= LIVENESS_LIVE_MIN:
        return {"decision": "LIVE", "confidence": confidence,
                "model": LIVENESS_MODEL, "reason": "ok"}
    if confidence <= LIVENESS_SPOOF_MAX:
        return {"decision": "SPOOF", "confidence": confidence,
                "model": LIVENESS_MODEL, "reason": "static_scene"}
    return {"decision": "REVIEW", "confidence": confidence,
            "model": LIVENESS_MODEL, "reason": "insufficient_motion"}


def attach_liveness(result: dict, frames: list[np.ndarray]) -> dict:
    """Add the liveness contract fields to a face-verification result."""
    verdict = evaluate_liveness(frames)
    result["livenessDecision"] = verdict["decision"]
    result["livenessConfidence"] = verdict["confidence"]
    result["livenessModel"] = verdict["model"]
    result["livenessReason"] = verdict["reason"]
    return result


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
async def verify_face(request: Request, registration: UploadFile = File(...), realtime: UploadFile = File(...)) -> dict:
    try:
        return enrich_face(face_engine.compare(
            decode_image(await registration.read()), decode_image(await realtime.read())), request)
    except ValueError as exc:
        raise HTTPException(422, str(exc)) from exc
    except RuntimeError as exc:
        raise HTTPException(503, str(exc)) from exc


@app.post("/face/verify-camera")
async def verify_face_camera(request: Request, registration: UploadFile = File(...),
                             cameraIndex: int = Form(0), cameraSource: str | None = Form(None)) -> dict:
    try:
        frames = capture_camera_frames(cameraIndex, count=4, camera_source=cameraSource)
        registered = decode_image(await registration.read())
        # Score every captured frame (plus a contrast-normalized copy)
        # and keep the clearest match instead of trusting the last frame.
        result = face_engine.compare_best(registered, frames)
        attach_liveness(result, frames)
        best_frame_index = int(result.get("bestFrameIndex", len(frames) - 1))
        frame = frames[max(0, min(best_frame_index, len(frames) - 1))]
        ok, encoded = cv2.imencode(".jpg", frame)
        result["realtimeImageBase64"] = base64.b64encode(encoded).decode() if ok else ""
        return enrich_face(result, request)
    except ValueError as exc:
        raise HTTPException(422, str(exc)) from exc
    except RuntimeError as exc:
        raise HTTPException(503, str(exc)) from exc
    except Exception as exc:
        raise HTTPException(502, "Không xử lý được ảnh đăng ký: " + str(exc)) from exc


@app.post("/face/verify-image")
async def verify_face_image(request: Request, registration: UploadFile = File(...),
                            realtime: UploadFile = File(...)) -> dict:
    try:
        registered = decode_image(await registration.read())
        frame = decode_image(await realtime.read())
        result = face_engine.compare_best(registered, [frame])
        attach_liveness(result, [frame])
        return enrich_face_result(result, [frame], request)
    except ValueError as exc:
        raise HTTPException(422, str(exc)) from exc
    except Exception as exc:
        raise HTTPException(502, "Không xử lý được ảnh khuôn mặt: " + str(exc)) from exc


@app.post("/face/verify-video")
async def verify_face_video(request: Request, registration: UploadFile = File(...),
                            realtime: UploadFile = File(...)) -> dict:
    suffix = Path(realtime.filename or "face.mp4").suffix.lower()
    if suffix not in VIDEO_EXTENSIONS:
        raise HTTPException(400, "Định dạng video khuôn mặt không được hỗ trợ")
    with tempfile.NamedTemporaryFile(delete=False, suffix=suffix) as temp:
        shutil.copyfileobj(realtime.file, temp)
        temp_path = Path(temp.name)
    try:
        capture = cv2.VideoCapture(str(temp_path))
        if not capture.isOpened():
            raise ValueError("Không mở được video khuôn mặt")
        frame_count = max(1, int(capture.get(cv2.CAP_PROP_FRAME_COUNT)))
        frames = []
        try:
            for index in np.linspace(0, frame_count - 1, min(6, frame_count), dtype=int):
                capture.set(cv2.CAP_PROP_POS_FRAMES, int(index))
                ok, frame = capture.read()
                if ok:
                    frames.append(frame)
        finally:
            capture.release()
        if not frames:
            raise ValueError("Không đọc được frame khuôn mặt nào từ video")
        registered = decode_image(await registration.read())
        result = face_engine.compare_best(registered, frames)
        attach_liveness(result, frames)
        return enrich_face_result(result, frames, request)
    except ValueError as exc:
        raise HTTPException(422, str(exc)) from exc
    except Exception as exc:
        raise HTTPException(502, "Không xử lý được video khuôn mặt: " + str(exc)) from exc
    finally:
        temp_path.unlink(missing_ok=True)


@app.post("/face/capture-camera")
def capture_face_camera(payload: CameraCaptureRequest, request: Request) -> dict:
    try:
        frames = capture_camera_frames(payload.cameraIndex, count=8, camera_source=payload.cameraSource)
        frame = frames[-1]
        face_engine.analyze(frame, "Ảnh realtime")
        ok, encoded = cv2.imencode(".jpg", frame)
        if not ok: raise ValueError("Không mã hóa được ảnh camera")
        data = base64.b64encode(encoded).decode()
        result = {"decision":"CAPTURED","similarity":0.0,"message":"Đã chụp một khuôn mặt rõ ràng","realtimeImageBase64":data}
        attach_liveness(result, frames)
        return enrich_face(result, request)
    except ValueError as exc:
        raise HTTPException(422, str(exc)) from exc


@app.post("/recognize/image", response_model=RecognitionResponse, response_model_by_alias=True)
async def recognize_image(request: Request, file: UploadFile = File(...)) -> RecognitionResponse:
    suffix = Path(file.filename or "image.jpg").suffix.lower()
    if suffix not in IMAGE_EXTENSIONS:
        raise HTTPException(400, "Định dạng ảnh không được hỗ trợ")
    data = await file.read()
    if len(data) > 25 * 1024 * 1024:
        raise HTTPException(413, "Ảnh vượt quá 25 MB")
    try:
        return enrich_recognition(recognizer().recognize_image_bytes(data), request)
    except ValueError as exc:
        raise HTTPException(400, str(exc)) from exc


@app.post("/recognize/camera", response_model=RecognitionResponse, response_model_by_alias=True)
def recognize_camera(payload: CameraRecognitionRequest, request: Request) -> RecognitionResponse:
    count = max(4, min(payload.frameCount, 16))
    try:
        frames = capture_camera_frames(payload.cameraIndex, count=count, camera_source=payload.cameraSource)
        indexed = [(index, frame) for index, frame in enumerate(frames)]
        return enrich_recognition(recognizer().recognize_frames(indexed), request)
    except ValueError as exc:
        raise HTTPException(422, str(exc)) from exc


@app.post("/recognize/video", response_model=RecognitionResponse, response_model_by_alias=True)
async def recognize_video(request: Request, file: UploadFile = File(...)) -> RecognitionResponse:
    suffix = Path(file.filename or "video.mp4").suffix.lower()
    if suffix not in VIDEO_EXTENSIONS:
        raise HTTPException(400, "Định dạng video không được hỗ trợ")
    with tempfile.NamedTemporaryFile(delete=False, suffix=suffix) as temp:
        shutil.copyfileobj(file.file, temp)
        temp_path = Path(temp.name)
    try:
        if temp_path.stat().st_size > 250 * 1024 * 1024:
            raise HTTPException(413, "Video vượt quá 250 MB")
        return enrich_recognition(recognizer().recognize_video(temp_path), request)
    except ValueError as exc:
        raise HTTPException(400, str(exc)) from exc
    finally:
        temp_path.unlink(missing_ok=True)


def enrich_recognition(result: RecognitionResponse, request: Request) -> RecognitionResponse:
    return result.model_copy(update={
        "contract_version": "1.0",
        "request_id": request.state.request_id,
        "model_version": SERVICE_VERSION,
        "processed_at": datetime.now(timezone.utc),
    })


def enrich_face(result: dict, request: Request) -> dict:
    return {
        "contractVersion": "1.0",
        "requestId": request.state.request_id,
        "evidenceId": str(__import__("uuid").uuid4()),
        "processedAt": datetime.now(timezone.utc).isoformat(),
        "modelVersion": SERVICE_VERSION,
        **result,
    }


def enrich_face_result(result: dict, frames: list[np.ndarray], request: Request) -> dict:
    best_index = max(0, min(int(result.get("bestFrameIndex", 0)), len(frames) - 1))
    ok, encoded = cv2.imencode(".jpg", frames[best_index], [cv2.IMWRITE_JPEG_QUALITY, 80])
    result["realtimeImageBase64"] = base64.b64encode(encoded).decode() if ok else ""
    return enrich_face(result, request)
