cmake_minimum_required(VERSION 3.22)
project(speechcore LANGUAGES C CXX)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

find_package(oboe REQUIRED CONFIG)   # Prefab из com.google.oboe:oboe

include(FetchContent)
set(LIBSAMPLERATE_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LIBSAMPLERATE_TESTS OFF CACHE BOOL "" FORCE)
set(BUILD_TESTING OFF CACHE BOOL "" FORCE)
FetchContent_Declare(samplerate
    GIT_REPOSITORY https://github.com/libsndfile/libsamplerate.git
    GIT_TAG 0.2.2)
FetchContent_MakeAvailable(samplerate)

# ONNX Runtime: scripts/fetch_ort.sh кладёт заголовки и .so
set(ORT_DIR ${CMAKE_CURRENT_SOURCE_DIR}/third_party/onnxruntime)
add_library(onnxruntime SHARED IMPORTED)
set_target_properties(onnxruntime PROPERTIES
    IMPORTED_LOCATION ${CMAKE_CURRENT_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libonnxruntime.so
    INTERFACE_INCLUDE_DIRECTORIES ${ORT_DIR}/headers)

add_library(speechcore SHARED
    jni/jni_bridge.cpp
    audio/audio_engine.cpp
    ml/dtln.cpp
    ml/gigaam.cpp
    pipeline/dsp_worker.cpp
    pipeline/ml_orchestrator.cpp)

target_include_directories(speechcore PRIVATE ${CMAKE_CURRENT_SOURCE_DIR})
target_compile_options(speechcore PRIVATE -O3 -Wall)
target_link_options(speechcore PRIVATE -Wl,-z,max-page-size=16384)
target_link_libraries(speechcore oboe::oboe samplerate onnxruntime android log)
