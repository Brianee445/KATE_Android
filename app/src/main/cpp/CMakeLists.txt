# app/src/main/cpp/CMakeLists.txt (UPDATED)

cmake_minimum_required(VERSION 3.22.1)

# Project name
project("kate_engine")

# Set C++ standard
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

# ==================== ARCHITECTURE DETECTION ====================

if(CMAKE_SYSTEM_PROCESSOR STREQUAL "aarch64")
    set(ARCH "arm64-v8a")
elseif(CMAKE_SYSTEM_PROCESSOR STREQUAL "armv7-a")
    set(ARCH "armeabi-v7a")
else()
    set(ARCH "arm64-v8a")
endif()

message("Building for architecture: ${ARCH}")

# ==================== VOSK ====================

add_library(vosk SHARED IMPORTED)
set_target_properties(vosk PROPERTIES IMPORTED_LOCATION
    ${CMAKE_SOURCE_DIR}/third_party/vosk/lib/${ARCH}/libvosk.so)

# ==================== TENSORFLOW LITE ====================

add_library(tflite SHARED IMPORTED)
set_target_properties(tflite PROPERTIES IMPORTED_LOCATION
    ${CMAKE_SOURCE_DIR}/third_party/tflite/lib/${ARCH}/libtensorflowlite_c.so)

# XNNPACK delegate (optional - for performance)
add_library(xnnpack SHARED IMPORTED)
set_target_properties(xnnpack PROPERTIES IMPORTED_LOCATION
    ${CMAKE_SOURCE_DIR}/third_party/tflite/delegate/${ARCH}/libxnnpack_delegate.so)

# ==================== CORE ENGINE ====================

add_library(kate_engine SHARED
    # Core
    core/kate_engine.cpp
    core/audio_pipeline.cpp
    
    # STT
    stt/vosk_wrapper.cpp
    stt/speech_recognizer.cpp
    
    # Intent
    intent/intent_engine.cpp
    intent/intent_classifier.cpp
    intent/entity_extractor.cpp
    
    # TTS
    tts/tts_synthesizer.cpp
    
    # Cache
    cache/conversation_cache.cpp
    
    # JNI Bridge
    jni/native_bridge.cpp
)

# ==================== INCLUDE DIRECTORIES ====================

target_include_directories(kate_engine PRIVATE
    # Core
    ${CMAKE_CURRENT_SOURCE_DIR}/core
    ${CMAKE_CURRENT_SOURCE_DIR}/stt
    ${CMAKE_CURRENT_SOURCE_DIR}/intent
    ${CMAKE_CURRENT_SOURCE_DIR}/tts
    ${CMAKE_CURRENT_SOURCE_DIR}/cache
    ${CMAKE_CURRENT_SOURCE_DIR}/jni
    
    # Vosk
    ${CMAKE_CURRENT_SOURCE_DIR}/third_party/vosk/include
    
    # TensorFlow Lite
    ${CMAKE_CURRENT_SOURCE_DIR}/third_party/tflite/include
    
    # nlohmann/json
    ${CMAKE_CURRENT_SOURCE_DIR}/third_party
)

# ==================== LINK LIBRARIES ====================

target_link_libraries(kate_engine
    vosk
    tflite
    log
    z
    dl
    c++_shared
)

# Optional: Link XNNPACK for performance
if(EXISTS "${CMAKE_SOURCE_DIR}/third_party/tflite/delegate/${ARCH}/libxnnpack_delegate.so")
    target_link_libraries(kate_engine xnnpack)
endif()

# ==================== COMPILER FLAGS ====================

target_compile_options(kate_engine PRIVATE
    -O2
    -fno-rtti
    -fno-exceptions
    -Wall
    -Wextra
    -Werror=return-type
)

# Architecture-specific optimizations
if(CMAKE_SYSTEM_PROCESSOR MATCHES "arm64")
    target_compile_options(kate_engine PRIVATE -march=armv8-a+crypto)
endif()

if(CMAKE_SYSTEM_PROCESSOR MATCHES "armeabi")
    target_compile_options(kate_engine PRIVATE -march=armv7-a -mfpu=neon -mthumb)
endif()

# ==================== PREBUILT LIBRARY COPY ====================

# Copy prebuilt libraries to the correct location
add_custom_command(TARGET kate_engine POST_BUILD
    COMMAND ${CMAKE_COMMAND} -E copy_if_different
        ${CMAKE_SOURCE_DIR}/third_party/vosk/lib/${ARCH}/libvosk.so
        ${CMAKE_LIBRARY_OUTPUT_DIRECTORY}/libvosk.so
