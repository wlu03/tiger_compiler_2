#!/bin/bash

# CS 4240 Project - Run Script
# Usage:
#   run.sh path/to/file.ir path/to/out.s [--naive|--greedy]
#   run.sh path/to/file.ir [--naive|--greedy]   # defaults output to ./out.s
# Produces: the provided out.s path (default ./out.s)

set -euo pipefail

usage() {
  echo "Usage: $0 path/to/file.ir path/to/out.s [--naive|--greedy]" >&2
  echo "   or: $0 path/to/file.ir [--naive|--greedy]  # writes ./output.s" >&2
  exit 1
}

if [ $# -eq 2 ]; then
  INPUT_IR="$1"
  OUT_PATH="output.s"
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

# Select allocator
if [ "$MODE" = "--greedy" ]; then
  export ALLOCATOR=block
else
  unset ALLOCATOR || true
fi

# Generate MIPS assembly via Test2 (honors input IR and allocator flag)
java -cp build/classes Test2 "$INPUT_IR" "$MODE"

# Normalize output filename to out.s
if [ ! -f output.s ]; then
  echo "Error: expected output.s not found after generation" >&2
  exit 1
fi

# Ensure destination directory exists and copy
mkdir -p "$(dirname "$OUT_PATH")"
cp output.s "$OUT_PATH"
echo "Wrote $OUT_PATH"
