from __future__ import annotations

import io
import os
from dataclasses import dataclass
from pathlib import Path

import cv2
import numpy as np
from fastapi import FastAPI, File, HTTPException, UploadFile
from fastapi.responses import FileResponse
from fastapi.staticfiles import StaticFiles
from PIL import Image, ImageOps


BASE_DIR = Path(__file__).resolve().parent
MODELS_DIR = BASE_DIR / "models"
STATIC_DIR = BASE_DIR / "static"
YUNET_PATH = MODELS_DIR / "face_detection_yunet_2023mar.onnx"
SFACE_PATH = MODELS_DIR / "face_recognition_sface_2021dec.onnx"

# Ngưỡng tham khảo của OpenCV SFace trên LFW. Khi triển khai thật phải hiệu chỉnh
# lại bằng dữ liệu đúng camera, ánh sáng và nhóm người sử dụng của hệ thống.
MATCH_THRESHOLD = float(os.getenv("FACE_MATCH_THRESHOLD", "0.363"))
REVIEW_THRESHOLD = float(os.getenv("FACE_REVIEW_THRESHOLD", "0.300"))
MAX_UPLOAD_BYTES = 10 * 1024 * 1024

app = FastAPI(title="Demo đối chiếu 3 ảnh khuôn mặt", version="1.0.0")
app.mount("/static", StaticFiles(directory=STATIC_DIR), name="static")


@dataclass
class FaceAnalysis:
    embedding: np.ndarray
    box: list[int]
    detection_score: float
    brightness: float
    sharpness: float
    warnings: list[str]


class FaceEngine:
    def __init__(self) -> None:
        self.detector = None
        self.recognizer = None

    def ensure_loaded(self) -> None:
        if self.detector is not None and self.recognizer is not None:
            return
        missing = [str(path.name) for path in (YUNET_PATH, SFACE_PATH) if not path.exists()]
        if missing:
            raise RuntimeError(
                "Thiếu mô hình: " + ", ".join(missing) + ". Hãy chạy download-models.ps1."
            )
        self.detector = cv2.FaceDetectorYN.create(
            str(YUNET_PATH), "", (320, 320), score_threshold=0.55, nms_threshold=0.30, top_k=5000
        )
        self.recognizer = cv2.FaceRecognizerSF.create(str(SFACE_PATH), "")

    def _detect(self, image: np.ndarray) -> np.ndarray | None:
        height, width = image.shape[:2]
        self.detector.setInputSize((width, height))
        _, faces = self.detector.detect(image)
        return faces

    def analyze(self, image: np.ndarray, label: str) -> FaceAnalysis:
        self.ensure_loaded()
        height, width = image.shape[:2]
        if min(height, width) < 160:
            raise ValueError(f"{label}: ảnh quá nhỏ; cần tối thiểu 160 px mỗi chiều.")

        # Thu anh goc truoc. Neu mat bi chup qua sat mep, them vien trung tinh de
        # YuNet co du ngu canh. Neu van that bai, tang tuong phan cuc bo chi cho
        # buoc detection; vector khuon mat van duoc tao tu anh mau ban dau.
        work_image = image
        offset_x = 0
        offset_y = 0
        faces = self._detect(work_image)
        if faces is None or len(faces) == 0:
            offset_x = max(32, int(round(width * 0.16)))
            offset_y = max(32, int(round(height * 0.16)))
            work_image = cv2.copyMakeBorder(
                image,
                offset_y,
                offset_y,
                offset_x,
                offset_x,
                cv2.BORDER_CONSTANT,
                value=(127, 127, 127),
            )
            faces = self._detect(work_image)

        if faces is None or len(faces) == 0:
            lab = cv2.cvtColor(work_image, cv2.COLOR_BGR2LAB)
            lightness, channel_a, channel_b = cv2.split(lab)
            lightness = cv2.createCLAHE(clipLimit=2.0, tileGridSize=(8, 8)).apply(lightness)
            enhanced = cv2.cvtColor(cv2.merge((lightness, channel_a, channel_b)), cv2.COLOR_LAB2BGR)
            faces = self._detect(enhanced)

        if faces is None or len(faces) == 0:
            raise ValueError(
                f"{label}: chưa phát hiện được khuôn mặt. Hãy để trọn đầu và cằm trong ảnh, "
                "nhìn gần thẳng camera và tránh phản sáng trên kính."
            )
        if len(faces) > 1:
            if label == "Ảnh CCCD":
                # Hoa van, quoc huy hoac chu tren giay to doi khi bi YuNet nhan
                # nham o nguong mem. CCCD chi co mot anh chan dung, nen chon ung
                # vien co ket hop do tin cay va kich thuoc khuon mat cao nhat.
                faces = np.asarray(
                    [max(faces, key=lambda item: float(item[-1]) * np.sqrt(float(item[2] * item[3])))],
                    dtype=np.float32,
                )
            else:
                raise ValueError(f"{label}: phát hiện {len(faces)} khuôn mặt; chỉ được có một người trong ảnh.")

        face = faces[0]
        x, y, w, h = [int(round(value)) for value in face[:4]]
        work_height, work_width = work_image.shape[:2]
        work_x1, work_y1 = max(0, x), max(0, y)
        work_x2, work_y2 = min(work_width, x + w), min(work_height, y + h)
        crop = work_image[work_y1:work_y2, work_x1:work_x2]
        if crop.size == 0:
            raise ValueError(f"{label}: vùng khuôn mặt không hợp lệ.")

        gray = cv2.cvtColor(crop, cv2.COLOR_BGR2GRAY)
        brightness = float(np.mean(gray))
        sharpness = float(cv2.Laplacian(gray, cv2.CV_64F).var())
        warnings: list[str] = []
        if brightness < 55:
            warnings.append("Ảnh khá tối")
        elif brightness > 210:
            warnings.append("Ảnh có nguy cơ cháy sáng")
        if sharpness < 45:
            warnings.append("Khuôn mặt có thể bị mờ")
        if min(w, h) < 90:
            warnings.append("Khuôn mặt chiếm diện tích nhỏ")

        aligned = self.recognizer.alignCrop(work_image, face)
        embedding = self.recognizer.feature(aligned)
        x1 = max(0, work_x1 - offset_x)
        y1 = max(0, work_y1 - offset_y)
        x2 = min(width, work_x2 - offset_x)
        y2 = min(height, work_y2 - offset_y)
        return FaceAnalysis(
            embedding=embedding,
            box=[x1, y1, max(0, x2 - x1), max(0, y2 - y1)],
            detection_score=round(float(face[-1]), 4),
            brightness=round(brightness, 1),
            sharpness=round(sharpness, 1),
            warnings=warnings,
        )

    def similarity(self, first: np.ndarray, second: np.ndarray) -> float:
        score = self.recognizer.match(first, second, cv2.FaceRecognizerSF_FR_COSINE)
        return round(float(score), 4)


engine = FaceEngine()


def decode_image(raw: bytes, label: str) -> np.ndarray:
    if not raw:
        raise ValueError(f"{label}: tệp rỗng.")
    if len(raw) > MAX_UPLOAD_BYTES:
        raise ValueError(f"{label}: ảnh lớn hơn 10 MB.")
    try:
        pil_image = Image.open(io.BytesIO(raw))
        pil_image = ImageOps.exif_transpose(pil_image).convert("RGB")
    except Exception as exc:
        raise ValueError(f"{label}: không đọc được tệp ảnh.") from exc
    return cv2.cvtColor(np.asarray(pil_image), cv2.COLOR_RGB2BGR)


def quality_payload(analysis: FaceAnalysis) -> dict:
    return {
        "box": analysis.box,
        "detectionScore": analysis.detection_score,
        "brightness": analysis.brightness,
        "sharpness": analysis.sharpness,
        "warnings": analysis.warnings,
    }


def classify(score: float) -> str:
    if score >= MATCH_THRESHOLD:
        return "MATCH"
    if score >= REVIEW_THRESHOLD:
        return "REVIEW"
    return "NO_MATCH"


def make_decision(enrollment: float, live_registration: float, live_id: float, has_warning: bool) -> tuple[str, str]:
    # Ảnh đăng ký ↔ CCCD phải hợp lệ từ khâu tạo hồ sơ. Tại cổng, ảnh đăng ký
    # là nguồn chính; CCCD là nguồn đối chứng bổ sung.
    if classify(enrollment) == "NO_MATCH":
        return "REJECT", "Ảnh đăng ký không khớp ảnh CCCD; cần kiểm tra lại hồ sơ cư dân."
    if classify(live_registration) == "NO_MATCH":
        return "REJECT", "Ảnh realtime không khớp ảnh đăng ký."
    if has_warning or "REVIEW" in (classify(enrollment), classify(live_registration), classify(live_id)):
        return "REVIEW", "Kết quả gần ngưỡng hoặc chất lượng ảnh chưa tốt; nhân viên cần kiểm tra/chụp lại."
    if classify(live_id) == "NO_MATCH":
        return "REVIEW", "Realtime khớp ảnh đăng ký nhưng không khớp ảnh CCCD; cần đối chiếu thủ công."
    return "PASS", "Cả ba nguồn ảnh nhất quán theo ngưỡng thử nghiệm."


@app.get("/")
def index() -> FileResponse:
    return FileResponse(STATIC_DIR / "index.html")


@app.get("/api/health")
def health() -> dict:
    models_ready = YUNET_PATH.exists() and SFACE_PATH.exists()
    return {
        "status": "ok" if models_ready else "missing_models",
        "modelsReady": models_ready,
        "matchThreshold": MATCH_THRESHOLD,
        "reviewThreshold": REVIEW_THRESHOLD,
    }


@app.post("/api/compare")
async def compare_faces(
    cccd: UploadFile = File(...),
    registration: UploadFile = File(...),
    realtime: UploadFile = File(...),
) -> dict:
    labels = {"cccd": "Ảnh CCCD", "registration": "Ảnh đăng ký", "realtime": "Ảnh realtime"}
    uploads = {"cccd": cccd, "registration": registration, "realtime": realtime}
    try:
        analyses: dict[str, FaceAnalysis] = {}
        for key, upload in uploads.items():
            if upload.content_type and not upload.content_type.startswith("image/"):
                raise ValueError(f"{labels[key]}: chỉ chấp nhận tệp ảnh.")
            image = decode_image(await upload.read(), labels[key])
            analyses[key] = engine.analyze(image, labels[key])

        enrollment = engine.similarity(analyses["cccd"].embedding, analyses["registration"].embedding)
        live_registration = engine.similarity(
            analyses["realtime"].embedding, analyses["registration"].embedding
        )
        live_id = engine.similarity(analyses["realtime"].embedding, analyses["cccd"].embedding)
        has_warning = any(item.warnings for item in analyses.values())
        decision, message = make_decision(enrollment, live_registration, live_id, has_warning)

        return {
            "decision": decision,
            "message": message,
            "livenessChecked": False,
            "thresholds": {"match": MATCH_THRESHOLD, "review": REVIEW_THRESHOLD},
            "comparisons": {
                "cccdVsRegistration": {"score": enrollment, "status": classify(enrollment)},
                "realtimeVsRegistration": {
                    "score": live_registration,
                    "status": classify(live_registration),
                },
                "realtimeVsCccd": {"score": live_id, "status": classify(live_id)},
            },
            "quality": {key: quality_payload(value) for key, value in analyses.items()},
        }
    except ValueError as exc:
        raise HTTPException(status_code=422, detail=str(exc)) from exc
    except RuntimeError as exc:
        raise HTTPException(status_code=503, detail=str(exc)) from exc
