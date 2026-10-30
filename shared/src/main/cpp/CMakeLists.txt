cmake_minimum_required(VERSION 3.22.1)
project(shio_jni LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

# 内核产物由 hydrogen 构建，按 ABI 放在 jniLibs 下。这里只用 IMPORTED 目标
# 建立链接关系，不参与它的编译；两个 .so 会一起打进 APK。
set(SHIO_FFI_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}")
set(SHIO_FFI_SO "${SHIO_FFI_DIR}/libshio_ffi.so")
if(NOT EXISTS "${SHIO_FFI_SO}")
    message(FATAL_ERROR "libshio_ffi.so not found for ABI ${ANDROID_ABI}: ${SHIO_FFI_SO}")
endif()

add_library(shio_ffi SHARED IMPORTED)
# libshio_ffi.so 没有 SONAME。若按绝对路径链接，链接器会把构建机路径写进
# DT_NEEDED，设备上必然找不到；NO_SONAME 让 CMake 用 -lshio_ffi 链接。
set_target_properties(shio_ffi PROPERTIES
    IMPORTED_LOCATION "${SHIO_FFI_SO}"
    IMPORTED_NO_SONAME TRUE
)

add_library(shio_jni SHARED shio_jni.cpp)
target_include_directories(shio_jni PRIVATE "${CMAKE_CURRENT_SOURCE_DIR}/include")
target_link_directories(shio_jni PRIVATE "${SHIO_FFI_DIR}")
target_compile_options(shio_jni PRIVATE -Wall -Wextra)
# JNI 层不用 C++ 异常与 RTTI，也不需要动态 C++ 运行时外带。
target_compile_options(shio_jni PRIVATE -fno-exceptions -fno-rtti)
target_link_libraries(shio_jni PRIVATE shio_ffi)

# release 变体（AGP 用 RelWithDebInfo 配置）在链接时直接丢符号表。
# 否则带调试信息的 .so 会被原样打进 APK：每个 ABI 多出约 2.8 MB。
# 用 CMake 自己的 -s 而不是靠 AGP 的 strip 任务——当 .so 来自 library 依赖
# （:shared 的 externalNativeBuild 产物）时，app 侧的 strip 不会缩小它。
target_link_options(shio_jni PRIVATE $<$<CONFIG:RelWithDebInfo>:-s>)
