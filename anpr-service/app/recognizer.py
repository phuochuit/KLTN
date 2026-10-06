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
VIETNAM_PLATE_PATTERN = re.compile(r"^\d{2}[A-Z]{1,2}\d{4,6}$")


def normalize_plate(value: str) -> str:
    return re.sub(r"[^A-Z0-9]", "", (value or "").upper())


def plate_format_score(value: str) -> float:
    """Ưu tiên chuỗi có cấu trúc gần với biển số Việt Nam.

    Hàm chỉ dùng để xếp hạng các kết quả OCR, không tự ý sửa ký tự vì sửa
    đoán có thể biến một biển sai thành biển có vẻ hợp lệ.
    """
    text = normalize_plate(value)
    if VIETNAM_PLATE_PATTERN.fullmatch(text):
        return 0.35
    if 7 <= len(text) <= 10 and text[:2].isdigit():
        return 0.12
    return 0.0


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

    def recognize_video(self, path: Path, max_frames: int = 12) -> RecognitionResponse:
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

                # Early stop for video if we have a highly confident plate
                if best.score > 0.85 and 7 <= len(best.response.plate_text) <= 10:
                    break
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

        # Ảnh cân bằng + khử nhiễu thường tốt nhất với biển số ngoài trời.
        # Chạy nó trước để đa số trường hợp chỉ cần một lượt OCR.
        variants = [denoised, equalized, enlarged]

        best_text, best_conf, best_quality = "", 0.0, -1.0
        with self._lock:
            for variant in variants:
                results = self.ocr.readtext(
                    variant,
                    detail=1,
                    paragraph=False,
                    allowlist="ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789",
                )
                ordered = self._order_ocr_results(results)
                text = normalize_plate("".join(str(item[1]) for item in ordered))
                token_lengths = [max(1, len(normalize_plate(str(item[1])))) for item in ordered]
                confidence = (sum(float(item[2]) * length for item, length in zip(ordered, token_lengths))
                              / sum(token_lengths)) if ordered else 0.0
                plausible_length = 7 <= len(text) <= 10
                format_score = plate_format_score(text)
                quality = confidence + min(len(text), 10) / 50 + format_score
                if text and quality > best_quality:
                    best_text, best_conf, best_quality = text, confidence, quality

                # Dừng ngay khi đã có biển đúng cấu trúc và độ tin cậy tốt.
                # Nhờ vậy ảnh rõ chỉ chạy một biến thể OCR thay vì cả ba.
                if confidence >= 0.82 and format_score >= 0.35:
                    break
        return best_text, best_conf

    @classmethod
    def _order_ocr_results(cls, results: list) -> list:
        """Ghép OCR theo từng dòng rồi từ trái sang phải.

        Cách cũ chia tọa độ Y cho một hằng số 35 nên biển hai dòng có thể bị
        xen ký tự dòng dưới vào dòng trên khi kích thước crop thay đổi.
        """
        if len(results) < 2:
            return list(results)

        items = []
        heights = []
        for item in results:
            points = item[0]
            x, y = cls._box_center(points)
            ys = [float(point[1]) for point in points]
            height = max(ys) - min(ys)
            heights.append(max(1.0, height))
            items.append((x, y, item))

        row_tolerance = max(18.0, float(np.median(heights)) * 0.55)
        rows: list[dict[str, object]] = []
        for x, y, item in sorted(items, key=lambda value: value[1]):
            nearest = min(rows, key=lambda row: abs(y - float(row["center_y"])), default=None)
            if nearest is None or abs(y - float(nearest["center_y"])) > row_tolerance:
                rows.append({"center_y": y, "items": [(x, item)]})
            else:
                row_items = nearest["items"]
                assert isinstance(row_items, list)
                row_items.append((x, item))
                nearest["center_y"] = (float(nearest["center_y"]) * (len(row_items) - 1) + y) / len(row_items)

        ordered = []
        for row in sorted(rows, key=lambda value: float(value["center_y"])):
            row_items = row["items"]
            assert isinstance(row_items, list)
            ordered.extend(item for _, item in sorted(row_items, key=lambda value: value[0]))
        return ordered

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
