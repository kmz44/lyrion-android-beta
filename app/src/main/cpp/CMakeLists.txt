# CMakeLists.txt for Android app with 16 KB page size support
cmake_minimum_required(VERSION 3.22.1)

project("orabel-app")

# 16 KB page size support - Required for Google Play Console (November 2025)
set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -Wl,-z,max-page-size=16384")
set(CMAKE_EXE_LINKER_FLAGS "${CMAKE_EXE_LINKER_FLAGS} -Wl,-z,max-page-size=16384")

# Use LLD linker for better compatibility with Android 15+
set(CMAKE_ANDROID_LD lld)

# This is a placeholder CMakeLists.txt for the app module
# The actual native libraries are built in the orabel module
# This file ensures proper 16KB alignment for all dependencies

# No native libraries to build here, but configuration is applied to dependencies
