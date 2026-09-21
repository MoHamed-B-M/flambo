cmake_minimum_required(VERSION 3.22)
project(flambo_whisper)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

set(WHISPER_DIR ${CMAKE_SOURCE_DIR}/whisper.cpp)

add_subdirectory(${WHISPER_DIR} whisper_build)

add_library(whisper_jni SHARED whisper_jni.cpp)

target_include_directories(whisper_jni PRIVATE
    ${WHISPER_DIR}/include
    ${WHISPER_DIR}/ggml/include
    ${WHISPER_DIR}/src
)

target_link_libraries(whisper_jni
    whisper
    ggml
    log
    android
)

if(NOT CMAKE_BUILD_TYPE STREQUAL "Debug")
    target_compile_options(whisper_jni PRIVATE -O3 -fvisibility=hidden -fvisibility-inlines-hidden -ffunction-sections -fdata-sections)
    target_link_options(whisper_jni PRIVATE -Wl,--gc-sections -Wl,--exclude-libs,ALL -flto)
endif()
