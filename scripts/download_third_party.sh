#!/bin/bash
# scripts/download_third_party.sh
# Simplified - TFLite and nlohmann/json removed
# TFLite is now handled via Gradle dependencies

set -e

echo "========================================"
echo "  Kate - Third-Party Library Setup"
echo "========================================"

# Get project root
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
THIRD_PARTY_DIR="$PROJECT_ROOT/app/src/main/cpp/third_party"

echo ""
echo "📁 Project root: $PROJECT_ROOT"
echo "📁 Third-party dir: $THIRD_PARTY_DIR"

# ==================== CLEAN UP OLD FILES ====================
echo ""
echo "🧹 Cleaning up old third-party files..."

# Remove TFLite and nlohmann from third_party
rm -rf "$THIRD_PARTY_DIR/tflite" 2>/dev/null || true
rm -rf "$THIRD_PARTY_DIR/nlohmann" 2>/dev/null || true

echo "✅ Cleanup complete"

# ==================== CREATE DIRECTORY STRUCTURE ====================
echo ""
echo "📁 Creating directory structure..."

# Only keep Vosk directory
mkdir -p "$THIRD_PARTY_DIR/vosk/lib/arm64-v8a"
mkdir -p "$THIRD_PARTY_DIR/vosk/lib/armeabi-v7a"
mkdir -p "$THIRD_PARTY_DIR/vosk/include"

echo "✅ Directories created"

# ==================== VOSK (Only if not already present) ====================
echo ""
echo "📥 Checking Vosk libraries..."

VOSK_ARM64="$THIRD_PARTY_DIR/vosk/lib/arm64-v8a/libvosk.so"
VOSK_ARMEABI="$THIRD_PARTY_DIR/vosk/lib/armeabi-v7a/libvosk.so"
VOSK_HEADER="$THIRD_PARTY_DIR/vosk/include/vosk_api.h"

if [ -f "$VOSK_ARM64" ] && [ -f "$VOSK_ARMEABI" ] && [ -f "$VOSK_HEADER" ]; then
    echo "✅ Vosk libraries already present"
    echo "   arm64-v8a: $(ls -lh $VOSK_ARM64 | awk '{print $5}')"
    echo "   armeabi-v7a: $(ls -lh $VOSK_ARMEABI | awk '{print $5}')"
else
    echo "⚠️ Vosk libraries not found or incomplete"
    echo "   They will be downloaded during the build process"
    echo "   (Vosk is handled by the release workflow)"
fi

# ==================== SUMMARY ====================
echo ""
echo "========================================"
echo "  ✅ Third-party setup complete!"
echo "========================================"
echo ""
echo "📁 Directory structure:"
echo "   $THIRD_PARTY_DIR"
echo "   └── vosk/"
echo "       ├── include/"
echo "       │   └── vosk_api.h"
echo "       └── lib/"
echo "           ├── arm64-v8a/"
echo "           │   └── libvosk.so"
echo "           └── armeabi-v7a/"
echo "               └── libvosk.so"
echo ""
echo "ℹ️  TFLite is now managed via Gradle dependencies:"
echo "   implementation 'org.tensorflow:tensorflow-lite:2.17.0'"
echo "   implementation 'org.tensorflow:tensorflow-lite-support:0.4.4'"
echo ""
echo "ℹ️  JSON handling: Model training will output JSON files"
echo "   (same approach as COLAB for model metadata)"
echo "========================================"
