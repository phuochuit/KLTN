from __future__ import annotations

import io, os
from pathlib import Path
import cv2
import numpy as np
from PIL import Image, ImageOps

ROOT = Path(__file__).resolve().parents[1]
MODEL_DIRS = [ROOT / "models", ROOT.parent / "face-verification-demo" / "models"]
YUNET = next((p / "face_detection_yunet_2023mar.onnx" for p in MODEL_DIRS if (p / "face_detection_yunet_2023mar.onnx").exists()), MODEL_DIRS[0] / "face_detection_yunet_2023mar.onnx")
SFACE = next((p / "face_recognition_sface_2021dec.onnx" for p in MODEL_DIRS if (p / "face_recognition_sface_2021dec.onnx").exists()), MODEL_DIRS[0] / "face_recognition_sface_2021dec.onnx")
MATCH_THRESHOLD = float(os.getenv("FACE_MATCH_THRESHOLD", "0.363"))
REVIEW_THRESHOLD = float(os.getenv("FACE_REVIEW_THRESHOLD", "0.300"))

class FaceEngine:
    def __init__(self): self.detector = self.recognizer = None
    @property
    def models_ready(self): return YUNET.exists() and SFACE.exists()
    def load(self):
        if self.detector is not None: return
        if not self.models_ready: raise RuntimeError("Thiếu mô hình YuNet/SFace cho xác thực khuôn mặt")
        self.detector = cv2.FaceDetectorYN.create(str(YUNET), "", (320,320), .55, .3, 5000)
        self.recognizer = cv2.FaceRecognizerSF.create(str(SFACE), "")
    def analyze(self, image, label):
        self.load(); h,w=image.shape[:2]
        if min(h,w)<160: raise ValueError(f"{label}: ảnh quá nhỏ")
        self.detector.setInputSize((w,h)); _,faces=self.detector.detect(image)
        if faces is None or len(faces)!=1:
            count=0 if faces is None else len(faces)
            raise ValueError(f"{label}: cần đúng một khuôn mặt rõ ràng (đã thấy {count})")
        face=faces[0]; x,y,fw,fh=[int(v) for v in face[:4]]
        crop=image[max(0,y):min(h,y+fh),max(0,x):min(w,x+fw)]
        gray=cv2.cvtColor(crop,cv2.COLOR_BGR2GRAY)
        brightness=float(np.mean(gray)); sharpness=float(cv2.Laplacian(gray,cv2.CV_64F).var())
        warnings=[]
        if brightness<55: warnings.append("Ảnh tối")
        if brightness>210: warnings.append("Ảnh cháy sáng")
        if sharpness<45: warnings.append("Ảnh mờ")
        return self.recognizer.feature(self.recognizer.alignCrop(image,face)), warnings
    def compare(self, registered, realtime):
        a,_=self.analyze(registered,"Ảnh đăng ký"); b,warnings=self.analyze(realtime,"Ảnh realtime")
        score=round(float(self.recognizer.match(a,b,cv2.FaceRecognizerSF_FR_COSINE)),4)
        decision="PASS" if score>=MATCH_THRESHOLD else ("REVIEW" if score>=REVIEW_THRESHOLD else "REJECT")
        if decision == "PASS":
            message = "Khuôn mặt khớp ảnh đăng ký"
            if warnings: message += "; lưu ý chất lượng: " + ", ".join(warnings)
        elif decision == "REVIEW":
            message = "Điểm nằm gần ngưỡng; nhân viên cần kiểm tra hoặc chụp lại"
        else:
            message = "Khuôn mặt không khớp ảnh đăng ký"
        return {"decision":decision,"similarity":score,"matchThreshold":MATCH_THRESHOLD,"livenessChecked":False,"warnings":warnings,"message":message}

def decode_image(raw: bytes):
    try:
        image=ImageOps.exif_transpose(Image.open(io.BytesIO(raw))).convert("RGB")
        return cv2.cvtColor(np.asarray(image),cv2.COLOR_RGB2BGR)
    except Exception as exc: raise ValueError("Không đọc được tệp ảnh") from exc
