#!/bin/bash
# scripts/download_third_party.sh
# Download all third-party libraries for Kate Android C++ core

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}  Kate Android - Third Party Downloader ${NC}"
echo -e "${GREEN}========================================${NC}"

# Get the project root (2 levels up from scripts/)
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
THIRD_PARTY_DIR="$PROJECT_ROOT/app/src/main/cpp/third_party"

echo -e "${YELLOW}Project root: $PROJECT_ROOT${NC}"
echo -e "${YELLOW}Third-party dir: $THIRD_PARTY_DIR${NC}"

# Create third-party directory
mkdir -p "$THIRD_PARTY_DIR"

# ==================== VOSK ====================
download_vosk() {
    echo -e "\n${GREEN}📥 Downloading Vosk for Android...${NC}"
    
    VOSK_VERSION="0.3.45"
    VOSK_URL="https://github.com/alphacep/vosk-api/releases/download/v${VOSK_VERSION}/vosk-android-${VOSK_VERSION}.zip"
    TEMP_DIR="/tmp/vosk-$$"
    
    mkdir -p "$TEMP_DIR"
    cd "$TEMP_DIR"
    
    curl -L -o vosk.zip "$VOSK_URL" --progress-bar
    unzip -q vosk.zip -d vosk/
    
    # Create lib directories
    mkdir -p "$THIRD_PARTY_DIR/vosk/lib/arm64-v8a"
    mkdir -p "$THIRD_PARTY_DIR/vosk/lib/armeabi-v7a"
    mkdir -p "$THIRD_PARTY_DIR/vosk/include"
    
    # Copy libraries
    cp vosk/lib/android/arm64-v8a/libvosk.so "$THIRD_PARTY_DIR/vosk/lib/arm64-v8a/"
    cp vosk/lib/android/armeabi-v7a/libvosk.so "$THIRD_PARTY_DIR/vosk/lib/armeabi-v7a/"
    
    # Copy header
    cp vosk/src/vosk_api.h "$THIRD_PARTY_DIR/vosk/include/"
    
    # Clean up
    cd /tmp
    rm -rf "$TEMP_DIR"
    
    echo -e "${GREEN}✅ Vosk downloaded successfully${NC}"
    echo -e "   📁 $THIRD_PARTY_DIR/vosk/"
}

# ==================== TENSORFLOW LITE ====================
download_tflite() {
    echo -e "\n${GREEN}📥 Downloading TensorFlow Lite for Android...${NC}"
    
    TFLITE_VERSION="2.17.0"
    TFLITE_URL="https://storage.googleapis.com/tensorflow/libtensorflow/libtensorflow-lite-android-${TFLITE_VERSION}.zip"
    TEMP_DIR="/tmp/tflite-$$"
    
    mkdir -p "$TEMP_DIR"
    cd "$TEMP_DIR"
    
    curl -L -o tflite.zip "$TFLITE_URL" --progress-bar
    unzip -q tflite.zip -d tflite/
    
    # Create directories
    mkdir -p "$THIRD_PARTY_DIR/tflite/lib/arm64-v8a"
    mkdir -p "$THIRD_PARTY_DIR/tflite/lib/armeabi-v7a"
    mkdir -p "$THIRD_PARTY_DIR/tflite/include"
    
    # Copy libraries
    cp tflite/lib/arm64-v8a/libtensorflowlite_c.so "$THIRD_PARTY_DIR/tflite/lib/arm64-v8a/"
    cp tflite/lib/armeabi-v7a/libtensorflowlite_c.so "$THIRD_PARTY_DIR/tflite/lib/armeabi-v7a/"
    
    # Copy headers
    cp -r tflite/include/tensorflow "$THIRD_PARTY_DIR/tflite/include/"
    
    # Clean up
    cd /tmp
    rm -rf "$TEMP_DIR"
    
    echo -e "${GREEN}✅ TensorFlow Lite downloaded successfully${NC}"
    echo -e "   📁 $THIRD_PARTY_DIR/tflite/"
}

# ==================== NLOHMANN JSON ====================
download_json() {
    echo -e "\n${GREEN}📥 Downloading nlohmann/json...${NC}"
    
    JSON_VERSION="3.11.3"
    JSON_URL="https://github.com/nlohmann/json/releases/download/v${JSON_VERSION}/json.hpp"
    
    mkdir -p "$THIRD_PARTY_DIR/nlohmann"
    
    curl -L -o "$THIRD_PARTY_DIR/nlohmann/json.hpp" "$JSON_URL" --progress-bar
    
    echo -e "${GREEN}✅ nlohmann/json downloaded successfully${NC}"
    echo -e "   📁 $THIRD_PARTY_DIR/nlohmann/json.hpp"
}

# ==================== VOSK MODEL (OPTIONAL - Runtime Download) ====================
download_vosk_model() {
    echo -e "\n${GREEN}📥 Downloading Vosk model (optional - can download at runtime)...${NC}"
    
    MODEL_NAME="vosk-model-small-en-us-0.15"
    MODEL_URL="https://alphacephei.com/vosk/models/${MODEL_NAME}.zip"
    TEMP_DIR="/tmp/vosk-model-$$"
    
    mkdir -p "$TEMP_DIR"
    cd "$TEMP_DIR"
    
    curl -L -o model.zip "$MODEL_URL" --progress-bar
    unzip -q model.zip -d model/
    
    # Move to assets (optional - can also download at runtime)
    mkdir -p "$PROJECT_ROOT/app/src/main/assets"
    cp -r model/${MODEL_NAME} "$PROJECT_ROOT/app/src/main/assets/"
    
    # Clean up
    cd /tmp
    rm -rf "$TEMP_DIR"
    
    echo -e "${GREEN}✅ Vosk model downloaded successfully${NC}"
    echo -e "   📁 app/src/main/assets/${MODEL_NAME}/"
}

# ==================== VERIFY DOWNLOADS ====================
verify_downloads() {
    echo -e "\n${YELLOW}🔍 Verifying downloads...${NC}"
    
    # Check Vosk
    if [ -f "$THIRD_PARTY_DIR/vosk/include/vosk_api.h" ] && \
       [ -f "$THIRD_PARTY_DIR/vosk/lib/arm64-v8a/libvosk.so" ] && \
       [ -f "$THIRD_PARTY_DIR/vosk/lib/armeabi-v7a/libvosk.so" ]; then
        echo -e "${GREEN}✅ Vosk verified${NC}"
    else
        echo -e "${RED}❌ Vosk verification failed${NC}"
        exit 1
    fi
    
    # Check TFLite
    if [ -f "$THIRD_PARTY_DIR/tflite/lib/arm64-v8a/libtensorflowlite_c.so" ] && \
       [ -f "$THIRD_PARTY_DIR/tflite/lib/armeabi-v7a/libtensorflowlite_c.so" ]; then
        echo -e "${GREEN}✅ TensorFlow Lite verified${NC}"
    else
        echo -e "${RED}❌ TensorFlow Lite verification failed${NC}"
        exit 1
    fi
    
    # Check JSON
    if [ -f "$THIRD_PARTY_DIR/nlohmann/json.hpp" ]; then
        echo -e "${GREEN}✅ nlohmann/json verified${NC}"
    else
        echo -e "${RED}❌ nlohmann/json verification failed${NC}"
        exit 1
    fi
    
    echo -e "\n${GREEN}✅ All third-party libraries verified successfully!${NC}"
}

# ==================== MAIN ====================

# Parse arguments
DOWNLOAD_MODEL=false
for arg in "$@"; do
    case $arg in
        --with-model)
            DOWNLOAD_MODEL=true
            shift
            ;;
        --help)
            echo "Usage: ./download_third_party.sh [OPTIONS]"
            echo "Options:"
            echo "  --with-model    Download Vosk model to assets"
            echo "  --help          Show this help message"
            exit 0
            ;;
    esac
done

# Download libraries
download_vosk
download_tflite
download_json

if [ "$DOWNLOAD_MODEL" = true ]; then
    download_vosk_model
fi

# Verify
verify_downloads

echo -e "\n${GREEN}========================================${NC}"
echo -e "${GREEN}  All downloads complete! 🚀${NC}"
echo -e "${GREEN}========================================${NC}"
echo -e "\nNext steps:"
echo -e "1. Build the project: ./gradlew assembleDebug"
echo -e "2. Or clean and rebuild: ./gradlew clean assembleDebug"
