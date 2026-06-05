#!/usr/bin/env bash
# build.sh — Produces a single self-contained JAR:
#   java -jar smart-print-server.jar
#
# What it does:
#   1. Build the React frontend with Vite
#   2. Copy the dist/ into Spring Boot's static resources
#   3. Package a fat JAR (backend + frontend + browser-launcher)
#   4. Symlink a friendly name at the project root

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
FRONTEND_DIR="$SCRIPT_DIR/frontend"
BACKEND_DIR="$SCRIPT_DIR/backend"
STATIC_DIR="$BACKEND_DIR/src/main/resources/static"
MVN="$SCRIPT_DIR/apache-maven-3.9.16/bin/mvn"

echo ""
echo "╔══════════════════════════════════════════════╗"
echo "║   OS Smart Print Server — Production Build   ║"
echo "╚══════════════════════════════════════════════╝"
echo ""

# ── Step 1: Frontend ──────────────────────────────────────────────────────────
echo "▶  [1/3] Building React frontend..."
cd "$FRONTEND_DIR"
npm install --silent
npm run build -- --logLevel warn

# ── Step 2: Copy dist → static resources ─────────────────────────────────────
echo "▶  [2/3] Embedding frontend into Spring Boot static resources..."
rm -rf "$STATIC_DIR"
mkdir -p "$STATIC_DIR"
cp -r "$FRONTEND_DIR/dist/." "$STATIC_DIR/"
echo "   Copied $(find "$STATIC_DIR" -type f | wc -l) files to $STATIC_DIR"

# ── Step 3: Package fat JAR ───────────────────────────────────────────────────
echo "▶  [3/3] Packaging Spring Boot fat JAR..."
cd "$BACKEND_DIR"
"$MVN" package -DskipTests -q

JAR=$(ls "$BACKEND_DIR/target/smart-print-server-"*.jar 2>/dev/null | grep -v 'sources\|javadoc\|tests' | head -1)
DEST="$SCRIPT_DIR/smart-print-server.jar"
cp "$JAR" "$DEST"

echo ""
echo "╔══════════════════════════════════════════════════════╗"
echo "║  ✅  Build complete!                                 ║"
echo "║                                                      ║"
echo "║  Run:  java -jar smart-print-server.jar              ║"
echo "║  URL:  http://localhost:8080                         ║"
echo "║  (browser opens automatically on startup)            ║"
echo "╚══════════════════════════════════════════════════════╝"
echo ""
