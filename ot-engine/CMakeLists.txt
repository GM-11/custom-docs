cmake_minimum_required(VERSION 3.10)
project(OtEngine)

include_directories(include)

set(CMAKE_CXX_STANDARD 17)

file(GLOB SRC_CPP CONFIGURE_DEPENDS "src/*.cpp" "ffi/*.cpp")

add_library(ot-engine-lib STATIC ${SRC_CPP})

target_include_directories(ot-engine-lib PRIVATE include)
