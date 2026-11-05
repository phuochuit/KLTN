import numpy as np

from app.recognizer import (
    AnprRecognizer,
    best_two_wheeler_type,
    canonicalize_plate,
    normalize_plate,
    plate_format_score,
    plate_shape_is_plausible,
    vehicle_type_from_detections,
    vehicle_type_near_plate,
)


def test_normalize_plate():
    assert normalize_plate("59-A1 123.45") == "59A112345"


def test_canonicalize_plate_removes_inserted_series_prefix_letter():
    assert canonicalize_plate("59HF241578") == "59F241578"


def test_canonicalize_plate_keeps_standard_plate_unchanged():
    assert canonicalize_plate("59F241578") == "59F241578"


def test_canonicalize_plate_corrects_position_safe_ocr_confusions():
    assert canonicalize_plate("3OF55775") == "30F55775"
    assert canonicalize_plate("30F55Z75") == "30F55275"


def test_canonicalize_plate_does_not_guess_between_numeric_digits():
    assert canonicalize_plate("30F55275") == "30F55275"


def test_vehicle_type_prefers_highest_confidence():
    assert vehicle_type_from_detections([("car", 0.4), ("motorcycle", 0.8)]) == ("MOTORBIKE", 0.8)


def test_vehicle_type_detects_bicycle_group():
    assert vehicle_type_from_detections([
        ("bicycle", 0.91), ("motorcycle", 0.25)
    ]) == ("BICYCLE_ELECTRIC_BICYCLE", 0.91)


def test_vehicle_type_ignores_unrelated_car_away_from_plate():
    plate = np.array([400, 400, 500, 500])
    detections = [
        ("car", 0.99, np.array([0, 0, 300, 300])),
        ("motorcycle", 0.72, np.array([350, 200, 560, 650])),
    ]
    assert vehicle_type_near_plate(detections, plate) == ("MOTORBIKE", 0.72)


def test_square_plate_rejects_conflicting_car_prediction():
    plate = np.array([400, 400, 500, 500])
    detections = [("car", 0.95, np.array([300, 150, 600, 700]))]
    assert vehicle_type_near_plate(detections, plate) == ("UNKNOWN", 0.0)


def test_vehicle_fallback_can_be_computed_before_a_plate_is_found():
    detections = [
        ("car", 0.45, np.array([0, 0, 100, 100])),
        ("motorcycle", 0.81, np.array([120, 20, 260, 300])),
    ]
    summary = [(name, score) for name, score, _ in detections]
    assert vehicle_type_from_detections(summary) == ("MOTORBIKE", 0.81)


def test_vietnamese_plate_format_prefers_plausible_result():
    assert plate_format_score("59F241578") > plate_format_score("594F241578")


def test_square_two_line_plate_rejects_incomplete_reading():
    assert not plate_shape_is_plausible("59A1122", 1.4)
    assert plate_shape_is_plausible("59A311122", 1.4)


def test_wide_plate_keeps_legacy_short_format():
    assert plate_shape_is_plausible("30A1234", 3.2)


def test_two_line_ocr_tokens_are_ordered_by_row_then_column():
    def token(text, confidence, left, top, right, bottom):
        return ([[left, top], [right, top], [right, bottom], [left, bottom]], text, confidence)

    # EasyOCR có thể trả token không theo thứ tự đọc.
    results = [
        token("41578", 0.95, 20, 70, 180, 110),
        token("F2", 0.90, 90, 10, 180, 50),
        token("59", 0.92, 20, 12, 80, 52),
    ]
    ordered = AnprRecognizer._order_ocr_results(results)
    assert "".join(item[1] for item in ordered) == "59F241578"


def test_car_prediction_on_square_plate_is_corrected_to_two_wheeler():
    plate = np.array([100, 400, 220, 480])
    detections = [
        ("car", 0.55, np.array([40, 200, 300, 700])),
        ("motorcycle", 0.45, np.array([40, 200, 300, 700])),
    ]
    assert vehicle_type_near_plate(detections, plate) == ("MOTORBIKE", 0.45)


def test_car_prediction_on_wide_plate_covering_body_is_a_two_wheeler():
    plate = np.array([100, 400, 220, 440])
    detections = [("car", 0.40, np.array([80, 150, 260, 700]))]
    # Biển rộng 120 trên thân xe 180 (67%): ô tô thật không có tỉ lệ này.
    assert vehicle_type_near_plate(detections, plate) == ("UNKNOWN", 0.0)


def test_wide_plate_small_relative_to_body_is_car():
    plate = np.array([400, 400, 500, 437])
    detections = [("motorcycle", 0.60, np.array([300, 100, 700, 600]))]
    assert vehicle_type_near_plate(detections, plate) == ("CAR", 0.6)


def test_best_two_wheeler_prefers_bicycle_when_scores_are_close():
    assert best_two_wheeler_type([
        ("bicycle", 0.42), ("motorcycle", 0.40)
    ]) == ("BICYCLE_ELECTRIC_BICYCLE", 0.42)


def test_best_two_wheeler_prefers_motorcycle_when_it_is_clearer():
    assert best_two_wheeler_type([
        ("bicycle", 0.30), ("motorcycle", 0.65)
    ]) == ("MOTORBIKE", 0.65)
