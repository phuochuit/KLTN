from __future__ import annotations

import base64
import os
import re
from dataclasses import dataclass
from pathlib import Path
from threading import Lock

import cv2
import easyocr
import numpy as np
from ultralytics import YOLO

from .schemas import BoundingBox, RecognitionResponse


CAR_CLASS = "car"
MOTORCYCLE_CLASSES = {"motorcycle", "motorbike"}


def normalize_plate(value: str) -> str:
    return re.sub(r"[^A-Z0-9]", "", (value or "").upper())


def vehicle_type_from_detections(detections: list[tuple[str, float]]) -> tuple[str, float]:
    best_car = max((score for name, score in detections if name == CAR_CLASS), default=0.0)
    best_motorcycle = max(
        (score for name, score in detections if name in MOTORCYCLE_CLASSES), default=0.0
    )
    if best_car == 0.0 and best_motorcycle == 0.0:
        return "UNKNOWN", 0.0
    return ("CAR", best_car) if best_car >= best_motorcycle else ("MOTORBIKE", best_motorcycle)


@dataclass
class Candidate:
    response: RecognitionResponse
    score: float


class AnprRecognizer:
    def __init__(self) -> None:
        service_dir = Path(__file__).resolve().parents[1]
        plate_model_path = os.getenv(
            "ANPR_PLATE_MODEL", str(service_dir / "models" / "license_plate_detector.pt")
        )
        vehicle_model_path = os.getenv(
            "ANPR_VEHICLE_MODEL", str(service_dir / "models" / "vehicle_detector.pt")
        )
        self.plate_model = YOLO(plate_model_path)
        self.vehicle_model = YOLO(vehicle_model_path)
        self.ocr = easyocr.Reader(
            ["en"], gpu=os.getenv("ANPR_GPU", "false").lower() == "true"
        )
        self.plate_confidence = float(os.getenv("ANPR_PLATE_CONFIDENCE", "0.20"))
        self.vehicle_confidence = float(os.getenv("ANPR_VEHICLE_CONFIDENCE", "0.25"))
        self._lock = Lock()

    def recognize_image_bytes(self, data: bytes, frame_index: int = 0) -> RecognitionResponse:
        image = cv2.imdecode(np.frombuffer(data, np.uint8), cv2.IMREAD_COLOR)
        if image is None:
            raise ValueError("Không đọc được dữ liệu ảnh")
        return self.recognize_frame(image, frame_index)

    def recognize_frame(self, image: np.ndarray, frame_index: int = 0) -> RecognitionResponse:
        with self._lock:
            plate_result = self.plate_model.predict(
                image, conf=self.plate_confidence, imgsz=640, verbose=False
            )[0]
            vehicle_result = self.vehicle_model.predict(
                image, conf=self.vehicle_confidence, imgsz=640, verbose=False,
                classes=[2, 3],
            )[0]

        vehicle_detections = self._detections(vehicle_result)
        vehicle_type, vehicle_conf = vehicle_type_from_detections(vehicle_detections)
        plate_boxes = self._all_boxes(plate_result)
        if not plate_boxes:
            return self._empty_result(
                image,
                vehicle_type,
                vehicle_conf,
                frame_index,
                "YOLO chuyên dụng chưa phát hiện được biển số trong khung hình",
            )

        plate_conf, xyxy = max(plate_boxes, key=lambda item: item[0])
        x1, y1, x2, y2 = self._expand_box(xyxy, image.shape, 0.10)
        if vehicle_type == "UNKNOWN":
            vehicle_type, vehicle_conf = self._vehicle_type_from_plate_shape(xyxy)
        crop = image[y1:y2, x1:x2]
        plate_text, ocr_conf = self._read_plate(crop)

        annotated = image.copy()
        color = (32, 180, 70) if plate_text else (0, 165, 255)
        cv2.rectangle(annotated, (x1, y1), (x2, y2), color, 3)
        label = f"{plate_text or 'PLATE'} | {vehicle_type}"
        cv2.putText(
            annotated,
            label,
            (x1, max(28, y1 - 10)),
            cv2.FONT_HERSHEY_SIMPLEX,
            0.8,
            color,
            2,
            cv2.LINE_AA,
        )
        message = (
            "Nhận dạng thành công"
            if plate_text
            else "Đã cắt biển số nhưng OCR chưa đọc được ký tự"
        )
        return RecognitionResponse(
            plateText=plate_text,
            vehicleType=vehicle_type,
            detectionConfidence=round(plate_conf, 4),
            ocrConfidence=round(ocr_conf, 4),
            vehicleConfidence=round(vehicle_conf, 4),
            boundingBox=BoundingBox(x=x1, y=y1, width=x2 - x1, height=y2 - y1),
            frameIndex=frame_index,
            annotatedImageBase64=self._encode_image(annotated),
            message=message,
        )

    def recognize_video(self, path: Path, max_frames: int = 24) -> RecognitionResponse:
        capture = cv2.VideoCapture(str(path))
        if not capture.isOpened():
            raise ValueError("Không mở được video")
        frame_count = max(1, int(capture.get(cv2.CAP_PROP_FRAME_COUNT)))
        indexes = np.linspace(0, frame_count - 1, min(max_frames, frame_count), dtype=int)
        best: Candidate | None = None
        try:
            for index in indexes:
                capture.set(cv2.CAP_PROP_POS_FRAMES, int(index))
                ok, frame = capture.read()
                if not ok:
                    continue
                response = self.recognize_frame(frame, int(index))
                score = (
                    response.detection_confidence * 0.45
                    + response.ocr_confidence * 0.45
                    + response.vehicle_confidence * 0.10
                )
                if response.plate_text:
                    score += min(len(response.plate_text), 10) / 100
                if best is None or score > best.score:
                    best = Candidate(response, score)
        finally:
            capture.release()
        if best is None:
            raise ValueError("Không đọc được frame nào từ video")
        return best.response

    def _read_plate(self, crop: np.ndarray) -> tuple[str, float]:
        if crop.size == 0:
            return "", 0.0
        scale = max(1.0, 420 / max(1, crop.shape[1]))
        enlarged = cv2.resize(crop, None, fx=scale, fy=scale, interpolation=cv2.INTER_CUBIC)
        gray = cv2.cvtColor(enlarged, cv2.COLOR_BGR2GRAY)
        equalized = cv2.equalizeHist(gray)
        denoised = cv2.bilateralFilter(equalized, 7, 35, 35)
        variants = [enlarged, equalized, denoised]

        best_text, best_conf, best_quality = "", 0.0, -1.0
        with self._lock:
            for variant in variants:
                results = self.ocr.readtext(
                    variant,
                    detail=1,
                    paragraph=False,
                    allowlist="ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789",
                )
                ordered = sorted(
                    results,
                    key=lambda item: (
                        round(self._box_center(item[0])[1] / 35),
                        self._box_center(item[0])[0],
                    ),
                )
                text = normalize_plate("".join(str(item[1]) for item in ordered))
                confidence = (
                    float(np.mean([float(item[2]) for item in ordered])) if ordered else 0.0
                )
                plausible_length = 7 <= len(text) <= 10
                quality = confidence + min(len(text), 10) / 50 + (0.15 if plausible_length else 0.0)
                if text and quality > best_quality:
                    best_text, best_conf, best_quality = text, confidence, quality
        return best_text, best_conf

    @staticmethod
    def _box_center(points: list[list[float]]) -> tuple[float, float]:
        return (
            sum(float(point[0]) for point in points) / len(points),
            sum(float(point[1]) for point in points) / len(points),
        )

    @staticmethod
    def _detections(result: object) -> list[tuple[str, float]]:
        detections: list[tuple[str, float]] = []
        if result.boxes is None:
            return detections
        for box in result.boxes:
            name = str(result.names[int(box.cls.item())]).lower()
            detections.append((name, float(box.conf.item())))
        return detections

    @staticmethod
    def _all_boxes(result: object) -> list[tuple[float, np.ndarray]]:
        boxes: list[tuple[float, np.ndarray]] = []
        if result.boxes is None:
            return boxes
        for box in result.boxes:
            boxes.append(
                (float(box.conf.item()), box.xyxy[0].cpu().numpy().astype(int))
            )
        return boxes

    @staticmethod
    def _vehicle_type_from_plate_shape(box: np.ndarray) -> tuple[str, float]:
        """Fallback for close-up frames where the vehicle body is outside the image.

        Vietnamese motorcycle plates are commonly two-line and nearly square, while
        the usual car plate is wide. Ambiguous ratios remain UNKNOWN.
        """
        x1, y1, x2, y2 = map(float, box)
        aspect_ratio = max(0.0, x2 - x1) / max(1.0, y2 - y1)
        if aspect_ratio <= 1.8:
            return "MOTORBIKE", 0.55
        if aspect_ratio >= 2.6:
            return "CAR", 0.55
        return "UNKNOWN", 0.0

    @staticmethod
    def _expand_box(
        box: np.ndarray, shape: tuple[int, ...], ratio: float
    ) -> tuple[int, int, int, int]:
        x1, y1, x2, y2 = map(int, box)
        dx, dy = int((x2 - x1) * ratio), int((y2 - y1) * ratio)
        height, width = shape[:2]
        return (
            max(0, x1 - dx),
            max(0, y1 - dy),
            min(width, x2 + dx),
            min(height, y2 + dy),
        )

    def _empty_result(
        self,
        image: np.ndarray,
        vehicle_type: str,
        vehicle_conf: float,
        frame_index: int,
        message: str,
    ) -> RecognitionResponse:
        return RecognitionResponse(
            plateText="",
            vehicleType=vehicle_type,
            detectionConfidence=0.0,
            ocrConfidence=0.0,
            vehicleConfidence=round(vehicle_conf, 4),
            boundingBox=None,
            frameIndex=frame_index,
            annotatedImageBase64=self._encode_image(image),
            message=message,
        )

    @staticmethod
    def _encode_image(image: np.ndarray) -> str:
        ok, encoded = cv2.imencode(".jpg", image, [cv2.IMWRITE_JPEG_QUALITY, 88])
        if not ok:
            return ""
        return base64.b64encode(encoded.tobytes()).decode("ascii")
