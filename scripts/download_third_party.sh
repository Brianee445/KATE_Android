#!/bin/bash
# scripts/download_third_party.sh
# Downloads only TensorFlow Lite and nlohmann/json
# Vosk is handled separately in the release workflow

set -e

echo "========================================"
echo "  Downloading Third-Party Libraries"
echo "========================================"

# Get project root
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
THIRD_PARTY_DIR="$PROJECT_ROOT/app/src/main/cpp/third_party"

mkdir -p "$THIRD_PARTY_DIR"

# ==================== TENSORFLOW LITE ====================
echo ""
echo "📥 Downloading TensorFlow Lite..."
mkdir -p "$THIRD_PARTY_DIR/tflite/lib/arm64-v8a"
mkdir -p "$THIRD_PARTY_DIR/tflite/lib/armeabi-v7a"
mkdir -p "$THIRD_PARTY_DIR/tflite/include"

curl -L -o /tmp/tflite.zip "https://storage.googleapis.com/tensorflow/libtensorflow/libtensorflow-lite-android-2.17.0.zip"
unzip -q /tmp/tflite.zip -d /tmp/tflite/

cp /tmp/tflite/lib/arm64-v8a/libtensorflowlite_c.so "$THIRD_PARTY_DIR/tflite/lib/arm64-v8a/"
cp /tmp/tflite/lib/armeabi-v7a/libtensorflowlite_c.so "$THIRD_PARTY_DIR/tflite/lib/armeabi-v7a/"
cp -r /tmp/tflite/include/tensorflow "$THIRD_PARTY_DIR/tflite/include/"
echo "✅ TensorFlow Lite done"

# ==================== NLOHMANN JSON ====================
echo ""
echo "📥 Downloading nlohmann/json..."
mkdir -p "$THIRD_PARTY_DIR/nlohmann"
curl -L -o "$THIRD_PARTY_DIR/nlohmann/json.hpp" \
    "https://github.com/nlohmann/json/releases/latest/download/json.hpp"
echo "✅ nlohmann/json done"

echo ""
echo "========================================"
echo "  All third-party libraries downloaded!"
echo "========================================"
