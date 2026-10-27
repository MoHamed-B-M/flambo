cmake_minimum_required(VERSION 3.22.1)

project(whisper-jni C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Build whisper.cpp (and its vendored ggml) as static libraries so everything
# ends up inside a single libwhisper-jni.so instead of several .so files.
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)

# whisper.cpp vendors ggml and calls add_subdirectory(ggml) from its own
# CMakeLists, so ggml must not be added here a second time.
add_subdirectory(whisper.cpp)

add_library(whisper-jni SHARED whisper_jni.cpp)

target_compile_options(whisper-jni PRIVATE -Wall -Wextra)
target_link_libraries(whisper-jni PRIVATE whisper android log)
