#!/bin/bash

# CS 4240 Project - Run Script
# Usage:
#   run.sh path/to/file.ir [--naive|--greedy]
#   run.sh path/to/file.ir path/to/out.s [--naive|--greedy]
# Produces: the provided out.s path (default <dir(file.ir)>/out.s)

set -euo pipefail

usage() {
  echo "Usage:" >&2
  echo "  $0 path/to/file.ir [--naive|--greedy]" >&2
  echo "  $0 path/to/file.ir path/to/out.s [--naive|--greedy]" >&2
  exit 1
}

if [ $# -eq 2 ]; then
  INPUT_IR="$1"
  OUT_PATH="$(dirname "$1")/out.s"
  MODE="$2"
elif [ $# -eq 3 ]; then
  INPUT_IR="$1"
  OUT_PATH="$2"
  MODE="$3"
else
  usage
fi

case "$MODE" in
  --naive|--greedy) ;;
  *) usage;;
esac

# Run from repo root
cd "$(dirname "$0")"

# Build project
./build.sh

# Generate MIPS assembly via Test2
mkdir -p "$(dirname "$OUT_PATH")"

# Prefer capturing stdout if Test2 prints assembly
set +e
java -cp build/classes Test2 "$INPUT_IR" "$MODE" >"$OUT_PATH"
rc=$?
set -e
if [ $rc -ne 0 ]; then
  echo "Error: codegen failed" >&2
  exit 1
fi

# If capture produced empty file but Test2 wrote a fixed name, fall back
if [ ! -s "$OUT_PATH" ] && [ -s "output.s" ]; then
  cp "output.s" "$OUT_PATH"
fi

# Final sanity check
if [ ! -s "$OUT_PATH" ]; then
  echo "Error: $OUT_PATH was not created or is empty" >&2
  exit 1
fi

echo "Wrote $OUT_PATH"
