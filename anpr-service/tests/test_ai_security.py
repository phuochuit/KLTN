import cv2
import numpy as np
from fastapi.testclient import TestClient

from app import main


TOKEN = "phase6-test-service-token"
FILES = {
    "registration": ("registered.jpg", None, "image/jpeg"),
    "realtime": ("realtime.jpg", None, "image/jpeg"),
}


def _jpeg() -> bytes:
    ok, encoded = cv2.imencode(".jpg", np.full((8, 8, 3), 127, dtype=np.uint8))
    assert ok
    return encoded.tobytes()


def _files(realtime: bytes | None = None) -> dict:
    return {
        "registration": ("registered.jpg", _jpeg(), "image/jpeg"),
        "realtime": ("realtime.jpg", _jpeg() if realtime is None else realtime,
                     "image/jpeg"),
    }


def _headers(token: str | None = TOKEN, request_id: str = "ai-security-test") -> dict:
    headers = {"X-Request-ID": request_id}
    if token is not None:
        headers["X-Parking-Service-Token"] = token
    return headers


def _client() -> TestClient:
    return TestClient(main.app)


def test_missing_service_token_is_rejected(monkeypatch):
    monkeypatch.setenv("PARKING_INTERNAL_SERVICE_TOKEN", TOKEN)

    response = _client().post("/face/verify-image", headers=_headers(token=None),
                              files=_files())

    assert response.status_code == 401
    assert response.json()["error"] == "invalid_service_token"


def test_wrong_service_token_is_rejected(monkeypatch):
    monkeypatch.setenv("PARKING_INTERNAL_SERVICE_TOKEN", TOKEN)

    response = _client().post("/face/verify-image", headers=_headers(token="wrong-token"),
                              files=_files())

    assert response.status_code == 401


def test_oversized_request_id_is_replaced(monkeypatch):
    monkeypatch.setenv("PARKING_INTERNAL_SERVICE_TOKEN", TOKEN)
    monkeypatch.setattr(main.face_engine, "compare_best", lambda registered, frames: {
        "decision": "PASS", "similarity": 0.9, "matchThreshold": 0.363,
        "reviewThreshold": 0.3, "message": "ok", "bestFrameIndex": 0,
    })
    oversized = "x" * 129

    response = _client().post("/face/verify-image",
                              headers=_headers(request_id=oversized), files=_files())

    assert response.status_code == 200
    assert response.headers["X-Request-ID"] != oversized
    assert len(response.headers["X-Request-ID"]) == 36


def test_malformed_image_is_rejected(monkeypatch):
    monkeypatch.setenv("PARKING_INTERNAL_SERVICE_TOKEN", TOKEN)

    response = _client().post("/face/verify-image", headers=_headers(),
                              files=_files(realtime=b"not-an-image"))

    assert response.status_code == 422


def test_unsupported_video_extension_is_rejected(monkeypatch):
    monkeypatch.setenv("PARKING_INTERNAL_SERVICE_TOKEN", TOKEN)

    response = _client().post(
        "/face/verify-video", headers=_headers(),
        files={
            "registration": ("registered.jpg", _jpeg(), "image/jpeg"),
            "realtime": ("clip.txt", b"not-a-video", "text/plain"),
        },
    )

    assert response.status_code == 400


def test_oversized_recognition_upload_is_rejected(monkeypatch):
    monkeypatch.setenv("PARKING_INTERNAL_SERVICE_TOKEN", TOKEN)
    payload = b"0" * (25 * 1024 * 1024 + 1)

    response = _client().post("/recognize/image", headers=_headers(),
                              files={"file": ("large.jpg", payload, "image/jpeg")})

    assert response.status_code == 413
