#!/bin/bash
# scripts/download_third_party.sh

set -e

echo "========================================"
echo "  Downloading Third-Party Libraries"
echo "========================================"

# Get project root
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
THIRD_PARTY_DIR="$PROJECT_ROOT/app/src/main/cpp/third_party"

mkdir -p "$THIRD_PARTY_DIR"

# ==================== VOSK ====================
echo ""
echo "📥 Downloading Vosk..."
mkdir -p "$THIRD_PARTY_DIR/vosk/lib/arm64-v8a"
mkdir -p "$THIRD_PARTY_DIR/vosk/lib/armeabi-v7a"
mkdir -p "$THIRD_PARTY_DIR/vosk/include"

curl -L -o /tmp/vosk.zip "https://github.com/alphacep/vosk-api/releases/download/v0.3.45/vosk-android-0.3.45.zip"
unzip -q /tmp/vosk.zip -d /tmp/vosk/

cp /tmp/vosk/lib/android/arm64-v8a/libvosk.so "$THIRD_PARTY_DIR/vosk/lib/arm64-v8a/"
cp /tmp/vosk/lib/android/armeabi-v7a/libvosk.so "$THIRD_PARTY_DIR/vosk/lib/armeabi-v7a/"
cp /tmp/vosk/src/vosk_api.h "$THIRD_PARTY_DIR/vosk/include/"
echo "✅ Vosk done"

# ==================== TENSORFLOW LITE ====================
echo ""
echo "📥 Downloading TensorFlow Lite..."
mkdir -p "$THIRD_PARTY_DIR/tflite/lib/arm64-v8a"
mkdir -p "$THIRD_PARTY_DIR/tflite/lib/armeabi-v7a"

curl -L -o /tmp/tflite.zip "https://storage.googleapis.com/tensorflow/libtensorflow/libtensorflow-lite-android-2.17.0.zip"
unzip -q /tmp/tflite.zip -d /tmp/tflite/

cp /tmp/tflite/lib/arm64-v8a/libtensorflowlite_c.so "$THIRD_PARTY_DIR/tflite/lib/arm64-v8a/"
cp /tmp/tflite/lib/armeabi-v7a/libtensorflowlite_c.so "$THIRD_PARTY_DIR/tflite/lib/armeabi-v7a/"
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
