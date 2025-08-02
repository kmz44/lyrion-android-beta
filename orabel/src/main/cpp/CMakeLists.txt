# For more information about using CMake with Android Studio, read the
# documentation: https://d.android.com/studio/projects/add-native-code.html.
# For more examples on how to use CMake, see https://github.com/android/ndk-samples.

# Sets the minimum CMake version required for this project.
cmake_minimum_required(VERSION 3.22.1)

# Declares the project name. The project name can be accessed via ${ PROJECT_NAME},
# Since this is the top level CMakeLists.txt, the project name is also accessible
# with ${CMAKE_PROJECT_NAME} (both CMake variables are in-sync within the top level
# build script scope).
project("orabel")

# Configure llama.cpp build options for Android
set(LLAMA_CURL OFF CACHE BOOL "Disable CURL for llama.cpp")
set(LLAMA_SERVER OFF CACHE BOOL "Disable server for llama.cpp")
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "Disable tests for llama.cpp")
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "Disable examples for llama.cpp")
set(LLAMA_METAL OFF CACHE BOOL "Disable Metal for llama.cpp")
set(LLAMA_BLAS OFF CACHE BOOL "Disable BLAS for llama.cpp")
set(LLAMA_CUBLAS OFF CACHE BOOL "Disable CUBLAS for llama.cpp")

# 16 KB page size support - Android NDK 26+ and Google Play Console requirement
# Force 16KB alignment for ALL libraries, including third-party ones
set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384")
set(CMAKE_EXE_LINKER_FLAGS "${CMAKE_EXE_LINKER_FLAGS} -Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384")

# Use LLD linker for better compatibility
set(CMAKE_ANDROID_LD lld)

# Additional linker flags for compatibility
set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -Wl,--hash-style=gnu")
set(CMAKE_EXE_LINKER_FLAGS "${CMAKE_EXE_LINKER_FLAGS} -Wl,--hash-style=gnu")

add_subdirectory(../../../../llama.cpp llama.cpp)

# Creates and names a library, sets it as either STATIC
# or SHARED, and provides the relative paths to its source code.
# You can define multiple libraries, and CMake builds them for you.
# Gradle automatically packages shared libraries with your APK.
#
# In this top level CMakeLists.txt, ${CMAKE_PROJECT_NAME} is used to define
# the target library name; in the sub-module's CMakeLists.txt, ${PROJECT_NAME}
# is preferred for the same purpose.
#
# In order to load a library into your app from Java/Kotlin, you must call
# System.loadLibrary() and pass the name of the library defined here;
# for GameActivity/NativeActivity derived applications, the same library name must be
# used in the AndroidManifest.xml file.
add_library(${CMAKE_PROJECT_NAME} SHARED
    # List C/C++ source files with relative paths to this CMakeLists.txt.
    llm_inference.cpp
    orabel.cpp
)

# Specifies libraries CMake should link to your target library. You
# can link libraries from various origins, such as libraries defined in this
# build script, prebuilt third-party libraries, or Android system libraries.
target_link_libraries(${CMAKE_PROJECT_NAME}
    # List libraries link to the target library
        android
        log
        common
        llama
)
