cmake_minimum_required(VERSION 3.10)

project(whisper-jni)

set(CMAKE_CXX_STANDARD 17)

set(WHISPER_LIB_DIR ${CMAKE_SOURCE_DIR}/whisper.cpp)

set(SOURCE_FILES
    ${WHISPER_LIB_DIR}/src/whisper.cpp
    ${CMAKE_SOURCE_DIR}/whisper_jni.cpp
)

find_library(LOG_LIB log)

include(FetchContent)

add_library(
    whisper
    SHARED
    ${SOURCE_FILES}
)

FetchContent_Declare(ggml SOURCE_DIR ${WHISPER_LIB_DIR}/ggml)
FetchContent_MakeAvailable(ggml)

target_link_libraries(whisper ${LOG_LIB} android ggml)
target_compile_definitions(whisper PUBLIC GGML_USE_CPU)

if (NOT ${CMAKE_BUILD_TYPE} STREQUAL "Debug")
    target_compile_options(whisper PRIVATE -O3)
    target_compile_options(whisper PRIVATE -fvisibility=hidden -fvisibility-inlines-hidden)
    target_compile_options(whisper PRIVATE -ffunction-sections -fdata-sections)
endif ()

include_directories(${WHISPER_LIB_DIR})
include_directories(${WHISPER_LIB_DIR}/src)
include_directories(${WHISPER_LIB_DIR}/include)
include_directories(${WHISPER_LIB_DIR}/ggml/include)
include_directories(${WHISPER_LIB_DIR}/ggml/src)
include_directories(${WHISPER_LIB_DIR}/ggml/src/ggml-cpu)
