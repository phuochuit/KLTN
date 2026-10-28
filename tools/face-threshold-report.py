#!/usr/bin/env python3
"""Đo phân bố điểm SFace trên dữ liệu thật để chọn ngưỡng khuôn mặt.

Cấu trúc thư mục đầu vào: mỗi người một thư mục con, mỗi thư mục >= 2 ảnh.

    face-samples/
      nguoi-a/1.jpg 2.jpg 3.jpg
      nguoi-b/1.jpg 2.jpg
      nguoi-c/1.jpg 2.jpg 3.jpg 4.jpg

Ảnh trong cùng thư mục tạo cặp genuine (cùng người); ảnh ở hai thư mục khác nhau
tạo cặp impostor (khác người). Công cụ tính điểm cosine bằng đúng mô hình
YuNet + SFace mà ANPR service dùng, nên kết quả chuyển thẳng thành
FACE_MATCH_THRESHOLD / FACE_REVIEW_THRESHOLD.

Chạy:

    anpr-service\\.venv\\Scripts\\python.exe tools\\face-threshold-report.py ^
        --input .artifacts\\face-samples ^
        --output .artifacts\\face-threshold-report.md

Không đưa ảnh chân dung thật vào git: để dữ liệu trong .artifacts/ (đã gitignore).
"""

from __future__ import annotations

import argparse
import json
import random
import sys
from itertools import combinations
from pathlib import Path

import cv2
import numpy as np

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "anpr-service"))

from app.face_engine import FaceEngine, MATCH_THRESHOLD, REVIEW_THRESHOLD  # noqa: E402

# Console Windows mặc định dùng cp1258, in tiếng Việt hoặc --help sẽ lỗi UnicodeEncodeError.
for _stream in (sys.stdout, sys.stderr):
    if hasattr(_stream, "reconfigure"):
        _stream.reconfigure(encoding="utf-8")

IMAGE_SUFFIXES = {".jpg", ".jpeg", ".png", ".bmp", ".webp"}


def read_image(path: Path):
    data = np.fromfile(str(path), dtype=np.uint8)
    if data.size == 0:
        return None
    return cv2.imdecode(data, cv2.IMREAD_COLOR)


def collect_people(input_dir: Path, max_per_person: int) -> dict[str, list[Path]]:
    people: dict[str, list[Path]] = {}
    for folder in sorted(p for p in input_dir.iterdir() if p.is_dir()):
        images = sorted(p for p in folder.iterdir() if p.suffix.lower() in IMAGE_SUFFIXES)
        if images:
            people[folder.name] = images[:max_per_person]
    return people


def extract(engine: FaceEngine, images: list[Path]) -> tuple[list, int]:
    vectors, skipped = [], 0
    for path in images:
        image = read_image(path)
        if image is None:
            skipped += 1
            continue
        try:
            feature, _, _ = engine.analyze(image, f"Ảnh {path.name}")
        except Exception:
            skipped += 1
            continue
        vectors.append(feature)
    return vectors, skipped


def cosine(engine: FaceEngine, a, b) -> float:
    return float(engine.recognizer.match(a, b, cv2.FaceRecognizerSF_FR_COSINE))


def stats(values: list[float]) -> dict:
    if not values:
        return {"count": 0}
    ordered = sorted(values)
    return {
        "count": len(ordered),
        "min": round(ordered[0], 4),
        "p05": round(ordered[max(0, int(0.05 * (len(ordered) - 1)))], 4),
        "mean": round(sum(ordered) / len(ordered), 4),
        "p95": round(ordered[int(0.95 * (len(ordered) - 1))], 4),
        "max": round(ordered[-1], 4),
    }


def sweep(genuine: list[float], impostor: list[float]) -> tuple[tuple, list[tuple]]:
    best = None
    rows = []
    threshold = 0.05
    while threshold <= 0.9501:
        tpr = sum(1 for s in genuine if s >= threshold) / len(genuine)
        fpr = sum(1 for s in impostor if s >= threshold) / len(impostor)
        balanced = (tpr + (1 - fpr)) / 2
        row = (round(threshold, 3), round(tpr, 4), round(fpr, 4), round(balanced, 4))
        rows.append(row)
        if best is None or row[3] > best[3]:
            best = row
        threshold += 0.005
    return best, rows


def pick_review_threshold(rows: list[tuple], target_far: float) -> float | None:
    """Ngưỡng thấp nhất còn giữ FPR <= target_far, tức biên dưới của dải REVIEW."""
    candidates = [row[0] for row in rows if row[2] <= target_far]
    return max(candidates) if candidates else None


def render_markdown(report: dict, rows: list[tuple]) -> str:
    g, i = report["genuine"], report["impostor"]
    lines = [
        "# Báo cáo ngưỡng khuôn mặt",
        "",
        f"- Dữ liệu: `{report['input']}`",
        f"- Người: {report['people']} thư mục, {report['images']} ảnh dùng được"
        f" ({report['skipped']} ảnh bỏ qua vì không thấy khuôn mặt)",
        f"- Cặp genuine: {g.get('count', 0)} · cặp impostor: {i.get('count', 0)}",
        f"- Ngưỡng đang dùng: MATCH {report['current']['match']} / REVIEW {report['current']['review']}",
        "",
        "## Phân bố điểm cosine",
        "",
        "| Tập | Số cặp | Min | P05 | Mean | P95 | Max |",
        "| --- | --- | --- | --- | --- | --- | --- |",
    ]
    for name, values in (("Genuine (cùng người)", g), ("Impostor (khác người)", i)):
        lines.append(
            f"| {name} | {values.get('count', 0)} | {values.get('min', '-')} | {values.get('p05', '-')} "
            f"| {values.get('mean', '-')} | {values.get('p95', '-')} | {values.get('max', '-')} |"
        )
    lines += [
        "",
        "## Đề xuất",
        "",
        f"- `FACE_MATCH_THRESHOLD={report['suggested']['match']}`"
        f" (TPR {report['suggested']['tpr']:.1%}, FPR {report['suggested']['fpr']:.1%},"
        f" balanced accuracy {report['suggested']['balanced']:.1%})",
        f"- `FACE_REVIEW_THRESHOLD={report['suggested']['review']}`"
        f" (giữ FPR <= {report['target_far']:.1%} ở biên dưới dải REVIEW)",
        "",
        "Điểm >= MATCH là PASS, từ REVIEW đến dưới MATCH là REVIEW, dưới REVIEW là REJECT.",
        "Nếu hai ngưỡng cách nhau dưới 0.02 thì dữ liệu chưa đủ tách biệt: cần thêm người/điều kiện sáng.",
        "",
        "## Đường cong ngưỡng",
        "",
        "| Ngưỡng | TPR | FPR | Balanced |",
        "| --- | --- | --- | --- |",
    ]
    for threshold, tpr, fpr, balanced in rows:
        if abs(threshold * 100 - round(threshold * 100)) < 1e-9 and int(round(threshold * 100)) % 5 == 0:
            lines.append(f"| {threshold:.3f} | {tpr:.1%} | {fpr:.1%} | {balanced:.1%} |")
    lines += [
        "",
        "Chỉ số này đo trên mẫu do bạn thu thập, không phải cam kết chất lượng trên mọi camera.",
        "Ghi lại ngày, thiết bị, điều kiện sáng khi thu mẫu để so sánh giữa các lần hiệu chỉnh.",
        "",
    ]
    return "\n".join(lines)


def main() -> int:
    parser = argparse.ArgumentParser(description="Đo ngưỡng SFace trên dữ liệu khuôn mặt thật.")
    parser.add_argument("--input", required=True, help="Thư mục gốc, mỗi người một thư mục con")
    parser.add_argument("--output", required=True, help="Đường dẫn file báo cáo .md")
    parser.add_argument("--max-per-person", type=int, default=8, help="Số ảnh tối đa mỗi người (mặc định 8)")
    parser.add_argument("--target-far", type=float, default=0.01, help="FPR mục tiêu cho ngưỡng REVIEW (mặc định 0.01)")
    parser.add_argument("--seed", type=int, default=0, help="Seed khi lấy mẫu cặp impostor")
    args = parser.parse_args()

    input_dir = Path(args.input).expanduser().resolve()
    if not input_dir.is_dir():
        print(f"Không thấy thư mục dữ liệu: {input_dir}", file=sys.stderr)
        return 2

    people = collect_people(input_dir, args.max_per_person)
    if len(people) < 2:
        print("Cần ít nhất 2 thư mục người, mỗi thư mục chứa ảnh khuôn mặt.", file=sys.stderr)
        return 2

    engine = FaceEngine()
    vectors, skipped_total, usable = {}, 0, {}
    for name, images in people.items():
        vecs, skipped = extract(engine, images)
        skipped_total += skipped
        if len(vecs) >= 2:
            usable[name] = vecs

    if len(usable) < 2:
        print(
            "Không đủ ảnh có khuôn mặt dùng được (cần >= 2 người, mỗi người >= 2 ảnh).",
            file=sys.stderr,
        )
        return 2

    genuine = [cosine(engine, a, b) for vecs in usable.values() for a, b in combinations(vecs, 2)]
    impostor = [
        cosine(engine, a, b)
        for (_, first), (_, second) in combinations(usable.items(), 2)
        for a in first
        for b in second
    ]
    if not genuine or not impostor:
        print("Không tạo được đủ cặp genuine/impostor.", file=sys.stderr)
        return 2

    best, rows = sweep(genuine, impostor)
    review = pick_review_threshold(rows, args.target_far)
    if review is None or review >= best[0]:
        review = round(max(0.05, best[0] - 0.063), 3)

    report = {
        "input": str(input_dir),
        "people": len(usable),
        "images": sum(len(v) for v in usable.values()),
        "skipped": skipped_total,
        "genuine": stats(genuine),
        "impostor": stats(impostor),
        "current": {"match": MATCH_THRESHOLD, "review": REVIEW_THRESHOLD},
        "suggested": {
            "match": best[0],
            "tpr": best[1],
            "fpr": best[2],
            "balanced": best[3],
            "review": review,
        },
        "target_far": args.target_far,
        "seed": args.seed,
    }

    output = Path(args.output).expanduser().resolve()
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(render_markdown(report, rows), encoding="utf-8")
    output.with_suffix(".json").write_text(
        json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8"
    )

    print(
        f"{len(usable)} người, {report['images']} ảnh, {len(genuine)} cặp genuine, "
        f"{len(impostor)} cặp impostor"
    )
    print(
        f"Đề xuất MATCH={best[0]} (TPR {best[1]:.1%}, FPR {best[2]:.1%}), REVIEW={review}"
    )
    print(f"Báo cáo: {output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
