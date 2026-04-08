#!/usr/bin/env bash
# =============================================================================
#  DITA Specialization Designer — Unix/Mac Deploy Script
#  Usage: ./scripts/deploy.sh [target-dir]
#
#  Builds the project and installs the distribution to target-dir
#  (default: deploy/). Optionally launches the application.
# =============================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
TARGET_DIR="${1:-${PROJECT_DIR}/deploy}"

echo ""
echo "========================================================"
echo "  DITA Specialization Designer — Deploy"
echo "========================================================"
echo "  Project : ${PROJECT_DIR}"
echo "  Target  : ${TARGET_DIR}"
echo "========================================================"

# ── Step 1: Build ─────────────────────────────────────────────────────────────
echo ""
echo "[1/3] Building project (ci task)..."
"${PROJECT_DIR}/gradlew" --project-dir "${PROJECT_DIR}" ci
echo "      Build OK."

# ── Step 2: Install distribution ──────────────────────────────────────────────
echo ""
echo "[2/3] Installing distribution to ${TARGET_DIR}..."
"${PROJECT_DIR}/gradlew" --project-dir "${PROJECT_DIR}" deploy
echo "      Install OK."

# ── Step 3: Make launcher executable ──────────────────────────────────────────
BIN_DIR="${TARGET_DIR}/bin"
if [ -d "${BIN_DIR}" ]; then
    chmod +x "${BIN_DIR}"/*
fi

# ── Summary ───────────────────────────────────────────────────────────────────
echo ""
echo "========================================================"
echo "  Deployment complete!"
FAT_JAR=$(find "${PROJECT_DIR}/build/libs" -name "*-all.jar" 2>/dev/null | head -1)
DIST_ZIP=$(find "${PROJECT_DIR}/build/distributions" -name "*.zip" 2>/dev/null | head -1)
echo "  Fat JAR : ${FAT_JAR:-n/a}"
echo "  Dist ZIP: ${DIST_ZIP:-n/a}"
echo "  Install : ${TARGET_DIR}"
echo "========================================================"
echo ""
echo "  To launch:"
echo "    ${TARGET_DIR}/bin/dita-specialization-designer"
echo ""

# Optional launch
if [ -t 0 ]; then
    read -r -p "Launch application now? (y/N): " LAUNCH
    if [[ "${LAUNCH}" =~ ^[Yy]$ ]]; then
        "${TARGET_DIR}/bin/dita-specialization-designer" &
    fi
fi
