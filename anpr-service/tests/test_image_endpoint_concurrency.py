from concurrent.futures import ThreadPoolExecutor
from threading import Barrier

import cv2
import numpy as np
from fastapi.testclient import TestClient

from app import main
from app.schemas import RecognitionResponse


TOKEN = "concurrency-test-service-token"


def _jpeg() -> bytes:
    ok, encoded = cv2.imencode(".jpg", np.full((32, 32, 3), 127, dtype=np.uint8))
    assert ok
    return encoded.tobytes()


def test_plate_and_face_image_inference_run_concurrently(monkeypatch):
    rendezvous = Barrier(2, timeout=2)

    class FakeRecognizer:
        ready = True

        def warm_up(self):
            pass

        def recognize_image_bytes(self, data, profile=None):
            rendezvous.wait()
            return RecognitionResponse(
                plateText="59F241578", vehicleType="MOTORBIKE",
                detectionConfidence=0.9, ocrConfidence=0.8,
                vehicleConfidence=0.7, boundingBox=None, frameIndex=0,
                annotatedImageBase64="", message="ok",
            )

    def compare_best(registered, frames):
        rendezvous.wait()
        return {
            "decision": "PASS", "similarity": 0.9,
            "matchThreshold": 0.363, "reviewThreshold": 0.3,
            "message": "ok", "bestFrameIndex": 0,
        }

    monkeypatch.setenv("PARKING_INTERNAL_SERVICE_TOKEN", TOKEN)
    monkeypatch.setattr(main, "recognizer", lambda: FakeRecognizer())
    monkeypatch.setattr(main.face_engine, "compare_best", compare_best)
    headers = {"X-Parking-Service-Token": TOKEN}
    image = _jpeg()

    def recognize_plate():
        return TestClient(main.app).post(
            "/recognize/image", headers={**headers, "X-Request-ID": "concurrent-plate"},
            files={"file": ("plate.jpg", image, "image/jpeg")},
        )

    def verify_face():
        return TestClient(main.app).post(
            "/face/verify-image", headers={**headers, "X-Request-ID": "concurrent-face"},
            files={
                "registration": ("registered.jpg", image, "image/jpeg"),
                "realtime": ("realtime.jpg", image, "image/jpeg"),
            },
        )

    with ThreadPoolExecutor(max_workers=2) as executor:
        plate_future = executor.submit(recognize_plate)
        face_future = executor.submit(verify_face)
        plate_response = plate_future.result(timeout=5)
        face_response = face_future.result(timeout=5)

    assert plate_response.status_code == 200
    assert plate_response.json()["plateText"] == "59F241578"
    assert face_response.status_code == 200
    assert face_response.json()["decision"] == "PASS"
