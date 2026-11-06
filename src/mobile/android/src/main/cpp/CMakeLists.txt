cmake_minimum_required(VERSION 3.22.1)
project(litert_bootstrap)

add_library(litert_bootstrap SHARED litert_bootstrap.cpp)

find_library(log-lib log)

target_link_libraries(
    litert_bootstrap
    ${log-lib}
    dl
)
