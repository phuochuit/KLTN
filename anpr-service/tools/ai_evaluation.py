"""Repeatable AI evaluation from a labelled manifest and a prediction file.

The evaluator is model agnostic: it consumes ground truth (``manifest.csv``)
and the output produced by a recognition run (``predictions.csv``), then
reports detection, OCR, classification, liveness and face metrics together
with latency.

Manifest columns::

    sample_id,kind,image_path,expected_plate,expected_vehicle,
    expected_liveness,expected_decision,condition

``kind`` is ``vehicle``, ``liveness`` or ``face``.

Prediction columns::

    sample_id,plate_text,vehicle_type,liveness_decision,face_decision,elapsed_ms

Usage::

    python tools/ai_evaluation.py --manifest manifest.csv \
        --predictions predictions.csv --out reports/ai
"""
from __future__ import annotations

import argparse
import csv
import math
from collections import defaultdict
from datetime import datetime, timezone
from pathlib import Path

LIVENESS_LABELS = ("LIVE", "SPOOF", "REVIEW", "UNKNOWN")


def _read_csv(path: Path) -> list[dict[str, str]]:
    with path.open(encoding="utf-8-sig", newline="") as handle:
        return [{(key or "").strip(): (value or "").strip()
                 for key, value in row.items()} for row in csv.DictReader(handle)]


def _normalize_plate(value: str) -> str:
    return "".join(character for character in (value or "").upper()
                   if character.isalnum())


def _percent(numerator: int, denominator: int) -> float:
    return round(100.0 * numerator / denominator, 2) if denominator else 0.0


def _p95(values: list[float]) -> float:
    if not values:
        return 0.0
    ordered = sorted(values)
    index = max(0, min(len(ordered) - 1, math.ceil(0.95 * len(ordered)) - 1))
    return round(ordered[index], 1)


def evaluate(manifest: list[dict[str, str]],
             predictions: list[dict[str, str]]) -> dict:
    predicted = {row.get("sample_id", ""): row for row in predictions}
    vehicle = [row for row in manifest if row.get("kind") == "vehicle"]
    liveness = [row for row in manifest if row.get("kind") == "liveness"]
    face = [row for row in manifest if row.get("kind") == "face"]

    detected = exact = type_ok = 0
    for row in vehicle:
        prediction = predicted.get(row.get("sample_id", ""))
        if prediction is None:
            continue
        text = _normalize_plate(prediction.get("plate_text", ""))
        if text:
            detected += 1
        expected_plate = _normalize_plate(row.get("expected_plate", ""))
        if text and expected_plate and text == expected_plate:
            exact += 1
        expected_type = (row.get("expected_vehicle") or "").upper()
        if expected_type and (prediction.get("vehicle_type") or "").upper() == expected_type:
            type_ok += 1

    confusion = defaultdict(lambda: defaultdict(int))
    correct = 0
    for row in liveness:
        prediction = predicted.get(row.get("sample_id", ""))
        expected = (row.get("expected_liveness") or "UNKNOWN").upper()
        actual = ((prediction or {}).get("liveness_decision") or "UNKNOWN").upper()
        if actual not in LIVENESS_LABELS:
            actual = "UNKNOWN"
        confusion[expected][actual] += 1
        if expected == actual:
            correct += 1

    false_accept = false_reject = face_pass = face_reject = 0
    for row in face:
        prediction = predicted.get(row.get("sample_id", ""))
        if prediction is None:
            continue
        expected = (row.get("expected_decision") or "").upper()
        actual = (prediction.get("face_decision") or "").upper()
        if expected == "PASS":
            face_pass += 1
            if actual != "PASS":
                false_reject += 1
        elif expected in {"REJECT", "FAIL", "NO_MATCH"}:
            face_reject += 1
            if actual == "PASS":
                false_accept += 1

    latencies = []
    for row in predictions:
        try:
            latencies.append(float(row.get("elapsed_ms", "")))
        except ValueError:
            continue

    return {
        "generated_at": datetime.now(timezone.utc).isoformat(),
        "counts": {"manifest": len(manifest), "predictions": len(predictions),
                   "vehicle": len(vehicle), "liveness": len(liveness),
                   "face": len(face)},
        "vehicle": {
            "plate_detection_rate": _percent(detected, len(vehicle)),
            "ocr_exact_match": _percent(exact, len(vehicle)),
            "vehicle_type_accuracy": _percent(type_ok, len(vehicle)),
        },
        "liveness": {
            "accuracy": _percent(correct, len(liveness)),
            "confusion": {expected: dict(actual)
                          for expected, actual in confusion.items()},
        },
        "face": {
            "false_accept_rate": _percent(false_accept, face_reject),
            "false_reject_rate": _percent(false_reject, face_pass),
        },
        "latency_ms": {
            "average": round(sum(latencies) / len(latencies), 1) if latencies else 0.0,
            "p95": _p95(latencies),
            "samples": len(latencies),
        },
    }


def to_markdown(report: dict) -> str:
    counts = report["counts"]
    vehicle = report["vehicle"]
    liveness = report["liveness"]
    face = report["face"]
    latency = report["latency_ms"]
    lines = [
        "# AI evaluation report",
        "",
        f"Generated: {report['generated_at']}",
        "",
        "## Dataset",
        "",
        "| Item | Count |",
        "|---|---|",
        f"| Manifest rows | {counts['manifest']} |",
        f"| Predictions | {counts['predictions']} |",
        f"| Vehicle samples | {counts['vehicle']} |",
        f"| Liveness samples | {counts['liveness']} |",
        f"| Face samples | {counts['face']} |",
        "",
        "## Metrics",
        "",
        "| Metric | Value (%) |",
        "|---|---|",
        f"| Plate detection rate | {vehicle['plate_detection_rate']} |",
        f"| OCR exact match | {vehicle['ocr_exact_match']} |",
        f"| Vehicle type accuracy | {vehicle['vehicle_type_accuracy']} |",
        f"| Liveness accuracy | {liveness['accuracy']} |",
        f"| Face false accept rate | {face['false_accept_rate']} |",
        f"| Face false reject rate | {face['false_reject_rate']} |",
        "",
        "## Latency",
        "",
        f"- Average: {latency['average']} ms",
        f"- P95: {latency['p95']} ms",
        f"- Samples: {latency['samples']}",
        "",
        "## Liveness confusion matrix",
        "",
        "| Expected \\ Predicted | " + " | ".join(LIVENESS_LABELS) + " |",
        "|---" * (len(LIVENESS_LABELS) + 1) + "|",
    ]
    for expected in LIVENESS_LABELS:
        row = liveness["confusion"].get(expected)
        if row is None:
            continue
        cells = " | ".join(str(row.get(label, 0)) for label in LIVENESS_LABELS)
        lines.append(f"| {expected} | {cells} |")
    return "\n".join(lines) + "\n"


def write_report(report: dict, output: Path) -> tuple[Path, Path]:
    output.mkdir(parents=True, exist_ok=True)
    markdown = output / "ai-report.md"
    markdown.write_text(to_markdown(report), encoding="utf-8")
    metrics = output / "ai-metrics.csv"
    with metrics.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.writer(handle)
        writer.writerow(["metric", "value"])
        writer.writerow(["plate_detection_rate", report["vehicle"]["plate_detection_rate"]])
        writer.writerow(["ocr_exact_match", report["vehicle"]["ocr_exact_match"]])
        writer.writerow(["vehicle_type_accuracy", report["vehicle"]["vehicle_type_accuracy"]])
        writer.writerow(["liveness_accuracy", report["liveness"]["accuracy"]])
        writer.writerow(["face_false_accept_rate", report["face"]["false_accept_rate"]])
        writer.writerow(["face_false_reject_rate", report["face"]["false_reject_rate"]])
        writer.writerow(["latency_average_ms", report["latency_ms"]["average"]])
        writer.writerow(["latency_p95_ms", report["latency_ms"]["p95"]])
    return markdown, metrics


def main() -> int:
    parser = argparse.ArgumentParser(description="Evaluate ANPR and face predictions")
    parser.add_argument("--manifest", type=Path, required=True)
    parser.add_argument("--predictions", type=Path, required=True)
    parser.add_argument("--out", type=Path, default=Path("reports/ai"))
    arguments = parser.parse_args()
    report = evaluate(_read_csv(arguments.manifest), _read_csv(arguments.predictions))
    markdown, metrics = write_report(report, arguments.out)
    print(f"Markdown: {markdown}")
    print(f"Metrics:  {metrics}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
