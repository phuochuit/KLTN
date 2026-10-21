from app.internal_auth import token_is_valid


def test_accepts_current_and_previous_tokens():
    tokens = ("current-token-with-sufficient-entropy", "previous-token-for-rotation")
    assert token_is_valid(tokens[0], tokens)
    assert token_is_valid(tokens[1], tokens)


def test_rejects_missing_wrong_and_oversized_tokens():
    tokens = ("current-token-with-sufficient-entropy",)
    assert not token_is_valid(None, tokens)
    assert not token_is_valid("wrong-token", tokens)
    assert not token_is_valid("x" * 513, tokens)
