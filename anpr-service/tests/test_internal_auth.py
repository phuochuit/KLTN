import pytest
from fastapi import HTTPException
from fastapi.testclient import TestClient
from pathlib import Path

from app import main
from app.schemas import RecognitionResponse


client = TestClient(main.app)
TEST_SERVICE_TOKEN = "synthetic-internal-token-for-tests-32-chars"


class FakeRecognizer:
    def recognize_image_bytes(self, data: bytes) -> RecognitionResponse:
        assert data == b"synthetic image"
        return RecognitionResponse(
            plateText="59A112345",
            vehicleType="MOTORBIKE",
            detectionConfidence=0.9,
            ocrConfidence=0.9,
            vehicleConfidence=0.8,
            boundingBox=None,
            frameIndex=0,
            annotatedImageBase64="",
            message="synthetic test response",
        )

    def recognize_video(self, path: Path) -> RecognitionResponse:
        assert path.read_bytes() == b"synthetic video"
        return self.recognize_image_bytes(b"synthetic image")


@pytest.fixture(autouse=True)
def use_fake_recognizer(monkeypatch):
    monkeypatch.setattr(main, "AnprRecognizer", FakeRecognizer)
    main.recognizer.cache_clear()
    yield
    main.recognizer.cache_clear()


def test_recognition_requires_configured_internal_bearer(monkeypatch):
    monkeypatch.delenv("ANPR_SERVICE_TOKEN", raising=False)

    response = client.post(
        "/recognize/image",
        files={"file": ("plate.jpg", b"synthetic image", "image/jpeg")},
    )
    assert response.status_code == 503


def test_weak_internal_bearer_configuration_fails_closed(monkeypatch):
    monkeypatch.setenv("ANPR_SERVICE_TOKEN", "too-short")

    response = client.post(
        "/recognize/image",
        files={"file": ("plate.jpg", b"synthetic image", "image/jpeg")},
    )

    assert response.status_code == 503


def test_recognition_rejects_missing_or_invalid_internal_bearer(monkeypatch):
    monkeypatch.setenv("ANPR_SERVICE_TOKEN", TEST_SERVICE_TOKEN)

    upload = {"file": ("plate.jpg", b"synthetic image", "image/jpeg")}
    assert client.post("/recognize/image", files=upload).status_code == 401
    assert client.post(
        "/recognize/image", files=upload,
        headers={"Authorization": "Bearer wrong-token"},
    ).status_code == 401
    with pytest.raises(HTTPException) as exception:
        main.require_internal_service(f"Bearer {TEST_SERVICE_TOKEN}é")
    assert exception.value.status_code == 401


def test_internal_bearer_allows_recognition_and_health_stays_minimal(monkeypatch):
    monkeypatch.setenv("ANPR_SERVICE_TOKEN", TEST_SERVICE_TOKEN)
    response = client.post(
        "/recognize/image",
        files={"file": ("plate.jpg", b"synthetic image", "image/jpeg")},
        headers={"Authorization": f"Bearer {TEST_SERVICE_TOKEN}"},
    )

    assert response.status_code == 200
    assert response.json()["plateText"] == "59A112345"
    health = client.get("/health")
    assert health.status_code == 200
    assert health.json() == {"status": "UP"}


def test_video_recognition_requires_internal_bearer_and_cleans_workspace_temp(monkeypatch):
    monkeypatch.setenv("ANPR_SERVICE_TOKEN", TEST_SERVICE_TOKEN)
    temp_root = Path(__file__).resolve().parent / ".test-tmp-video"
    temp_root.mkdir(exist_ok=True)
    monkeypatch.setenv("ANPR_TEMP_DIR", str(temp_root))
    upload = {"file": ("clip.mp4", b"synthetic video", "video/mp4")}

    assert client.post("/recognize/video", files=upload).status_code == 401
    response = client.post("/recognize/video", files=upload,
        headers={"Authorization": f"Bearer {TEST_SERVICE_TOKEN}"})

    assert response.status_code == 200
    assert response.json()["plateText"] == "59A112345"
    assert list(temp_root.iterdir()) == []
    temp_root.rmdir()


def test_face_routes_reject_unauthenticated_direct_calls(monkeypatch):
    monkeypatch.setenv("ANPR_SERVICE_TOKEN", TEST_SERVICE_TOKEN)
    image = {"registration": ("face.jpg", b"synthetic image", "image/jpeg"),
             "realtime": ("live.jpg", b"synthetic image", "image/jpeg")}

    assert client.post("/face/verify", files=image).status_code == 401
    assert client.post("/face/verify-camera", files={"registration": image["registration"]},
                       data={"cameraIndex": "0"}).status_code == 401
    assert client.post("/face/capture-camera").status_code == 401
