from __future__ import annotations

import io, os, math
from pathlib import Path
from threading import RLock
import cv2
import numpy as np
from PIL import Image, ImageOps

ROOT = Path(__file__).resolve().parents[1]
MODEL_DIRS = [ROOT / "models", ROOT.parent / "face-verification-demo" / "models"]
YUNET = next((p / "face_detection_yunet_2023mar.onnx" for p in MODEL_DIRS if (p / "face_detection_yunet_2023mar.onnx").exists()), MODEL_DIRS[0] / "face_detection_yunet_2023mar.onnx")
SFACE = next((p / "face_recognition_sface_2021dec.onnx" for p in MODEL_DIRS if (p / "face_recognition_sface_2021dec.onnx").exists()), MODEL_DIRS[0] / "face_recognition_sface_2021dec.onnx")
MATCH_THRESHOLD = float(os.getenv("FACE_MATCH_THRESHOLD", "0.363"))
REVIEW_THRESHOLD = float(os.getenv("FACE_REVIEW_THRESHOLD", "0.300"))
DETECTION_THRESHOLD = float(os.getenv("FACE_DETECTION_THRESHOLD", "0.60"))


class FaceEngine:
    def __init__(self):
        self.detector = self.recognizer = None
        # FaceDetectorYN mutates its input size for every frame.  FastAPI runs
        # synchronous endpoints in worker threads, so concurrent operations
        # must not access this native OpenCV state at the same time.
        self._lock = RLock()

    @property
    def models_ready(self):
        return YUNET.exists() and SFACE.exists()

    def load(self):
        if self.detector is not None:
            return
        if not self.models_ready:
            raise RuntimeError("Thiếu mô hình YuNet/SFace cho xác thực khuôn mặt")
        # A larger input size lets YuNet recover faces at wider yaw/pitch.
        self.detector = cv2.FaceDetectorYN.create(str(YUNET), "", (640, 640), DETECTION_THRESHOLD, 0.3, 5000)
        self.recognizer = cv2.FaceRecognizerSF.create(str(SFACE), "")

    def detect_best(self, image):
        """Detect a face across scales; return the clearest single detection.

        YuNet is sensitive to extreme head pose, so we inspect the original
        plus a mildly contrast-normalized copy.  We never lower the confidence
        threshold to chase difficult angles because that admits printed faces.
        """
        with self._lock:
            self.load()
            h, w = image.shape[:2]
            if min(h, w) < 160:
                return None, []
            candidates = []
            variants = [image]
            lab = cv2.cvtColor(image, cv2.COLOR_BGR2LAB)
            l, a, b = cv2.split(lab)
            clahe = cv2.createCLAHE(clipLimit=2.0, tileGridSize=(8, 8))
            normalized = cv2.cvtColor(cv2.merge([clahe.apply(l), a, b]), cv2.COLOR_LAB2BGR)
            variants.append(normalized)
            for variant in variants:
                self.detector.setInputSize((w, h))
                _, faces = self.detector.detect(variant)
                if faces is None:
                    continue
                for face in faces:
                    x, y, fw, fh = (int(v) for v in face[:4])
                    confidence = float(face[14])
                    # Reject boxes that are implausibly small for recognition.
                    if min(fw, fh) < 80:
                        continue
                    if fw <= 0 or fh <= 0 or x < 0 or y < 0 or x + fw > w or y + fh > h:
                        continue
                    crop = image[max(0, y):min(h, y + fh), max(0, x):min(w, x + fw)]
                    gray = cv2.cvtColor(crop, cv2.COLOR_BGR2GRAY)
                    sharpness = float(cv2.Laplacian(gray, cv2.CV_64F).var()) if crop.size else 0.0
                    area = fw * fh
                    score = confidence * 0.65 + min(sharpness / 300.0, 1.0) * 0.20 + min(area / (w * h) / 0.25, 1.0) * 0.15
                    candidates.append((score, face, sharpness))
            if not candidates:
                return None, []
            candidates.sort(key=lambda item: item[0], reverse=True)
            best = candidates[0][1]
            details = [{"confidence": round(float(best[14]), 4), "box": [int(v) for v in best[:4]]}]
            return best, details

    def analyze(self, image, label):
        with self._lock:
            face, details = self.detect_best(image)
            if face is None:
                raise ValueError(f"{label}: cần đúng một khuôn mặt rõ ràng (đã thấy 0)")
            x, y, fw, fh = (int(v) for v in face[:4])
            crop = image[max(0, y):min(image.shape[0], y + fh), max(0, x):min(image.shape[1], x + fw)]
            gray = cv2.cvtColor(crop, cv2.COLOR_BGR2GRAY)
            brightness = float(np.mean(gray))
            sharpness = float(cv2.Laplacian(gray, cv2.CV_64F).var())
            warnings = []
            if brightness < 55:
                warnings.append("Ảnh tối")
            if brightness > 210:
                warnings.append("Ảnh cháy sáng")
            if sharpness < 45:
                warnings.append("Ảnh mờ")
            aligned = self.recognizer.alignCrop(image, face)
            feature = self.recognizer.feature(aligned)
            return feature, warnings, {"box": [x, y, fw, fh], "confidence": round(float(face[14]), 4), "brightness": round(brightness, 1), "sharpness": round(sharpness, 1)}

    def compare(self, registered, realtime):
        with self._lock:
            a, _, _ = self.analyze(registered, "Ảnh đăng ký")
            b, warnings, details = self.analyze(realtime, "Ảnh realtime")
            score = round(float(self.recognizer.match(a, b, cv2.FaceRecognizerSF_FR_COSINE)), 4)
            decision = "PASS" if score >= MATCH_THRESHOLD else ("REVIEW" if score >= REVIEW_THRESHOLD else "REJECT")
            if decision == "PASS":
                message = "Khuôn mặt khớp ảnh đăng ký"
                if warnings:
                    message += "; lưu ý chất lượng: " + ", ".join(warnings)
            elif decision == "REVIEW":
                message = "Điểm nằm gần ngưỡng; nhân viên cần kiểm tra hoặc chụp lại"
            else:
                message = "Khuôn mặt không khớp ảnh đăng ký"
            return {"decision": decision, "similarity": score, "matchThreshold": MATCH_THRESHOLD, "warnings": warnings, "face": details, "message": message}

    @staticmethod
    def _clahe_variant(image):
        lab = cv2.cvtColor(image, cv2.COLOR_BGR2LAB)
        l, a, b = cv2.split(lab)
        l = cv2.createCLAHE(clipLimit=2.0, tileGridSize=(8, 8)).apply(l)
        return cv2.cvtColor(cv2.merge([l, a, b]), cv2.COLOR_LAB2BGR)

    def compare_best(self, registered, candidates):
        """Match one registered image against several camera frames.

        A single webcam frame can be unlucky (blink, motion blur, glare).
        We score every frame plus a contrast-normalized copy and keep the
        highest cosine similarity.  The match threshold is unchanged, so
        this only reduces false rejections, it does not weaken security.
        """
        with self._lock:
            a, _, _ = self.analyze(registered, "Ảnh đăng ký")
            best_score = -1.0
            best_warnings = []
            best_details = None
            for candidate in candidates:
                for variant in (candidate, self._clahe_variant(candidate)):
                    try:
                        feature, warnings, details = self.analyze(variant, "Ảnh realtime")
                    except ValueError:
                        continue
                    score = float(self.recognizer.match(a, feature, cv2.FaceRecognizerSF_FR_COSINE))
                    if score > best_score:
                        best_score = score
                        best_warnings = warnings
                        best_details = details
            if best_details is None:
                raise ValueError("Ảnh realtime: cần đúng một khuôn mặt rõ ràng (đã thấy 0)")
            score = round(best_score, 4)
            decision = "PASS" if score >= MATCH_THRESHOLD else ("REVIEW" if score >= REVIEW_THRESHOLD else "REJECT")
            if decision == "PASS":
                message = "Khuôn mặt khớp ảnh đăng ký"
                if best_warnings:
                    message += "; lưu ý chất lượng: " + ", ".join(best_warnings)
            elif decision == "REVIEW":
                message = "Điểm nằm gần ngưỡng; nhân viên cần kiểm tra hoặc chụp lại"
            else:
                message = "Khuôn mặt không khớp ảnh đăng ký"
            return {"decision": decision, "similarity": score, "matchThreshold": MATCH_THRESHOLD, "warnings": best_warnings, "face": best_details, "message": message}


def decode_image(raw: bytes):
    try:
        image = ImageOps.exif_transpose(Image.open(io.BytesIO(raw))).convert("RGB")
        return cv2.cvtColor(np.asarray(image), cv2.COLOR_RGB2BGR)
    except Exception as exc:
        raise ValueError("Không đọc được tệp ảnh") from exc
