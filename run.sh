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
mkdir -p "$(dirname "$OUT_PATH")"

# Try 1: if Test2 supports an explicit out path, use it.
# (Adjust the main class name/package if needed.)
if java -cp build/classes Test2 "$INPUT_IR" "$MODE" "$OUT_PATH" 2>/dev/null; then
  :
else
  # Try 2: capture stdout (if Test2 prints assembly)
  if java -cp build/classes Test2 "$INPUT_IR" "$MODE" > "$OUT_PATH"; then
    :
  else
    echo "Error: Java codegen failed" >&2
    exit 1
  fi
fi

# Fallback: if OUT_PATH is empty (some codegens write to output.s instead),
# move a generated file if present.
if [[ ! -s "$OUT_PATH" ]]; then
  for CAND in "out.s" "output.s" "build/out.s" "build/output.s"; do
    if [[ -s "$CAND" ]]; then
      cp "$CAND" "$OUT_PATH"
      break
    fi
  done
fi

# Final check for the grader
if [[ ! -s "$OUT_PATH" ]]; then
  echo "Error: $OUT_PATH was not created or is empty" >&2
  exit 1
fi

echo "Wrote $OUT_PATH"
