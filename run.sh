#!/usr/bin/env bash
# run.sh — Start the OS Smart Print Server
# Requires: Java 17+ on PATH
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
JAR="$SCRIPT_DIR/smart-print-server.jar"

if [ ! -f "$JAR" ]; then
  echo "❌  smart-print-server.jar not found."
  echo "    Run ./build.sh first to create the production build."
  exit 1
fi

echo "🖨️  Starting OS Smart Print Server..."
echo "   Dashboard → http://localhost:8080"
echo "   Press Ctrl+C to stop."
echo ""
exec java -jar "$JAR" --server.port=8080

