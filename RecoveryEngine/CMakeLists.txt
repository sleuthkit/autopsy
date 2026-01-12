cmake_minimum_required(VERSION 3.15)
project(DataRecoveryEngine VERSION 1.0.0 LANGUAGES CXX C)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_EXPORT_COMPILE_COMMANDS ON)

# Windows-specific settings
if(WIN32)
    add_definitions(-D_WIN32_WINNT=0x0601)
    add_definitions(-DWIN32_LEAN_AND_MEAN)
    add_definitions(-DNOMINMAX)
    add_definitions(-DUNICODE -D_UNICODE)
endif()

# Options
option(BUILD_SHARED_LIBS "Build shared libraries" ON)
option(BUILD_NODE_ADDON "Build Node.js addon" ON)
option(BUILD_TESTS "Build test executables" OFF)
option(ENABLE_PHOTOREC_CARVING "Enable PhotoRec-based file carving" ON)

# Find required packages
find_package(SQLite3 REQUIRED)

# TSK (The Sleuth Kit) - expect TSK_HOME environment variable
if(DEFINED ENV{TSK_HOME})
    set(TSK_HOME "$ENV{TSK_HOME}")
    message(STATUS "TSK_HOME: ${TSK_HOME}")
else()
    message(FATAL_ERROR "TSK_HOME environment variable not set. Please set it to The Sleuth Kit installation directory.")
endif()

# TSK include and library paths
set(TSK_INCLUDE_DIR "${TSK_HOME}/include")
if(EXISTS "${TSK_HOME}/win32/Release")
    set(TSK_LIB_DIR "${TSK_HOME}/win32/Release")
elseif(EXISTS "${TSK_HOME}/lib")
    set(TSK_LIB_DIR "${TSK_HOME}/lib")
else()
    message(FATAL_ERROR "Cannot find TSK library directory")
endif()

# TSK libraries
find_library(TSK_LIBRARY 
    NAMES libtsk tsk 
    PATHS ${TSK_LIB_DIR}
    REQUIRED
)

# Core Recovery Engine Library
set(RECOVERY_ENGINE_SOURCES
    src/core/RecoveryEngine.cpp
    src/core/DeviceScanner.cpp
    src/core/VolumeScanner.cpp
    src/core/FileSystemParser.cpp
    src/core/DeepScanner.cpp
    src/core/FileCarver.cpp
    src/core/RecoverySession.cpp
    src/core/RecoveryScore.cpp
    src/core/FileTypeClassifier.cpp
    src/core/ProgressTracker.cpp
    src/core/MetadataExtractor.cpp
    src/storage/SessionDatabase.cpp
    src/storage/RecoveryResultStore.cpp
    src/util/DiskAccess.cpp
    src/util/ErrorHandler.cpp
    src/util/StringUtils.cpp
    src/util/PathUtils.cpp
)

set(RECOVERY_ENGINE_HEADERS
    include/RecoveryEngine.h
    include/RecoveryTypes.h
    include/DeviceScanner.h
    include/VolumeScanner.h
    include/FileSystemParser.h
    include/DeepScanner.h
    include/FileCarver.h
    include/RecoverySession.h
    include/RecoveryScore.h
    include/FileTypeClassifier.h
    include/ProgressTracker.h
    include/MetadataExtractor.h
    include/SessionDatabase.h
    include/RecoveryResultStore.h
    include/DiskAccess.h
    include/ErrorHandler.h
    include/IRecoveryCallback.h
)

add_library(recovery_engine ${RECOVERY_ENGINE_SOURCES} ${RECOVERY_ENGINE_HEADERS})
target_include_directories(recovery_engine 
    PUBLIC 
        ${CMAKE_CURRENT_SOURCE_DIR}/include
        ${TSK_INCLUDE_DIR}
    PRIVATE
        ${CMAKE_CURRENT_SOURCE_DIR}/src
)
target_link_libraries(recovery_engine 
    PUBLIC 
        ${TSK_LIBRARY}
        SQLite::SQLite3
)

if(WIN32)
    target_link_libraries(recovery_engine PUBLIC ws2_32 advapi32)
endif()

# Node.js addon
if(BUILD_NODE_ADDON)
    # Find Node.js and node-addon-api
    execute_process(
        COMMAND node -p "require('node-addon-api').include"
        WORKING_DIRECTORY ${CMAKE_SOURCE_DIR}
        OUTPUT_VARIABLE NODE_ADDON_API_DIR
        OUTPUT_STRIP_TRAILING_WHITESPACE
        ERROR_QUIET
    )
    
    execute_process(
        COMMAND node -p "require('node-api-headers').include_dir"
        WORKING_DIRECTORY ${CMAKE_SOURCE_DIR}
        OUTPUT_VARIABLE NODE_API_HEADERS_DIR
        OUTPUT_STRIP_TRAILING_WHITESPACE
        ERROR_QUIET
    )
    
    if(NODE_ADDON_API_DIR AND NODE_API_HEADERS_DIR)
        string(REGEX REPLACE "[\r\n\"]" "" NODE_ADDON_API_DIR ${NODE_ADDON_API_DIR})
        string(REGEX REPLACE "[\r\n\"]" "" NODE_API_HEADERS_DIR ${NODE_API_HEADERS_DIR})
        
        add_library(recovery_engine_addon SHARED
            src/node/NodeBindings.cpp
            src/node/AsyncWorkers.cpp
            src/node/TypeConverters.cpp
        )
        
        target_include_directories(recovery_engine_addon PRIVATE
            ${NODE_ADDON_API_DIR}
            ${NODE_API_HEADERS_DIR}
            ${CMAKE_CURRENT_SOURCE_DIR}/include
        )
        
        target_link_libraries(recovery_engine_addon PRIVATE recovery_engine)
        
        set_target_properties(recovery_engine_addon PROPERTIES
            PREFIX ""
            SUFFIX ".node"
            OUTPUT_NAME "recovery_engine"
        )
        
        target_compile_definitions(recovery_engine_addon PRIVATE
            NAPI_VERSION=8
            NODE_ADDON_API_DISABLE_DEPRECATED
        )
        
        if(WIN32)
            set_target_properties(recovery_engine_addon PROPERTIES
                LINK_FLAGS "/DELAYLOAD:node.exe"
            )
            target_link_libraries(recovery_engine_addon PRIVATE delayimp)
        endif()
        
        message(STATUS "Node.js addon will be built")
    else()
        message(WARNING "node-addon-api or node-api-headers not found. Node.js addon will not be built.")
        message(STATUS "Run: npm install node-addon-api node-api-headers")
    endif()
endif()

# Test executable
if(BUILD_TESTS)
    add_executable(recovery_engine_test
        test/main.cpp
        test/DeviceScannerTest.cpp
        test/VolumeScannerTest.cpp
        test/RecoverySessionTest.cpp
    )
    target_link_libraries(recovery_engine_test PRIVATE recovery_engine)
    target_include_directories(recovery_engine_test PRIVATE ${CMAKE_CURRENT_SOURCE_DIR}/include)
endif()

# Installation
install(TARGETS recovery_engine
    LIBRARY DESTINATION lib
    ARCHIVE DESTINATION lib
    RUNTIME DESTINATION bin
)

install(DIRECTORY include/ DESTINATION include/recovery_engine)

if(BUILD_NODE_ADDON AND TARGET recovery_engine_addon)
    install(TARGETS recovery_engine_addon DESTINATION lib/node)
endif()

# Package configuration
include(CMakePackageConfigHelpers)
write_basic_package_version_file(
    "${CMAKE_CURRENT_BINARY_DIR}/RecoveryEngineConfigVersion.cmake"
    VERSION ${PROJECT_VERSION}
    COMPATIBILITY SameMajorVersion
)

configure_package_config_file(
    "${CMAKE_CURRENT_SOURCE_DIR}/cmake/RecoveryEngineConfig.cmake.in"
    "${CMAKE_CURRENT_BINARY_DIR}/RecoveryEngineConfig.cmake"
    INSTALL_DESTINATION lib/cmake/RecoveryEngine
)

install(FILES
    "${CMAKE_CURRENT_BINARY_DIR}/RecoveryEngineConfig.cmake"
    "${CMAKE_CURRENT_BINARY_DIR}/RecoveryEngineConfigVersion.cmake"
    DESTINATION lib/cmake/RecoveryEngine
)
