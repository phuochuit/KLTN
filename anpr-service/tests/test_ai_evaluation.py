import importlib.util
from pathlib import Path

MODULE_PATH = Path(__file__).resolve().parents[1] / "tools" / "ai_evaluation.py"
_spec = importlib.util.spec_from_file_location("ai_evaluation", MODULE_PATH)
ai = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(ai)


def _manifest():
    return [
        {"sample_id": "v1", "kind": "vehicle", "expected_plate": "59A112345",
         "expected_vehicle": "MOTORBIKE"},
        {"sample_id": "v2", "kind": "vehicle", "expected_plate": "51H88888",
         "expected_vehicle": "CAR"},
        {"sample_id": "l1", "kind": "liveness", "expected_liveness": "LIVE"},
        {"sample_id": "l2", "kind": "liveness", "expected_liveness": "SPOOF"},
        {"sample_id": "l3", "kind": "liveness", "expected_liveness": "SPOOF"},
        {"sample_id": "f1", "kind": "face", "expected_decision": "PASS"},
        {"sample_id": "f2", "kind": "face", "expected_decision": "REJECT"},
        {"sample_id": "f3", "kind": "face", "expected_decision": "REJECT"},
        {"sample_id": "f4", "kind": "face", "expected_decision": "PASS"},
    ]


def _predictions():
    return [
        {"sample_id": "v1", "plate_text": "59a1-123.45", "vehicle_type": "motorbike",
         "elapsed_ms": "120"},
        {"sample_id": "v2", "plate_text": "", "vehicle_type": "",
         "elapsed_ms": "880"},
        {"sample_id": "l1", "liveness_decision": "LIVE"},
        {"sample_id": "l2", "liveness_decision": "LIVE"},
        {"sample_id": "l3", "liveness_decision": "SPOOF"},
        {"sample_id": "f1", "face_decision": "PASS"},
        {"sample_id": "f2", "face_decision": "PASS"},
        {"sample_id": "f3", "face_decision": "REJECT"},
        {"sample_id": "f4", "face_decision": "REJECT"},
    ]


def test_vehicle_metrics_normalize_plate_and_type():
    report = ai.evaluate(_manifest(), _predictions())

    assert report["vehicle"]["plate_detection_rate"] == 50.0
    assert report["vehicle"]["ocr_exact_match"] == 50.0
    assert report["vehicle"]["vehicle_type_accuracy"] == 50.0


def test_liveness_confusion_and_accuracy():
    report = ai.evaluate(_manifest(), _predictions())

    assert report["liveness"]["accuracy"] == 66.67
    assert report["liveness"]["confusion"]["SPOOF"] == {"LIVE": 1, "SPOOF": 1}


def test_face_far_and_frr():
    report = ai.evaluate(_manifest(), _predictions())

    assert report["face"]["false_accept_rate"] == 50.0
    assert report["face"]["false_reject_rate"] == 50.0


def test_latency_average_and_p95():
    report = ai.evaluate(_manifest(), _predictions())

    assert report["latency_ms"]["average"] == 500.0
    assert report["latency_ms"]["p95"] == 880.0
    assert report["latency_ms"]["samples"] == 2


def test_markdown_contains_confusion_matrix():
    markdown = ai.to_markdown(ai.evaluate(_manifest(), _predictions()))

    assert "# AI evaluation report" in markdown
    assert "| SPOOF | 1 | 1 | 0 | 0 |" in markdown
    assert "P95: 880.0 ms" in markdown
