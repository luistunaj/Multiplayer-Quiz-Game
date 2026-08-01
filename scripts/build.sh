#!/usr/bin/env bash
set -e
cd "$(dirname "$0")/.."
javac -d out $(find src/main/java -name "*.java")
echo "built -> out/"
