cmake_minimum_required(VERSION 3.22.1)
project(parc_probe_jni VERSION 0.1.0 LANGUAGES CXX)

add_library(parc_probe_jni SHARED native_probe_jni.cpp)

target_compile_features(parc_probe_jni PRIVATE cxx_std_17)
target_compile_options(parc_probe_jni PRIVATE -Wall -Wextra -Wpedantic)
