cmake_minimum_required(VERSION 3.22.1)

project(hoshidicts_jni LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 23)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

add_subdirectory(hoshidicts)

add_library(hoshidicts_jni SHARED
    hoshidicts_jni.cpp
)

target_link_libraries(hoshidicts_jni PRIVATE
    hoshidicts
)