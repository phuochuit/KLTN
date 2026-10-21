from __future__ import annotations

import hmac
import os


TOKEN_HEADER = "x-parking-service-token"
PUBLIC_PATHS = frozenset({"/health", "/favicon.ico"})


def configured_tokens() -> tuple[str, ...]:
    values = (
        os.getenv("PARKING_INTERNAL_SERVICE_TOKEN", "").strip(),
        os.getenv("PARKING_INTERNAL_PREVIOUS_TOKEN", "").strip(),
    )
    return tuple(value for value in values if value)


def token_is_valid(candidate: str | None, tokens: tuple[str, ...] | None = None) -> bool:
    if candidate is None or len(candidate) > 512:
        return False
    accepted = configured_tokens() if tokens is None else tokens
    # Evaluate every configured token so the matching position is not exposed.
    matched = False
    for expected in accepted:
        matched = hmac.compare_digest(candidate, expected) or matched
    return matched
