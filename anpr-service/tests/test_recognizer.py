from app.recognizer import normalize_plate, vehicle_type_from_detections


def test_normalize_plate():
    assert normalize_plate("59-A1 123.45") == "59A112345"


def test_vehicle_type_prefers_highest_confidence():
    assert vehicle_type_from_detections([("car", 0.4), ("motorcycle", 0.8)]) == ("MOTORBIKE", 0.8)
