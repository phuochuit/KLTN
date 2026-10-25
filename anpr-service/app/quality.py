"""Image quality gate for plate and face captures.

The gate never blocks a request by itself.  It returns a structured verdict so
Spring, the desktop station and the exception report can decide what to do,
while the original evidence image is stored untouched.
"""
from __future__ import annotations

import math

import cv2
import numpy as np

QUALITY_VERSION = "quality/1.0"

DARK_BELOW = 55.0
BRIGHT_ABOVE = 210.0
GLARE_ABOVE = 0.12

FACE_MIN_SIDE = 96
FACE_BLUR_BELOW = 45.0
FACE_BLUR_REJECT = 18.0

PLATE_MIN_WIDTH = 80
PLATE_MIN_HEIGHT = 24
PLATE_BLUR_BELOW = 60.0
PLATE_BLUR_REJECT = 20.0
PLATE_TILT_ABOVE = 12.0

_MESSAGES: dict[str, str] = {
    "FACE_DARK": "Ảnh khuôn mặt quá tối",
    "FACE_OVEREXPOSED": "Ảnh khuôn mặt cháy sáng",
    "FACE_GLARE": "Khuôn mặt bị lóa sáng",
    "FACE_BLURRY": "Ảnh khuôn mặt bị mờ",
    "FACE_TOO_SMALL": "Khuôn mặt quá nhỏ trong khung hình",
    "PLATE_DARK": "Ảnh biển số quá tối",
    "PLATE_OVEREXPOSED": "Ảnh biển số cháy sáng",
    "PLATE_GLARE": "Biển số bị lóa sáng",
    "PLATE_BLURRY": "Ảnh biển số bị mờ",
    "PLATE_TOO_SMALL": "Biển số quá nhỏ trong khung hình",
    "PLATE_TILTED": "Biển số bị nghiêng",
}


def _issue(code: str, severity: str = "warning") -> dict:
    return {"code": code, "message": _MESSAGES[code], "severity": severity}


def _crop(image: np.ndarray, box: tuple[int, int, int, int] | None) -> np.ndarray:
    if box is None:
        return image
    x, y, width, height = box
    image_height, image_width = image.shape[:2]
    x0, y0 = max(0, int(x)), max(0, int(y))
    x1, y1 = min(image_width, int(x + width)), min(image_height, int(y + height))
    return image[y0:y1, x0:x1]


def _metrics(image: np.ndarray) -> dict:
    if image.size == 0:
        return {"brightness": 0.0, "contrast": 0.0, "sharpness": 0.0,
                "glare": 0.0, "width": 0, "height": 0}
    gray = cv2.cvtColor(image, cv2.COLOR_BGR2GRAY)
    return {
        "brightness": round(float(np.mean(gray)), 1),
        "contrast": round(float(np.std(gray)), 1),
        "sharpness": round(float(cv2.Laplacian(gray, cv2.CV_64F).var()), 1),
        "glare": round(float(np.count_nonzero(gray >= 250)) / max(gray.size, 1), 4),
        "width": int(image.shape[1]),
        "height": int(image.shape[0]),
    }


def _verdict(issues: list[dict], metrics: dict) -> dict:
    if any(issue["severity"] == "reject" for issue in issues):
        status = "REJECT"
    elif issues:
        status = "WARNING"
    else:
        status = "OK"
    return {"status": status, "version": QUALITY_VERSION,
            "warnings": issues, "metrics": metrics}


def _check_lighting(issues: list[dict], metrics: dict, prefix: str) -> None:
    if metrics["brightness"] < DARK_BELOW:
        issues.append(_issue(f"{prefix}_DARK"))
    if metrics["brightness"] > BRIGHT_ABOVE:
        issues.append(_issue(f"{prefix}_OVEREXPOSED"))
    if metrics["glare"] > GLARE_ABOVE:
        issues.append(_issue(f"{prefix}_GLARE"))


def assess_face(image: np.ndarray, box: tuple[int, int, int, int] | None = None) -> dict:
    """Assess the detected face crop before it is used for matching."""
    crop = _crop(image, box)
    metrics = _metrics(crop)
    issues: list[dict] = []
    if min(metrics["width"], metrics["height"]) < FACE_MIN_SIDE:
        issues.append(_issue("FACE_TOO_SMALL", "reject"))
    if metrics["sharpness"] < FACE_BLUR_REJECT:
        issues.append(_issue("FACE_BLURRY", "reject"))
    elif metrics["sharpness"] < FACE_BLUR_BELOW:
        issues.append(_issue("FACE_BLURRY"))
    _check_lighting(issues, metrics, "FACE")
    return _verdict(issues, metrics)


def _tilt_degrees(crop: np.ndarray) -> float:
    """Estimate the dominant near-horizontal edge angle of a plate crop."""
    if crop.size == 0:
        return 0.0
    gray = cv2.cvtColor(crop, cv2.COLOR_BGR2GRAY)
    edges = cv2.Canny(gray, 60, 160)
    minimum = max(20, crop.shape[1] // 3)
    lines = cv2.HoughLinesP(edges, 1, np.pi / 180, threshold=30,
                            minLineLength=minimum, maxLineGap=5)
    if lines is None:
        return 0.0
    angles = []
    for x1, y1, x2, y2 in lines.reshape(-1, 4):
        angle = math.degrees(math.atan2(float(y2 - y1), float(x2 - x1)))
        if angle > 90:
            angle -= 180
        elif angle < -90:
            angle += 180
        if abs(angle) <= 45:
            angles.append(angle)
    if not angles:
        return 0.0
    return round(float(np.median(angles)), 1)


def deskew(image: np.ndarray, angle: float | None = None) -> np.ndarray:
    """Straighten a plate crop so OCR sees horizontal character rows.

    The caller keeps the original evidence image; only the OCR copy is rotated.
    Small angles are returned unchanged to avoid needless interpolation.
    """
    if image.size == 0:
        return image
    measured = _tilt_degrees(image) if angle is None else angle
    if abs(measured) < 3:
        return image
    height, width = image.shape[:2]
    matrix = cv2.getRotationMatrix2D((width / 2, height / 2), measured, 1.0)
    return cv2.warpAffine(image, matrix, (width, height), flags=cv2.INTER_CUBIC,
                          borderMode=cv2.BORDER_REPLICATE)


def assess_plate(image: np.ndarray, box: tuple[int, int, int, int] | None = None) -> dict:
    """Assess the detected plate crop before OCR is trusted."""
    crop = _crop(image, box)
    metrics = _metrics(crop)
    metrics["tilt"] = _tilt_degrees(crop)
    issues: list[dict] = []
    if metrics["width"] < PLATE_MIN_WIDTH or metrics["height"] < PLATE_MIN_HEIGHT:
        issues.append(_issue("PLATE_TOO_SMALL", "reject"))
    if metrics["sharpness"] < PLATE_BLUR_REJECT:
        issues.append(_issue("PLATE_BLURRY", "reject"))
    elif metrics["sharpness"] < PLATE_BLUR_BELOW:
        issues.append(_issue("PLATE_BLURRY"))
    if abs(metrics["tilt"]) > PLATE_TILT_ABOVE:
        issues.append(_issue("PLATE_TILTED"))
    _check_lighting(issues, metrics, "PLATE")
    return _verdict(issues, metrics)
