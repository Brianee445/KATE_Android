# app/src/main/cpp/CMakeLists.txt
# Kate Engine - Clean build without TFLite

cmake_minimum_required(VERSION 3.22.1)
project("kate_engine")

# ==================== C++ STANDARD ====================
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

# ==================== ARCHITECTURE ====================
if(CMAKE_SYSTEM_PROCESSOR STREQUAL "aarch64")
    set(ARCH "arm64-v8a")
elseif(CMAKE_SYSTEM_PROCESSOR STREQUAL "armv7-a")
    set(ARCH "armeabi-v7a")
else()
    set(ARCH "arm64-v8a")
endif()

message("Building for architecture: ${ARCH}")

# ==================== VOSK ====================
# Fail the configure step immediately if the real Vosk library is missing.
# (Previously this silently wrote a text file containing "placeholder" in its
# place, which let the build limp all the way to the link step before failing
# with a cryptic "unexpected EOF" linker error. Fail loud, fail early instead.)
if(NOT EXISTS "${CMAKE_SOURCE_DIR}/third_party/vosk/lib/${ARCH}/libvosk.so")
    message(FATAL_ERROR
        "libvosk.so not found for ${ARCH} at "
        "${CMAKE_SOURCE_DIR}/third_party/vosk/lib/${ARCH}/libvosk.so — "
        "run the Vosk download step (or scripts/download_third_party.sh locally) before building.")
endif()

add_library(vosk SHARED IMPORTED)
set_target_properties(vosk PROPERTIES IMPORTED_LOCATION
    ${CMAKE_SOURCE_DIR}/third_party/vosk/lib/${ARCH}/libvosk.so)

# ==================== CORE ENGINE ====================
add_library(kate_engine SHARED
    # Core
    core/kate_engine.cpp
    core/audio_pipeline.cpp
    
    # STT (Speech-to-Text)
    stt/vosk_wrapper.cpp
    stt/speech_recognizer.cpp
    
    # Intent (Rule-based only)
    intent/intent_engine.cpp
    
    # TTS (Text-to-Speech)
    tts/tts_synthesizer.cpp
    
    # Cache
    cache/conversation_cache.cpp
    
    # JNI Bridge
    jni/native_bridge.cpp
)

# ==================== INCLUDE DIRECTORIES ====================
target_include_directories(kate_engine PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}/core
    ${CMAKE_CURRENT_SOURCE_DIR}/stt
    ${CMAKE_CURRENT_SOURCE_DIR}/intent
    ${CMAKE_CURRENT_SOURCE_DIR}/tts
    ${CMAKE_CURRENT_SOURCE_DIR}/cache
    ${CMAKE_CURRENT_SOURCE_DIR}/jni
    ${CMAKE_CURRENT_SOURCE_DIR}/third_party/vosk/include
)

# ==================== LINK LIBRARIES ====================
target_link_libraries(kate_engine
    vosk
    log
    z
    dl
    c++_shared
)

# ==================== COMPILER FLAGS ====================
target_compile_options(kate_engine PRIVATE
    -O2
    -fno-rtti
    -fno-exceptions
    -Wall
    -Wextra
    -Werror=return-type
)

# ==================== ARCHITECTURE OPTIMIZATIONS ====================
if(CMAKE_SYSTEM_PROCESSOR MATCHES "arm64")
    target_compile_options(kate_engine PRIVATE -march=armv8-a+crypto)
endif()

if(CMAKE_SYSTEM_PROCESSOR MATCHES "armeabi")
    target_compile_options(kate_engine PRIVATE -march=armv7-a -mfpu=neon -mthumb)
endif()

# ==================== DEBUG INFO ====================
message("========================================")
message("  Kate Engine Build Configuration")
message("========================================")
message("  Architecture: ${ARCH}")
message("  Vosk Path: ${CMAKE_SOURCE_DIR}/third_party/vosk/lib/${ARCH}/libvosk.so")
message("========================================")
