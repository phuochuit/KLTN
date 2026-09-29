from app import classify, make_decision


def test_classify_three_bands():
    assert classify(0.50) == "MATCH"
    assert classify(0.32) == "REVIEW"
    assert classify(0.20) == "NO_MATCH"


def test_pass_when_all_pairs_match():
    decision, _ = make_decision(0.50, 0.60, 0.55, False)
    assert decision == "PASS"


def test_reject_when_live_does_not_match_registration():
    decision, _ = make_decision(0.50, 0.20, 0.55, False)
    assert decision == "REJECT"


def test_review_when_quality_warning_exists():
    decision, _ = make_decision(0.50, 0.60, 0.55, True)
    assert decision == "REVIEW"

