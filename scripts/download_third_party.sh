#!/bin/bash
# scripts/download_third_party.sh

# Download Vosk for Android
download_vosk() {
    echo "Downloading Vosk for Android..."
    
    # Vosk Android binaries
    VOSK_VERSION="0.3.45"
    VOSK_URL="https://github.com/alphacep/vosk-api/releases/download/v${VOSK_VERSION}/vosk-android-${VOSK_VERSION}.zip"
    
    curl -L -o /tmp/vosk.zip $VOSK_URL
    unzip -o /tmp/vosk.zip -d /tmp/vosk/
    
    # Copy libraries
    mkdir -p app/src/main/cpp/third_party/vosk/lib/arm64-v8a
    mkdir -p app/src/main/cpp/third_party/vosk/lib/armeabi-v7a
    
    cp /tmp/vosk/lib/android/arm64-v8a/libvosk.so app/src/main/cpp/third_party/vosk/lib/arm64-v8a/
    cp /tmp/vosk/lib/android/armeabi-v7a/libvosk.so app/src/main/cpp/third_party/vosk/lib/armeabi-v7a/
    
    # Copy header
    cp /tmp/vosk/src/vosk_api.h app/src/main/cpp/third_party/vosk/include/
    
    echo "Vosk downloaded successfully"
}

# Download TensorFlow Lite for Android
download_tflite() {
    echo "Downloading TensorFlow Lite for Android..."
    
    TFLITE_VERSION="2.17.0"
    TFLITE_URL="https://storage.googleapis.com/tensorflow/libtensorflow/libtensorflow-lite-android-${TFLITE_VERSION}.zip"
    
    curl -L -o /tmp/tflite.zip $TFLITE_URL
    unzip -o /tmp/tflite.zip -d /tmp/tflite/
    
    # Copy libraries
    mkdir -p app/src/main/cpp/third_party/tflite/lib/arm64-v8a
    mkdir -p app/src/main/cpp/third_party/tflite/lib/armeabi-v7a
    
    cp /tmp/tflite/lib/arm64-v8a/libtensorflowlite_c.so app/src/main/cpp/third_party/tflite/lib/arm64-v8a/
    cp /tmp/tflite/lib/armeabi-v7a/libtensorflowlite_c.so app/src/main/cpp/third_party/tflite/lib/armeabi-v7a/
    
    # Copy headers
    cp -r /tmp/tflite/include/tensorflow app/src/main/cpp/third_party/tflite/include/
    
    echo "TensorFlow Lite downloaded successfully"
}

# Download nlohmann/json
download_json() {
    echo "Downloading nlohmann/json..."
    
    mkdir -p app/src/main/cpp/third_party
    curl -L -o app/src/main/cpp/third_party/nlohmann/json.hpp \
        "https://github.com/nlohmann/json/releases/latest/download/json.hpp"
    
    echo "nlohmann/json downloaded successfully"
}

# Main
echo "Starting third-party library downloads..."

download_vosk
download_tflite
download_json

echo "All third-party libraries downloaded successfully!"
