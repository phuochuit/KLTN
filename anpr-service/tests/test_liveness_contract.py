import cv2
import numpy as np
from fastapi.testclient import TestClient

from app import main


TOKEN = "phase4-test-service-token"


def _jpeg() -> bytes:
    ok, encoded = cv2.imencode(".jpg", np.full((8, 8, 3), 127, dtype=np.uint8))
    assert ok
    return encoded.tobytes()


def _headers() -> dict[str, str]:
    return {"X-Parking-Service-Token": TOKEN, "X-Request-ID": "liveness-contract-test"}


def _static_frames(count: int = 4) -> list[np.ndarray]:
    return [np.full((120, 120, 3), 127, dtype=np.uint8) for _ in range(count)]


def _moving_frames(count: int = 6) -> list[np.ndarray]:
    frames = []
    for index in range(count):
        frame = np.full((120, 120, 3), 255, dtype=np.uint8)
        left = 10 + index * 8
        cv2.rectangle(frame, (left, 35), (left + 50, 85), (0, 0, 0), -1)
        frames.append(frame)
    return frames


def test_static_frames_are_reported_as_spoof():
    result = main.evaluate_liveness(_static_frames())

    assert result["decision"] == "SPOOF"
    assert result["confidence"] == 0.0
    assert result["reason"] == "static_scene"
    assert result["model"] == main.LIVENESS_MODEL


def test_too_few_frames_are_not_evaluated():
    result = main.evaluate_liveness(_static_frames(2))

    assert result["decision"] == "NOT_EVALUATED"
    assert result["reason"] == "insufficient_frames"


def test_live_motion_is_reported_as_live():
    result = main.evaluate_liveness(_moving_frames())

    assert result["decision"] == "LIVE"
    assert 0.0 < result["confidence"] <= 1.0
    assert result["reason"] == "ok"


def test_excessive_motion_requires_review():
    frames = [np.full((120, 120, 3), 0 if index % 2 else 255, dtype=np.uint8) for index in range(6)]

    result = main.evaluate_liveness(frames)

    assert result["decision"] == "REVIEW"
    assert result["reason"] == "excessive_motion"


def test_verify_camera_surfaces_liveness_fields(monkeypatch):
    monkeypatch.setenv("PARKING_INTERNAL_SERVICE_TOKEN", TOKEN)
    monkeypatch.setattr(
        main, "capture_camera_frames",
        lambda index, count, camera_source=None: _static_frames(),
    )
    monkeypatch.setattr(main.face_engine, "compare_best", lambda registered, frames: {
        "decision": "PASS", "similarity": 0.9, "matchThreshold": 0.363,
        "reviewThreshold": 0.3, "message": "ok", "bestFrameIndex": 0,
    })

    response = TestClient(main.app).post(
        "/face/verify-camera", headers=_headers(), data={"cameraIndex": "0"},
        files={"registration": ("registered.jpg", _jpeg(), "image/jpeg")},
    )

    assert response.status_code == 200
    body = response.json()
    assert body["livenessDecision"] == "SPOOF"
    assert body["livenessReason"] == "static_scene"
    assert body["livenessModel"] == main.LIVENESS_MODEL
    assert body["decision"] == "PASS"


def test_verify_image_reports_liveness_not_evaluated_for_single_frame(monkeypatch):
    monkeypatch.setenv("PARKING_INTERNAL_SERVICE_TOKEN", TOKEN)
    monkeypatch.setattr(main.face_engine, "compare_best", lambda registered, frames: {
        "decision": "PASS", "similarity": 0.9, "matchThreshold": 0.363,
        "reviewThreshold": 0.3, "message": "ok", "bestFrameIndex": 0,
    })

    response = TestClient(main.app).post(
        "/face/verify-image", headers=_headers(),
        files={
            "registration": ("registered.jpg", _jpeg(), "image/jpeg"),
            "realtime": ("realtime.jpg", _jpeg(), "image/jpeg"),
        },
    )

    assert response.status_code == 200
    body = response.json()
    assert body["livenessDecision"] == "NOT_EVALUATED"
    assert body["livenessReason"] == "insufficient_frames"
