#!/bin/bash

# CS 4240 Project - Run Script
# Usage: run.sh path/to/file.ir [--naive|--greedy]
# Produces: out.s

set -euo pipefail

usage() {
  echo "Usage: $0 path/to/file.ir [--naive|--greedy]" >&2
  exit 1
}

[ $# -eq 2 ] || usage

INPUT_IR="$1"
MODE="$2"

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

cp output.s out.s
echo "Wrote out.s"
