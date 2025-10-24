#!/bin/bash

# Run a compiled MIPS .s program against public_test_cases/<suite>/ {i.in,i.out}
# Usage: run_public_tests.sh path/to/program.s <suite>
#  suite ∈ {quicksort, prime} (or any directory under public_test_cases/ with *.in/*.out pairs)

set -euo pipefail

usage() {
  echo "Usage: $0 path/to/program.s <suite-name>" >&2
  exit 1
}

[ $# -eq 2 ] || usage

PROGRAM_S="$1"
SUITE="$2"

if [ ! -f "$PROGRAM_S" ]; then
  echo "Error: MIPS assembly not found: $PROGRAM_S" >&2
  exit 1
fi

CASEDIR="public_test_cases/$SUITE"
if [ ! -d "$CASEDIR" ]; then
  echo "Error: test suite directory not found: $CASEDIR" >&2
  exit 1
fi

# Run from repo root
cd "$(dirname "$0")"

# Ensure classes exist for the interpreter
if [ ! -d build/classes ]; then
  echo "No build found. Building..."
  ./build.sh
fi

mkdir -p .cache

fail=0
count=0
for inFile in "$CASEDIR"/*.in; do
  [ -e "$inFile" ] || continue
  base="$(basename "$inFile" .in)"
  expFile="$CASEDIR/$base.out"
  outFile=".cache/${SUITE}_$base.out"

  if [ ! -f "$expFile" ]; then
    echo "Skipping $base (missing expected file)"
    continue
  fi

  java -cp build/classes mips.MIPSInterpreter --in "$inFile" "$PROGRAM_S" > "$outFile"
  if diff -u "$expFile" "$outFile" > /dev/null; then
    echo "ok $base"
  else
    echo "FAIL $base"
    fail=$((fail+1))
  fi
  count=$((count+1))
done

if [ "$count" -eq 0 ]; then
  echo "No test cases found in $CASEDIR" >&2
  exit 1
fi

if [ $fail -eq 0 ]; then
  echo "ALL VERIFIED: $SUITE ($count cases)"
else
  echo "SOME FAILED: $SUITE ($fail of $count)"
  exit 1
fi


