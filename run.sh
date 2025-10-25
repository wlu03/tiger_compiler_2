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

# Run codegen in the output directory so Test2's output.s lands there
# Compute absolute path to input IR (portable realpath)
ABS_INPUT_DIR="$(cd -- "$(dirname "$INPUT_IR")" && pwd)"
ABS_INPUT_IR="$ABS_INPUT_DIR/$(basename "$INPUT_IR")"

(
  cd "$OUT_DIR"
  java -cp "$SCRIPT_DIR/build/classes" Test2 "$ABS_INPUT_IR" "$MODE"
)

# Final check for the grader
if [[ ! -s "$OUT_PATH" ]]; then
  echo "Error: $OUT_PATH was not created or is empty" >&2
  exit 1
fi

echo "Wrote $OUT_PATH"
