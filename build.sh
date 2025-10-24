
#!/bin/bash

# CS 4240 Project - Build Script
# Compiles all Java sources under src/ into build/classes

set -e  # Exit on any error

# Ensure we run from the repo root (script's directory)
cd "$(dirname "$0")"

echo "Building Java project..."

# Remove existing classes if present
if [ -d "build/classes" ]; then
    echo "Removing existing build/classes..."
    rm -rf build/classes
fi

# Create build directories
if [ ! -d "build" ]; then
    mkdir build
    echo "Created build directory"
fi
mkdir -p build/classes

# Discover sources
echo "Finding Java source files..."
find src -name "*.java" > sources.txt

# Compile
echo "Compiling Java files..."
javac -d build/classes @sources.txt

echo "Build completed successfully."
echo "Compiled classes are in build/classes/"

# Cleanup
rm -f sources.txt
echo "Build script completed."