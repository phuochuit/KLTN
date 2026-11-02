import cv2
import numpy as np

from app import classify_liveness


def test_static_frames_are_spoof():
    frames = [np.full((120, 120, 3), 120, dtype=np.uint8) for _ in range(5)]

    result = classify_liveness(frames)

    assert result["decision"] == "SPOOF"
    assert result["reason"] == "static_scene"


def test_too_few_frames_require_review():
    result = classify_liveness([np.zeros((80, 80, 3), dtype=np.uint8)])

    assert result["decision"] == "REVIEW"
    assert result["reason"] == "insufficient_frames"


def test_moving_frames_are_live():
    frames = []
    for index in range(6):
        frame = np.full((120, 120, 3), 255, dtype=np.uint8)
        left = 10 + index * 8
        cv2.rectangle(frame, (left, 35), (left + 50, 85), (0, 0, 0), -1)
        frames.append(frame)

    result = classify_liveness(frames)

    assert result["decision"] == "LIVE"
    assert result["reason"] == "ok"
