# Can be called with something like cmake -G "Visual Studio 17 2022" -A x64 -S .. "-DCMAKE_TOOLCHAIN_FILE=C:/Users/gregd/vcpkg/scripts/buildsystems/vcpkg.cmake" -DVCPKG_TARGET_TRIPLET=x64-windows-static

cmake_minimum_required (VERSION 3.15)

project ("heifconvert")

find_package(libheif CONFIG REQUIRED)
find_package(JPEG REQUIRED)
find_package(Java REQUIRED)

# add_compile_definitions(LIBDE265_STATIC_BUILD LIBHEIF_STATIC_BUILD)

message("JAVA_HOME = $ENV{JAVA_HOME}")
message("Java_JAVA_EXECUTABLE = ${Java_JAVA_EXECUTABLE}")
message("Java_JAVAC_EXECUTABLE = ${Java_JAVAC_EXECUTABLE}")
message("Java_JAVAH_EXECUTABLE = ${Java_JAVAH_EXECUTABLE}")
message("Java_JAVADOC_EXECUTABLE = ${Java_JAVADOC_EXECUTABLE}")
message("Java_VERSION_STRING = ${Java_VERSION_STRING}")
message("Java_VERSION = ${Java_VERSION}")

find_package(JNI)

if (JNI_FOUND)
    message (STATUS "JNI_INCLUDE_DIRS=${JNI_INCLUDE_DIRS}")
    message (STATUS "JNI_LIBRARIES=${JNI_LIBRARIES}")
endif()

add_compile_options(/std:c++latest)

set (heif_convert_sources
  encoder.cc
  encoder.h
  encoder_jpeg.cc
  encoder_jpeg.h
  org_sleuthkit_autopsy_modules_pictureanalyzer_impls_HeifJNI.cc
  org_sleuthkit_autopsy_modules_pictureanalyzer_impls_HeifJNI.h
)

set (additional_link_directories)
set (additional_libraries
  heif
  ${JPEG_LIBRARIES}
)
set (additional_includes
  ${JNI_INCLUDE_DIRS}
  ${JPEG_INCLUDE_DIRS}
  ${JPEG_INCLUDE_DIR}
)

include (${CMAKE_ROOT}/Modules/FindJPEG.cmake)
include_directories(SYSTEM ${JPEG_INCLUDE_DIR})

include (${CMAKE_ROOT}/Modules/CheckCXXSourceCompiles.cmake)

set(CMAKE_REQUIRED_LIBRARIES ${JPEG_LIBRARIES})

# while the docs say JPEG_INCLUDE_DIRS, my FindJPEG.cmake script returns it in JPEG_INCLUDE_DIR
set(CMAKE_REQUIRED_INCLUDES ${JPEG_INCLUDE_DIRS} ${JPEG_INCLUDE_DIR})

add_definitions(-DHAVE_JPEG_WRITE_ICC_PROFILE=1)

if(UNIX OR MINGW)
  include (${CMAKE_ROOT}/Modules/FindPkgConfig.cmake)
endif()

#set(CompilerFlags
#        CMAKE_CXX_FLAGS
#        CMAKE_CXX_FLAGS_DEBUG
#        CMAKE_CXX_FLAGS_RELEASE
#        CMAKE_C_FLAGS
#        CMAKE_C_FLAGS_DEBUG
#        CMAKE_C_FLAGS_RELEASE
#        )
#foreach(CompilerFlag ${CompilerFlags})
#  string(REPLACE "/MD" "/MT" ${CompilerFlag} "${${CompilerFlag}}")
#endforeach()

add_library(heifconvert SHARED ${heif_convert_sources})

#set_property(TARGET heifconvert PROPERTY
#  MSVC_RUNTIME_LIBRARY "MultiThreaded$<$<CONFIG:Debug>:Debug>")

target_link_directories (heifconvert PRIVATE ${additional_link_directories})
target_link_libraries (heifconvert ${additional_libraries})
target_include_directories(heifconvert PRIVATE ${additional_includes})

message("Installing to: ${CMAKE_INSTALL_BINDIR}")
install(TARGETS heifconvert RUNTIME DESTINATION ${CMAKE_INSTALL_BINDIR})
# include(InstallRequiredSystemLibraries)