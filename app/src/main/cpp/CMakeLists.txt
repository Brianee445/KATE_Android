# app/src/main/cpp/CMakeLists.txt

cmake_minimum_required(VERSION 3.22.1)

# Project name
project("kate_engine")

# Set C++ standard
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

# Add Vosk
add_library(vosk SHARED IMPORTED)
set_target_properties(vosk PROPERTIES IMPORTED_LOCATION
    ${CMAKE_SOURCE_DIR}/src/main/cpp/third_party/vosk/libvosk.so)

# Add TensorFlow Lite
add_library(tflite SHARED IMPORTED)
set_target_properties(tflite PROPERTIES IMPORTED_LOCATION
    ${CMAKE_SOURCE_DIR}/src/main/cpp/third_party/tflite/libtensorflowlite_c.so)

# Core engine library
add_library(kate_engine SHARED
    # Core
    core/kate_engine.cpp
    core/audio_pipeline.cpp
    core/audio_processor.cpp
    core/ring_buffer.cpp
    
    # STT
    stt/vosk_wrapper.cpp
    stt/speech_recognizer.cpp
    
    # Intent
    intent/intent_engine.cpp
    intent/intent_classifier.cpp
    intent/entity_extractor.cpp
    
    # TTS
    tts/tts_synthesizer.cpp
    tts/voice_manager.cpp
    
    # Cache
    cache/conversation_cache.cpp
    cache/embedding_store.cpp
    
    # JNI Bridge
    jni/native_bridge.cpp
)

# Include directories
target_include_directories(kate_engine PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}/core
    ${CMAKE_CURRENT_SOURCE_DIR}/stt
    ${CMAKE_CURRENT_SOURCE_DIR}/intent
    ${CMAKE_CURRENT_SOURCE_DIR}/tts
    ${CMAKE_CURRENT_SOURCE_DIR}/cache
    ${CMAKE_CURRENT_SOURCE_DIR}/jni
    ${CMAKE_CURRENT_SOURCE_DIR}/third_party/vosk/include
    ${CMAKE_CURRENT_SOURCE_DIR}/third_party/tflite/include
)

# Link libraries
target_link_libraries(kate_engine
    vosk
    tflite
    log
    z
    c++_shared
)

# Compiler flags
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
    target_compile_options(kate_engine PRIVATE -march=armv7-a -mfpu=neon)
endif()
