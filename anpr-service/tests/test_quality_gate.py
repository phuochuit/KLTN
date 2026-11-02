import cv2
import numpy as np

from app import quality


def _codes(verdict: dict) -> set[str]:
    return {warning["code"] for warning in verdict["warnings"]}


def _checkerboard(width: int = 240, height: int = 80) -> np.ndarray:
    image = np.full((height, width, 3), 140, dtype=np.uint8)
    for x in range(0, width, 10):
        cv2.line(image, (x, 0), (x, height - 1), (20, 20, 20), 3)
    for y in range(0, height, 20):
        cv2.line(image, (0, y), (width - 1, y), (240, 240, 240), 2)
    return image


def test_dark_face_is_flagged():
    verdict = quality.assess_face(np.full((200, 200, 3), 15, dtype=np.uint8))

    assert "FACE_DARK" in _codes(verdict)
    assert verdict["status"] == "REJECT"
    assert verdict["version"] == quality.QUALITY_VERSION


def test_blurry_face_is_rejected():
    verdict = quality.assess_face(np.full((200, 200, 3), 120, dtype=np.uint8))

    assert "FACE_BLURRY" in _codes(verdict)
    assert verdict["status"] == "REJECT"


def test_small_face_box_is_rejected():
    image = np.full((400, 400, 3), 120, dtype=np.uint8)

    verdict = quality.assess_face(image, (10, 10, 60, 60))

    assert "FACE_TOO_SMALL" in _codes(verdict)
    assert verdict["status"] == "REJECT"


def test_clear_plate_passes():
    verdict = quality.assess_plate(_checkerboard())

    assert verdict["status"] == "OK"
    assert verdict["warnings"] == []
    assert verdict["metrics"]["width"] == 240


def test_small_plate_is_rejected():
    image = np.full((300, 300, 3), 120, dtype=np.uint8)

    verdict = quality.assess_plate(image, (0, 0, 50, 15))

    assert "PLATE_TOO_SMALL" in _codes(verdict)
    assert verdict["status"] == "REJECT"


def test_tilted_plate_is_flagged():
    image = np.full((160, 320, 3), 200, dtype=np.uint8)
    for offset in range(-20, 340, 24):
        cv2.line(image, (offset, 20), (offset + 250, 110), (10, 10, 10), 3)

    verdict = quality.assess_plate(image)

    assert "PLATE_TILTED" in _codes(verdict)
    assert abs(verdict["metrics"]["tilt"]) > 0


def test_deskew_straightens_tilted_crop():
    image = np.full((200, 360, 3), 190, dtype=np.uint8)
    for offset in range(-40, 400, 30):
        cv2.line(image, (offset, 20), (offset + 300, 130), (15, 15, 15), 4)

    before = quality._tilt_degrees(image)
    fixed = quality.deskew(image)
    after = quality._tilt_degrees(fixed)

    assert abs(before) > 15
    assert abs(after) < abs(before)
