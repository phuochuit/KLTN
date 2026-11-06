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
BICYCLE_CLASSES = {"bicycle", "bike"}
VIETNAM_PLATE_PATTERN = re.compile(r"^\d{2}[A-Z]{1,2}\d{4,6}$")
STANDARD_PLATE_WITH_EXTRA_LETTER = re.compile(r"^(\d{2})[A-Z]([A-Z]\d{6})$")
STANDARD_PLATE_WITH_EXTRA_DIGIT = re.compile(r"^(\d{2})\d([A-Z]{1,2}\d{4,6})$")
OCR_DIGIT_CONFUSIONS = str.maketrans({"O": "0", "Q": "0", "I": "1", "L": "1", "Z": "2", "S": "5", "G": "6", "B": "8"})
OCR_LETTER_CONFUSIONS = str.maketrans({"0": "O", "1": "I", "2": "Z", "5": "S", "6": "G", "8": "B"})


def normalize_plate(value: str) -> str:
    return re.sub(r"[^A-Z0-9]", "", (value or "").upper())


def canonicalize_plate(value: str) -> str:
    """Normalize a standard Vietnamese plate and remove one OCR-only prefix letter.

    Resident motorcycle/car plates handled by this system use a two-digit province
    code followed by one series letter. EasyOCR can hallucinate a letter between
    the province code and the real series (for example 59HF241578). In the narrow
    two-letter + six-digit shape, discard only that inserted first letter.
    """
    text = normalize_plate(value)
    # Correct only position-safe OCR confusions. The first two and final four
    # to six characters are numeric; the series between them is alphabetic.
    # Do not guess between genuinely numeric glyphs (for example 2 versus 7).
    if 7 <= len(text) <= 10 and not VIETNAM_PLATE_PATTERN.fullmatch(text):
        prefix = text[:2].translate(OCR_DIGIT_CONFUSIONS)
        suffix_start = next(
            (index for index in range(3, len(text)) if text[index:].isalnum()
             and len(text) - index in (4, 5, 6)),
            max(3, len(text) - 5),
        )
        series = text[2:suffix_start].translate(OCR_LETTER_CONFUSIONS)
        suffix = text[suffix_start:].translate(OCR_DIGIT_CONFUSIONS)
        corrected = prefix + series + suffix
        if VIETNAM_PLATE_PATTERN.fullmatch(corrected):
            text = corrected
    match = STANDARD_PLATE_WITH_EXTRA_LETTER.fullmatch(text)
    if match:
        return "".join(match.groups())
    # OCR đôi khi lấy một nét/đinh trên khung biển thành chữ số ngay sau mã
    # tỉnh (ví dụ 594F241578). Chỉ bỏ đúng một chữ số ở vị trí vốn bắt buộc
    # phải là sê-ri chữ và chỉ khi phần còn lại tạo thành biển hợp lệ.
    match = STANDARD_PLATE_WITH_EXTRA_DIGIT.fullmatch(text)
    return "".join(match.groups()) if match else text


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


def plate_shape_is_plausible(value: str, aspect_ratio: float) -> bool:
    """Reject incomplete OCR results for square two-line motorcycle plates."""
    text = normalize_plate(value)
    if not VIETNAM_PLATE_PATTERN.fullmatch(text):
        return False
    # A two-line plate normally contains at least the province, series and
    # four trailing digits. Seven-character readings indicate that OCR has
    # dropped part of one row (for example 59A1122 instead of 59A3/11122).
    return not (aspect_ratio <= 1.8 and len(text) < 8)


def select_plate_candidate(votes: dict[str, list[float]]) -> tuple[str, float]:
    """Balance OCR confidence with agreement across image variants.

    A small agreement bonus lets several independent, moderately confident
    readings beat one isolated mistake. It is deliberately capped so two weak
    duplicate readings cannot replace one very clear result.
    """
    if not votes:
        return "", 0.0

    def score(value: str) -> tuple[float, float, int]:
        confidences = votes[value]
        strongest = max(confidences)
        agreement_bonus = 0.12 * min(max(len(confidences) - 1, 0), 3)
        return strongest + agreement_bonus, strongest, len(confidences)

    selected = max(votes, key=score)
    return selected, max(votes[selected])


def vehicle_type_from_detections(detections: list[tuple[str, float]]) -> tuple[str, float]:
    best_car = max((score for name, score in detections if name == CAR_CLASS), default=0.0)
    best_motorcycle = max(
        (score for name, score in detections if name in MOTORCYCLE_CLASSES), default=0.0
    )
    best_bicycle = max(
        (score for name, score in detections if name in BICYCLE_CLASSES), default=0.0
    )
    if best_car == 0.0 and best_motorcycle == 0.0 and best_bicycle == 0.0:
        return "UNKNOWN", 0.0
    candidates = [
        ("CAR", best_car),
        ("MOTORBIKE", best_motorcycle),
        ("BICYCLE_ELECTRIC_BICYCLE", best_bicycle),
    ]
    return max(candidates, key=lambda item: item[1])


def best_two_wheeler_type(detections: list[tuple[str, float]]) -> tuple[str, float]:
    """Chọn nhóm xe hai bánh khi biển số đã loại trừ khả năng ô tô."""
    best_motorcycle = max(
        (score for name, score in detections if name in MOTORCYCLE_CLASSES), default=0.0
    )
    best_bicycle = max(
        (score for name, score in detections if name in BICYCLE_CLASSES), default=0.0
    )
    if best_bicycle >= best_motorcycle:
        return "BICYCLE_ELECTRIC_BICYCLE", best_bicycle
    return "MOTORBIKE", best_motorcycle


def vehicle_type_near_plate(
    detections: list[tuple[str, float, np.ndarray]], plate_box: np.ndarray
) -> tuple[str, float]:
    """Classify only the vehicle spatially associated with the detected plate.

    Hình dạng biển số Việt Nam là bằng chứng đáng tin hơn lớp do mô hình đoán:
    ô tô dùng biển dài và biển chỉ chiếm một dải nhỏ trên thân xe, còn xe hai
    bánh dùng biển vuông hoặc biển chiếm phần lớn chiều ngang thân xe. Khi mô
    hình đoán "ô tô" nhưng hình học biển số nói ngược lại, chọn nhóm hai bánh
    để nhân viên không phải sửa lại từ đầu.
    """
    px1, py1, px2, py2 = map(float, plate_box)
    center_x, center_y = (px1 + px2) / 2, (py1 + py2) / 2
    nearby: list[tuple[str, float, np.ndarray]] = []
    for name, score, box in detections:
        x1, y1, x2, y2 = map(float, box)
        margin_x = max(8.0, (x2 - x1) * 0.08)
        margin_y = max(8.0, (y2 - y1) * 0.08)
        if x1 - margin_x <= center_x <= x2 + margin_x and y1 - margin_y <= center_y <= y2 + margin_y:
            nearby.append((name, score, box))

    if not nearby:
        return "UNKNOWN", 0.0
    summary = [(name, score) for name, score, _ in nearby]
    vehicle_type, confidence = vehicle_type_from_detections(summary)
    plate_ratio = max(0.0, px2 - px1) / max(1.0, py2 - py1)
    vehicle_box = max(nearby, key=lambda item: item[1])[2]
    vehicle_width = max(1.0, float(vehicle_box[2]) - float(vehicle_box[0]))
    plate_span = max(0.0, px2 - px1) / vehicle_width

    # Biển vuông hoặc biển rộng gần bằng thân xe: ô tô không có kiểu biển này.
    if vehicle_type == "CAR" and (plate_ratio <= 1.8 or plate_span >= 0.45):
        two_wheeler_type, two_wheeler_conf = best_two_wheeler_type(summary)
        if two_wheeler_conf > 0.0:
            return two_wheeler_type, two_wheeler_conf
        return "UNKNOWN", 0.0
    # Biển dài và nhỏ so với thân xe: gần như chắc chắn là ô tô.
    if vehicle_type != "CAR" and plate_ratio >= 2.6 and plate_span <= 0.30:
        return "CAR", max(confidence, 0.5)
    return vehicle_type, confidence


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
        self.ready = False

    def warm_up(self) -> None:
        """Initialize YOLO and EasyOCR execution before serving gate requests."""
        blank = np.zeros((640, 640, 3), dtype=np.uint8)
        self.recognize_frame(blank)

        # A blank frame never reaches OCR because YOLO finds no plate. Run OCR
        # once on a synthetic high-contrast plate so the first real vehicle does
        # not pay PyTorch/EasyOCR's lazy CPU initialization cost.
        synthetic_plate = np.full((140, 420, 3), 255, dtype=np.uint8)
        cv2.putText(synthetic_plate, "59F2", (18, 58), cv2.FONT_HERSHEY_SIMPLEX,
                    1.6, (0, 0, 0), 3, cv2.LINE_AA)
        cv2.putText(synthetic_plate, "41578", (65, 122), cv2.FONT_HERSHEY_SIMPLEX,
                    1.6, (0, 0, 0), 3, cv2.LINE_AA)
        self._read_plate(synthetic_plate)
        self.ready = True

    def recognize_image_bytes(self, data: bytes, frame_index: int = 0) -> RecognitionResponse:
        image = cv2.imdecode(np.frombuffer(data, np.uint8), cv2.IMREAD_COLOR)
        if image is None:
            raise ValueError("Không đọc được dữ liệu ảnh")
        return self.recognize_frame(image, frame_index)

    def recognize_frame(self, image: np.ndarray, frame_index: int = 0,
                        plate_result=None) -> RecognitionResponse:
        with self._lock:
            if plate_result is None:
                plate_result = self.plate_model.predict(
                    image, conf=self.plate_confidence, imgsz=640, verbose=False
                )[0]
            vehicle_result = self.vehicle_model.predict(
                image, conf=self.vehicle_confidence, imgsz=640, verbose=False,
                classes=[1, 2, 3],
            )[0]

        vehicle_detections = self._vehicle_detections(vehicle_result)
        vehicle_type, vehicle_conf = vehicle_type_from_detections(
            [(name, score) for name, score, _ in vehicle_detections]
        )
        plate_boxes = self._all_boxes(plate_result)
        if not plate_boxes:
            return self._empty_result(
                image,
                vehicle_type,
                vehicle_conf,
                frame_index,
                "Chưa tìm thấy biển số trong ảnh; vui lòng chụp lại hoặc nhập biển số.",
            )

        plate_conf, xyxy = max(plate_boxes, key=lambda item: item[0])
        vehicle_type, vehicle_conf = vehicle_type_near_plate(vehicle_detections, xyxy)
        if vehicle_type == "UNKNOWN":
            # Khung hình camera thường rộng: dò lại ở độ phân giải cao hơn để
            # bắt được xe nhỏ (xe đạp, xe đạp điện) mà lượt dò đầu bỏ sót.
            extra_detections = self._vehicle_detections_at(image, 960)
            if extra_detections:
                vehicle_type, vehicle_conf = vehicle_type_near_plate(
                    vehicle_detections + extra_detections, xyxy
                )
        x1, y1, x2, y2 = self._expand_box(xyxy, image.shape, 0.10)
        if vehicle_type == "UNKNOWN":
            vehicle_type, vehicle_conf = self._vehicle_type_from_plate_shape(xyxy)
        plate_width = max(1, int(xyxy[2]) - int(xyxy[0]))
        plate_height = max(1, int(xyxy[3]) - int(xyxy[1]))
        # Biển dài cần giữ đúng mép khung phát hiện. Việc thêm viền đưa các
        # chi tiết nền vào ảnh và từng làm 30F55775 bị đọc thành 80F55275.
        ocr_margin = 0.0 if plate_width / plate_height >= 2.6 else 0.10
        ox1, oy1, ox2, oy2 = self._expand_box(xyxy, image.shape, ocr_margin)
        crop = image[oy1:oy2, ox1:ox2]
        tx1, ty1, tx2, ty2 = self._expand_box(xyxy, image.shape, 0.0)
        tight_crop = image[ty1:ty2, tx1:tx2]
        from .quality import assess_plate
        plate_quality = assess_plate(image, (x1, y1, x2 - x1, y2 - y1))
        plate_text, ocr_conf = self._read_plate(crop, tight_crop)

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
            else "Biển số chưa rõ; vui lòng chụp lại hoặc nhập biển số."
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
            quality=plate_quality,
            message=message,
        )

    def recognize_video(self, path: Path, max_frames: int = 12, finalist_count: int = 3) -> RecognitionResponse:
        capture = cv2.VideoCapture(str(path))
        if not capture.isOpened():
            raise ValueError("Không mở được video")
        frame_count = max(1, int(capture.get(cv2.CAP_PROP_FRAME_COUNT)))
        indexes = np.linspace(0, frame_count - 1, min(max_frames, frame_count), dtype=int)
        frames: list[tuple[int, np.ndarray]] = []
        try:
            for index in indexes:
                capture.set(cv2.CAP_PROP_POS_FRAMES, int(index))
                ok, frame = capture.read()
                if not ok:
                    continue
                frames.append((int(index), frame))
        finally:
            capture.release()

        return self.recognize_frames(frames, finalist_count)

    def recognize_frames(self, frames: list[tuple[int, np.ndarray]], finalist_count: int = 3) -> RecognitionResponse:
        """Select sharp plate candidates, then run expensive OCR only on finalists."""
        finalists: list[tuple[float, int, np.ndarray, object]] = []
        for index, frame in frames:
            with self._lock:
                plate_result = self.plate_model.predict(
                    frame, conf=self.plate_confidence, imgsz=640, verbose=False
                )[0]
            plate_confidence = max((score for score, _ in self._all_boxes(plate_result)), default=0.0)
            gray = cv2.cvtColor(frame, cv2.COLOR_BGR2GRAY)
            sharpness = min(float(cv2.Laplacian(gray, cv2.CV_64F).var()) / 1000.0, 1.0)
            finalists.append((plate_confidence * 0.9 + sharpness * 0.1, index, frame, plate_result))

        if not finalists:
            raise ValueError("Không đọc được frame nào để nhận dạng")

        best: Candidate | None = None
        for _, index, frame, plate_result in sorted(finalists, key=lambda item: item[0], reverse=True)[:max(1, finalist_count)]:
            response = self.recognize_frame(frame, index, plate_result=plate_result)
            score = (
                response.detection_confidence * 0.45
                + response.ocr_confidence * 0.45
                + response.vehicle_confidence * 0.10
            )
            if response.plate_text:
                score += min(len(response.plate_text), 10) / 100
            if best is None or score > best.score:
                best = Candidate(response, score)

            # Early stop for video if we have a highly confident plate.
            if best.score > 0.85 and 7 <= len(best.response.plate_text) <= 10:
                break
        if best is None:
            raise ValueError("Không phân tích được frame ứng viên từ video")
        return best.response

    def _read_plate(self, crop: np.ndarray, tight_crop: np.ndarray | None = None) -> tuple[str, float]:
        if crop.size == 0:
            return "", 0.0
        scale = max(1.0, 420 / max(1, crop.shape[1]))
        enlarged = cv2.resize(crop, None, fx=scale, fy=scale, interpolation=cv2.INTER_CUBIC)
        from .quality import deskew
        straightened = deskew(enlarged)
        gray = cv2.cvtColor(straightened, cv2.COLOR_BGR2GRAY)
        equalized = cv2.equalizeHist(gray)
        denoised = cv2.bilateralFilter(equalized, 7, 35, 35)
        sharpened = cv2.filter2D(
            denoised, -1, np.array([[0, -1, 0], [-1, 5, -1], [0, -1, 0]], dtype=np.float32)
        )
        adaptive = cv2.adaptiveThreshold(
            denoised, 255, cv2.ADAPTIVE_THRESH_GAUSSIAN_C,
            cv2.THRESH_BINARY, 31, 7,
        )

        # Wide Vietnamese car plates respond much better to a tightly cropped,
        # 2x enlarged inverse Otsu image. In particular this preserves the open
        # shape of "3", which EasyOCR otherwise tends to read as "B"/"8".
        raw_gray = cv2.cvtColor(crop, cv2.COLOR_BGR2GRAY)
        raw_enlarged_2x = cv2.resize(
            raw_gray, None, fx=2.0, fy=2.0, interpolation=cv2.INTER_CUBIC
        )
        raw_otsu_inverted = cv2.threshold(
            raw_enlarged_2x, 0, 255, cv2.THRESH_BINARY_INV + cv2.THRESH_OTSU
        )[1]

        # Biển vuông hai dòng: khung mở rộng giúp đọc chữ nhưng có thể kéo cả
        # nền vào và làm sai chữ số; biển đúng khung phát hiện phóng 2x + Otsu
        # thường đọc ổn định nhất nên thử trước.
        crop_aspect_ratio = crop.shape[1] / max(1, crop.shape[0])
        if crop_aspect_ratio >= 2.6:
            variants = [raw_otsu_inverted, straightened, enlarged, equalized, sharpened, adaptive]
        else:
            variants = [equalized, denoised, straightened, sharpened, adaptive]
            tight_otsu = self._tight_otsu(tight_crop)
            if tight_otsu is not None:
                variants.insert(0, tight_otsu)

        # Nhiều biến thể có thể cùng đọc ra một biển: đó là bằng chứng mạnh hơn
        # một lượt đọc có độ tin cậy cao nhưng lẻ loi. Cách này sửa được các ca
        # "17Z..." từng thắng nhờ tin cậy cao trong khi 4 biến thể khác đều đọc
        # đúng "47B...".
        votes: dict[str, list[float]] = {}
        best_text, best_conf, best_quality = "", 0.0, -1.0
        with self._lock:
            for variant in variants:
                results = self.ocr.readtext(
                    variant,
                    detail=1,
                    paragraph=False,
                    mag_ratio=1.0,
                    allowlist="ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789",
                )
                ordered = self._order_ocr_results(results)
                text = canonicalize_plate("".join(str(item[1]) for item in ordered))
                token_lengths = [max(1, len(normalize_plate(str(item[1])))) for item in ordered]
                confidence = (sum(float(item[2]) * length for item, length in zip(ordered, token_lengths))
                              / sum(token_lengths)) if ordered else 0.0
                format_score = plate_format_score(text)
                if format_score < 0.35 or not plate_shape_is_plausible(text, crop_aspect_ratio):
                    continue
                votes.setdefault(text, []).append(confidence)
                quality = confidence + format_score
                if quality > best_quality:
                    best_text, best_conf, best_quality = text, confidence, quality

                # Một kết quả đơn lẻ có thể rất tự tin nhưng vẫn nhầm một chữ
                # số. Chỉ dừng sớm khi hai biến thể cùng đọc ra một biển và ít
                # nhất một lượt đủ rõ; các ca còn nghi ngờ tiếp tục bỏ phiếu.
                if any((len(scores) >= 2 and max(scores) >= 0.80)
                       or len(scores) >= 3
                       for scores in votes.values()):
                    break

        if votes:
            selected_text, selected_conf = select_plate_candidate(votes)
            if selected_text:
                return selected_text, selected_conf
        return best_text, best_conf

    @staticmethod
    def _tight_otsu(crop: np.ndarray | None) -> np.ndarray | None:
        if crop is None or crop.size == 0:
            return None
        gray = cv2.cvtColor(crop, cv2.COLOR_BGR2GRAY)
        # EasyOCR scales its detector cost with the input area. A fixed 2x
        # enlargement made already-large camera crops take several seconds.
        # Keep the useful enlargement for small plates but cap the OCR width.
        scale = min(2.0, 420 / max(1, gray.shape[1]))
        interpolation = cv2.INTER_AREA if scale < 1.0 else cv2.INTER_CUBIC
        enlarged = cv2.resize(
            gray, None, fx=scale, fy=scale, interpolation=interpolation
        )
        return cv2.threshold(enlarged, 0, 255, cv2.THRESH_BINARY + cv2.THRESH_OTSU)[1]

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

    def _vehicle_detections_at(
        self, image: np.ndarray, imgsz: int
    ) -> list[tuple[str, float, np.ndarray]]:
        """Lượt dò bổ sung cho khung hình lớn, bỏ qua ảnh chụp cận cảnh."""
        if min(image.shape[:2]) < 480:
            return []
        result = self.vehicle_model.predict(
            image,
            conf=max(0.10, self.vehicle_confidence * 0.5),
            imgsz=imgsz,
            verbose=False,
            classes=[1, 2, 3],
        )[0]
        return self._vehicle_detections(result)

    @staticmethod
    def _vehicle_detections(result: object) -> list[tuple[str, float, np.ndarray]]:
        detections: list[tuple[str, float, np.ndarray]] = []
        if result.boxes is None:
            return detections
        for box in result.boxes:
            name = str(result.names[int(box.cls.item())]).lower()
            detections.append((
                name,
                float(box.conf.item()),
                box.xyxy[0].cpu().numpy().astype(int),
            ))
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
        height, width = image.shape[:2]
        max_dimension = 1280
        if max(height, width) > max_dimension:
            scale = max_dimension / max(height, width)
            image = cv2.resize(image, (max(1, round(width * scale)), max(1, round(height * scale))),
                               interpolation=cv2.INTER_AREA)
        ok, encoded = cv2.imencode(".jpg", image, [cv2.IMWRITE_JPEG_QUALITY, 78])
        if not ok:
            return ""
        return base64.b64encode(encoded.tobytes()).decode("ascii")
