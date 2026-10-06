from app.recognizer import AnprRecognizer, normalize_plate, plate_format_score, vehicle_type_from_detections


def test_normalize_plate():
    assert normalize_plate("59-A1 123.45") == "59A112345"


def test_vehicle_type_prefers_highest_confidence():
    assert vehicle_type_from_detections([("car", 0.4), ("motorcycle", 0.8)]) == ("MOTORBIKE", 0.8)


def test_vietnamese_plate_format_prefers_plausible_result():
    assert plate_format_score("59F241578") > plate_format_score("594F241578")


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
