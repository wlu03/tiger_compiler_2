#!/usr/bin/env bash
set -euo pipefail

usage() {
  echo "Usage:" >&2
  echo "  $0 path/to/file.ir path/to/out.s [--naive|--greedy]" >&2
  echo "  $0 path/to/file.ir [--naive|--greedy]   # writes <dir(file.ir)>/out.s" >&2
  exit 1
}

# Parse args
if [[ $# -eq 2 ]]; then
  INPUT_IR="$1"
  MODE="$2"
  OUT_PATH="$(dirname "$INPUT_IR")/out.s"
elif [[ $# -eq 3 ]]; then
  INPUT_IR="$1"
  OUT_PATH="$2"
  MODE="$3"
else
  usage
fi

case "$MODE" in
  --naive|--greedy) ;;
  *) usage ;;
esac

# Run from repo root (folder containing this script)
SCRIPT_DIR="$(cd -- "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

# Build
./build.sh

# Ensure output directory exists
OUT_DIR="$(dirname "$OUT_PATH")"
mkdir -p "$OUT_DIR"

# Absolute path to input IR (portable realpath)
ABS_INPUT_DIR="$(cd -- "$(dirname "$INPUT_IR")" && pwd)"
ABS_INPUT_IR="$ABS_INPUT_DIR/$(basename "$INPUT_IR")"

# Run codegen in OUT_DIR so Java drops its default 'out.s'/'output.s' there
(
  cd "$OUT_DIR"
  java -cp "$SCRIPT_DIR/build/classes" Test2 "$ABS_INPUT_IR" "$MODE"
)

# ── Normalize the produced file name to the requested OUT_PATH ────────────────
# Your Java may create one of several names; pick the first that exists.
CANDIDATES=(
  "$OUT_DIR/out.s"
  "$OUT_DIR/output.s"
  "$OUT_DIR/tmp.s"
)

FOUND=""
for f in "${CANDIDATES[@]}"; do
  if [[ -s "$f" ]]; then
    FOUND="$f"
    break
  fi
done

if [[ -z "$FOUND" ]]; then
  echo "Error: codegen did not produce an assembly file in $OUT_DIR" >&2
  exit 1
fi

# If the found file isn't exactly the requested OUT_PATH, move it there.
if [[ "$FOUND" != "$OUT_PATH" ]]; then
  # If OUT_PATH exists and is different, overwrite to satisfy the grader.
  mv -f "$FOUND" "$OUT_PATH"
fi

# Final guard for the grader
if [[ ! -s "$OUT_PATH" ]]; then
  echo "Error: $OUT_PATH was not created or is empty" >&2
  exit 1
fi

# Convenience copy to repo root (optional)
ROOT_OUT="$SCRIPT_DIR/out.s"
if [[ "$OUT_PATH" != "$ROOT_OUT" ]]; then
  cp -f "$OUT_PATH" "$ROOT_OUT"
fi

echo "Wrote $OUT_PATH"
echo "Wrote $ROOT_OUT"
