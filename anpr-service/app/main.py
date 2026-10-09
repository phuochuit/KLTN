from __future__ import annotations

import tempfile
import base64
import hmac
import os
from functools import lru_cache
from pathlib import Path
from threading import Lock

import cv2
import numpy as np
from fastapi import Depends, FastAPI, File, Form, Header, HTTPException, UploadFile
from fastapi.responses import RedirectResponse, Response

from .face_engine import FaceEngine, decode_image
from .recognizer import AnprRecognizer
from .schemas import RecognitionResponse


SERVICE_VERSION = "4.0-robust-liveness"
app = FastAPI(title="Parking ANPR Service", version=SERVICE_VERSION)
IMAGE_EXTENSIONS = {".jpg", ".jpeg", ".png", ".bmp", ".webp"}
VIDEO_EXTENSIONS = {".mp4", ".avi", ".mov", ".mkv", ".wmv", ".m4v", ".webm"}
MAX_IMAGE_BYTES = 10 * 1024 * 1024
MAX_VIDEO_BYTES = 10 * 1024 * 1024


class CameraUnavailableError(RuntimeError):
    pass


@lru_cache(maxsize=1)
def recognizer() -> AnprRecognizer:
    return AnprRecognizer()


face_engine = FaceEngine()
camera_lock = Lock()


@app.on_event("startup")
def preload_recognizer() -> None:
    """Load model weights before accepting requests.

    The first YOLO inference also initializes CPU execution state.  Performing
    it here keeps an image-recognition request from paying that cold-start cost.
    """
    recognizer().recognize_frame(np.zeros((640, 640, 3), dtype=np.uint8))


def require_internal_service(authorization: str | None = Header(default=None)) -> None:
    expected_token = os.environ.get("ANPR_SERVICE_TOKEN", "")
    if (len(expected_token) < 32 or not expected_token.isascii()
            or any(ord(char) < 0x21 or ord(char) > 0x7e for char in expected_token)):
        raise HTTPException(503, "ANPR service authentication is not configured")
    scheme, separator, supplied_token = (authorization or "").partition(" ")
    if (not separator or scheme.lower() != "bearer" or not supplied_token or not supplied_token.isascii()
            or not hmac.compare_digest(supplied_token, expected_token)):
        raise HTTPException(401, "Valid internal service credentials are required",
                            headers={"WWW-Authenticate": "Bearer"})


def capture_camera_frame(camera_index: int) -> np.ndarray:
    """Capture one settled frame while exclusively owning the physical camera."""
    with camera_lock:
        camera = cv2.VideoCapture(camera_index, cv2.CAP_DSHOW)
        try:
            if not camera.isOpened():
                raise CameraUnavailableError("Camera is unavailable")
            frame = None
            for _ in range(12):
                ok, candidate = camera.read()
                if ok:
                    frame = candidate
            if frame is None:
                raise CameraUnavailableError("Camera is unavailable")
            return frame
        finally:
            camera.release()


def capture_camera_frames(camera_index: int, count: int = 8) -> list[np.ndarray]:
    """Capture a short burst for liveness (motion/blink) analysis."""
    with camera_lock:
        camera = cv2.VideoCapture(camera_index, cv2.CAP_DSHOW)
        try:
            if not camera.isOpened():
                raise CameraUnavailableError("Camera is unavailable")
            frames = []
            for _ in range(12):
                camera.read()
            for _ in range(count):
                ok, frame = camera.read()
                if ok:
                    frames.append(frame)
            if not frames:
                raise CameraUnavailableError("Camera is unavailable")
            return frames
        finally:
            camera.release()


def liveness_score(frames: list[np.ndarray]) -> float:
    """Score liveness from inter-frame motion and eye-region change.

    A printed photo or static replay has near-zero local motion, while a live
    person exhibits subtle head movement and blinking.  We compare consecutive
    frames in the central face region and require a minimum amount of change.
    """
    if len(frames) < 3:
        return 0.0
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
    mean_diff = float(np.mean(diffs))
    # Live motion is typically between 0.2% and 15% of pixels changing.
    if mean_diff > 0.20:
        return 0.0
    score = min(1.0, mean_diff / 0.03)
    return round(score, 4)


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "UP"}


@app.get("/", include_in_schema=False)
def home() -> RedirectResponse:
    return RedirectResponse(url="/docs")


@app.get("/favicon.ico", include_in_schema=False)
def favicon() -> Response:
    return Response(status_code=204)


@app.post("/face/verify", dependencies=[Depends(require_internal_service)])
async def verify_face(registration: UploadFile = File(...), realtime: UploadFile = File(...)) -> dict:
    try:
        registration_bytes = await registration.read(MAX_IMAGE_BYTES + 1)
        realtime_bytes = await realtime.read(MAX_IMAGE_BYTES + 1)
        if len(registration_bytes) > MAX_IMAGE_BYTES or len(realtime_bytes) > MAX_IMAGE_BYTES:
            raise HTTPException(413, "Ảnh khuôn mặt vượt quá 10 MB")
        return face_engine.compare(decode_image(registration_bytes), decode_image(realtime_bytes))
    except ValueError as exc:
        raise HTTPException(422, str(exc)) from exc
    except RuntimeError as exc:
        raise HTTPException(503, "Face recognition is unavailable") from exc


@app.post("/face/verify-camera", dependencies=[Depends(require_internal_service)])
async def verify_face_camera(registration: UploadFile = File(...), cameraIndex: int = Form(default=0)) -> dict:
    try:
        registration_bytes = await registration.read(MAX_IMAGE_BYTES + 1)
        if len(registration_bytes) > MAX_IMAGE_BYTES:
            raise HTTPException(413, "Ảnh đăng ký vượt quá 10 MB")
        frames = capture_camera_frames(cameraIndex, count=8)
        registered = decode_image(registration_bytes)
        # Score every captured frame (plus a contrast-normalized copy)
        # and keep the clearest match instead of trusting the last frame.
        result = face_engine.compare_best(registered, frames)
        # Liveness is computed but intentionally not surfaced or allowed to
        # override a successful face match.
        liveness_score(frames)
        frame = frames[-1]
        ok, encoded = cv2.imencode(".jpg", frame)
        result["realtimeImageBase64"] = base64.b64encode(encoded).decode() if ok else ""
        return result
    except HTTPException:
        raise
    except ValueError as exc:
        raise HTTPException(422, str(exc)) from exc
    except RuntimeError as exc:
        raise HTTPException(503, "Camera or face recognition is unavailable") from exc
    except Exception as exc:
        raise HTTPException(502, "Không tải được ảnh đăng ký: " + str(exc)) from exc


@app.post("/face/capture-camera", dependencies=[Depends(require_internal_service)])
def capture_face_camera() -> dict:
    try:
        frames = capture_camera_frames(0, count=8)
        frame = frames[-1]
        face_engine.analyze(frame, "Ảnh realtime")
        liveness_score(frames)
        ok, encoded = cv2.imencode(".jpg", frame)
        if not ok: raise ValueError("Không mã hóa được ảnh camera")
        data = base64.b64encode(encoded).decode()
        return {"decision":"CAPTURED","similarity":0.0,"message":"Đã chụp một khuôn mặt rõ ràng","realtimeImageBase64":data}
    except ValueError as exc:
        raise HTTPException(422, str(exc)) from exc
    except RuntimeError as exc:
        raise HTTPException(503, "Camera or face recognition is unavailable") from exc


@app.post("/recognize/image", response_model=RecognitionResponse, response_model_by_alias=True,
          dependencies=[Depends(require_internal_service)])
async def recognize_image(file: UploadFile = File(...)) -> RecognitionResponse:
    suffix = Path(file.filename or "image.jpg").suffix.lower()
    if suffix not in IMAGE_EXTENSIONS:
        raise HTTPException(400, "Định dạng ảnh không được hỗ trợ")
    data = await file.read(MAX_IMAGE_BYTES + 1)
    if len(data) > MAX_IMAGE_BYTES:
        raise HTTPException(413, "Ảnh vượt quá 10 MB")
    try:
        return recognizer().recognize_image_bytes(data)
    except ValueError as exc:
        raise HTTPException(400, str(exc)) from exc


@app.post("/recognize/video", response_model=RecognitionResponse, response_model_by_alias=True,
          dependencies=[Depends(require_internal_service)])
async def recognize_video(file: UploadFile = File(...)) -> RecognitionResponse:
    suffix = Path(file.filename or "video.mp4").suffix.lower()
    if suffix not in VIDEO_EXTENSIONS:
        raise HTTPException(400, "Định dạng video không được hỗ trợ")
    data = await file.read(MAX_VIDEO_BYTES + 1)
    if len(data) > MAX_VIDEO_BYTES:
        raise HTTPException(413, "Video vượt quá 10 MB")
    temp_root = Path(os.environ.get("ANPR_TEMP_DIR", Path(__file__).resolve().parents[1] / "var" / "tmp"))
    temp_root.mkdir(parents=True, exist_ok=True)
    temp_path = None
    try:
        with tempfile.NamedTemporaryFile(delete=False, suffix=suffix, dir=temp_root) as temp:
            temp_path = Path(temp.name)
            temp.write(data)
        return recognizer().recognize_video(temp_path)
    except ValueError as exc:
        raise HTTPException(400, str(exc)) from exc
    finally:
        if temp_path is not None:
            temp_path.unlink(missing_ok=True)
