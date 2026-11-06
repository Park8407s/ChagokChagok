# FluidSynth JNI 래퍼 (libostsynth.so)
# FluidSynth 본체는 app/build.gradle.kts 의 fetchFluidSynth 가 받아 둔 공식 안드로이드 빌드를 동적 링크한다 (LGPL-2.1).
cmake_minimum_required(VERSION 3.22.1)
project(ostsynth CXX)

if(NOT FLUIDSYNTH_DIR)
    message(FATAL_ERROR "FLUIDSYNTH_DIR 가 없습니다. ./gradlew :app:fetchFluidSynth 를 먼저 실행하세요.")
endif()

add_library(fluidsynth SHARED IMPORTED)
set_target_properties(fluidsynth PROPERTIES
        IMPORTED_LOCATION ${FLUIDSYNTH_DIR}/jniLibs/${ANDROID_ABI}/libfluidsynth.so)

add_library(ostsynth SHARED ostsynth_jni.cpp)
set_target_properties(ostsynth PROPERTIES CXX_STANDARD 17)
target_include_directories(ostsynth PRIVATE ${FLUIDSYNTH_DIR}/include)
target_link_libraries(ostsynth fluidsynth log)
