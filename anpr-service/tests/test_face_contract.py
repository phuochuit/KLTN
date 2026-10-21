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
    return {"X-Parking-Service-Token": TOKEN, "X-Request-ID": "face-contract-test"}


def test_verify_image_accepts_private_registration_bytes(monkeypatch):
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
    assert response.json()["decision"] == "PASS"
    assert response.json()["requestId"] == "face-contract-test"
    assert response.json()["realtimeImageBase64"]


def test_verify_camera_uses_requested_camera_and_uploaded_registration(monkeypatch):
    monkeypatch.setenv("PARKING_INTERNAL_SERVICE_TOKEN", TOKEN)
    seen = {}
    monkeypatch.setattr(main, "capture_camera_frames", lambda index, count, camera_source=None: (
        seen.update(index=index, count=count, source=camera_source) or [np.full((8, 8, 3), 127, dtype=np.uint8)]
    ))
    monkeypatch.setattr(main.face_engine, "compare_best", lambda registered, frames: {
        "decision": "PASS", "similarity": 0.8, "matchThreshold": 0.363,
        "reviewThreshold": 0.3, "message": "ok", "bestFrameIndex": 0,
    })
    monkeypatch.setattr(main, "liveness_score", lambda frames: 0.0)

    response = TestClient(main.app).post(
        "/face/verify-camera", headers=_headers(), data={"cameraIndex": "3"},
        files={"registration": ("registered.jpg", _jpeg(), "image/jpeg")},
    )

    assert response.status_code == 200
    assert seen == {"index": 3, "count": 4, "source": None}


def test_capture_camera_uses_requested_camera(monkeypatch):
    monkeypatch.setenv("PARKING_INTERNAL_SERVICE_TOKEN", TOKEN)
    seen = {}
    monkeypatch.setattr(main, "capture_camera_frames", lambda index, count, camera_source=None: (
        seen.update(index=index, count=count, source=camera_source) or [np.full((8, 8, 3), 127, dtype=np.uint8)]
    ))
    monkeypatch.setattr(main.face_engine, "analyze", lambda frame, label: (None, None, None))
    monkeypatch.setattr(main, "liveness_score", lambda frames: 0.0)

    response = TestClient(main.app).post(
        "/face/capture-camera", headers=_headers(), json={"cameraIndex": 4},
    )

    assert response.status_code == 200
    assert seen == {"index": 4, "count": 8, "source": None}
    assert response.json()["decision"] == "CAPTURED"


def test_network_camera_source_is_forwarded_without_using_it_as_lock_key(monkeypatch):
    monkeypatch.setenv("PARKING_INTERNAL_SERVICE_TOKEN", TOKEN)
    seen = {}
    monkeypatch.setattr(main, "capture_camera_frames", lambda index, count, camera_source=None: (
        seen.update(index=index, count=count, source=camera_source)
        or [np.full((8, 8, 3), 127, dtype=np.uint8)]
    ))
    monkeypatch.setattr(main.face_engine, "analyze", lambda frame, label: (None, None, None))
    monkeypatch.setattr(main, "liveness_score", lambda frames: 0.0)
    source = "rtsp://camera-user:camera-password@192.168.1.20/live"

    response = TestClient(main.app).post(
        "/face/capture-camera", headers=_headers(),
        json={"cameraIndex": 0, "cameraSource": source},
    )

    assert response.status_code == 200
    assert seen["source"] == source
    lock_key = main.camera_lock_key(source)
    assert lock_key.startswith("network:")
    assert source not in lock_key


def test_camera_source_rejects_unsafe_values_without_echoing_them():
    for source in ("file:///private/video.mp4", "C:/camera.mp4", "ftp://camera/live"):
        try:
            main.normalize_camera_source(0, source)
            assert False, "Nguồn camera không an toàn phải bị từ chối"
        except ValueError as error:
            assert source not in str(error)
