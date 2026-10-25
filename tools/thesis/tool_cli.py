"""Shared path policy for the existing document tools; no document processing here."""
import argparse
from datetime import datetime, timezone
from pathlib import Path
from uuid import uuid4

REPOSITORY_ROOT = Path(__file__).resolve().parents[2]


def parse_paths(output_name, *, requires_input=True, argv=None):
    parser = argparse.ArgumentParser(description='Historical thesis tool; output is not an approved requirement or application test.')
    if requires_input:
        parser.add_argument('--input', type=Path, required=True, help='Explicit local DOCX source; never overwritten')
    parser.add_argument('--output', type=Path, help='New DOCX path; defaults to a unique .artifacts/tool-runs directory')
    args = parser.parse_args(argv)
    if requires_input:
        args.input = args.input.resolve()
        if not args.input.is_file():
            parser.error('Input file does not exist; restore/select the intended source explicitly.')
    if args.output is None:
        run_id = datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%SZ') + '-' + uuid4().hex[:8]
        args.output = REPOSITORY_ROOT / '.artifacts' / 'tool-runs' / run_id / output_name
    args.output = args.output.resolve()
    if requires_input and args.output == args.input:
        parser.error('Output must not overwrite the source.')
    if args.output.exists():
        parser.error('Output already exists; choose a new path. No implicit overwrite.')
    args.output.parent.mkdir(parents=True, exist_ok=True)
    return args
