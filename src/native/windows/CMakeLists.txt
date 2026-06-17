cmake_minimum_required(VERSION 3.13 FATAL_ERROR)

project(WinTray LANGUAGES C CXX)

# Define the C++ standard
set(CMAKE_CXX_STANDARD 17)

# Allow overriding the output base directory via environment variable
# (used by CI to write artifacts to build/nativeLibs instead of src/jvmMain/resources)
if(DEFINED ENV{NATIVE_LIBS_OUTPUT_DIR})
    set(BASE_OUTPUT_DIR "$ENV{NATIVE_LIBS_OUTPUT_DIR}")
else()
    set(BASE_OUTPUT_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../../jvmMain/resources/composetray/native")
endif()

# Check target architecture
if(CMAKE_GENERATOR_PLATFORM STREQUAL "x64" OR CMAKE_GENERATOR_PLATFORM STREQUAL "")
    set(TARGET_ARCH "x64")
    set(OUTPUT_DIR "${BASE_OUTPUT_DIR}/win32-x86-64")
    # Do NOT add /arch:AVX, /arch:AVX2 or higher here. The default x64 baseline is
    # SSE2, which every x86-64 CPU supports. Enabling AVX makes MSVC emit
    # VEX-encoded instructions (e.g. VPXOR) that raise EXCEPTION_ILLEGAL_INSTRUCTION
    # on pre-2011 CPUs without AVX (Nehalem Xeon, Core 2 Duo, ...). See issue #401.
    # The CI build disassembles the produced DLL and fails if any AVX instruction
    # is found, so a reintroduced /arch flag will be caught.
elseif(CMAKE_GENERATOR_PLATFORM STREQUAL "ARM64")
    set(TARGET_ARCH "ARM64")
    set(OUTPUT_DIR "${BASE_OUTPUT_DIR}/win32-arm64")
else()
    message(FATAL_ERROR "Unsupported architecture: ${CMAKE_GENERATOR_PLATFORM}")
endif()

# Ensure output directory exists
file(MAKE_DIRECTORY ${OUTPUT_DIR})

# ── JNI headers ─────────────────────────────────────────────────────────────────
find_package(JNI REQUIRED)
include_directories(${JNI_INCLUDE_DIRS})

# Add sources
list(APPEND SRCS
    ${CMAKE_CURRENT_SOURCE_DIR}/tray_windows.c
    ${CMAKE_CURRENT_SOURCE_DIR}/jni_bridge.c
)

# Create the shared library
add_library(WinTray SHARED ${SRCS})
set_property(TARGET WinTray PROPERTY C_STANDARD 99)
target_compile_definitions(WinTray PRIVATE TRAY_EXPORTS)
set_target_properties(WinTray PROPERTIES C_VISIBILITY_PRESET hidden)

# Set necessary preprocessor definitions
target_compile_definitions(WinTray PRIVATE TRAY_WINAPI=1 WIN32_LEAN_AND_MEAN NOMINMAX)

# Link to Shell32.lib for Windows platform
if(WIN32)
    target_link_libraries(WinTray PRIVATE shell32 user32 gdi32)
endif()

# Define the installation path
INSTALL(TARGETS WinTray DESTINATION lib)

IF (NOT WIN32)
    INSTALL(FILES tray.h DESTINATION include)
ENDIF ()

# Ensure the output directory exists for the architecture
message(STATUS "Target architecture: ${TARGET_ARCH}")
message(STATUS "Output directory: ${OUTPUT_DIR}")

# Ensure the output directory exists
file(MAKE_DIRECTORY ${OUTPUT_DIR})

# Post-build step to copy DLL
add_custom_command(TARGET WinTray POST_BUILD
        COMMAND ${CMAKE_COMMAND} -E copy $<TARGET_FILE:WinTray> ${OUTPUT_DIR}/WinTray.dll)

# Set MinSizeRel options to reduce size
set(CMAKE_BUILD_TYPE MinSizeRel)

# Add any further optimizations for MinSizeRel
if(CMAKE_BUILD_TYPE STREQUAL "MinSizeRel")
    target_compile_options(WinTray PRIVATE "/O1")  # Optimize for size
    set(CMAKE_EXE_LINKER_FLAGS "${CMAKE_EXE_LINKER_FLAGS} /OPT:REF /OPT:ICF")  # Remove unused code and merge identical functions
endif()
