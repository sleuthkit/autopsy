cmake_minimum_required (VERSION 3.8)

project ("heif-convert")

find_package(libheif CONFIG REQUIRED)
find_package(Java REQUIRED)

message("JAVA_HOME = $ENV{JAVA_HOME}")
message("Java_JAVA_EXECUTABLE = ${Java_JAVA_EXECUTABLE}")
message("Java_JAVAC_EXECUTABLE = ${Java_JAVAC_EXECUTABLE}")
message("Java_JAVAH_EXECUTABLE = ${Java_JAVAH_EXECUTABLE}")
message("Java_JAVADOC_EXECUTABLE = ${Java_JAVADOC_EXECUTABLE}")
message("Java_VERSION_STRING = ${Java_VERSION_STRING}")
message("Java_VERSION = ${Java_VERSION}")

find_package(JNI)
find_package(JPEG REQUIRED)                                   

if (JNI_FOUND)
    message (STATUS "JNI_INCLUDE_DIRS=${JNI_INCLUDE_DIRS}")
    message (STATUS "JNI_LIBRARIES=${JNI_LIBRARIES}")
endif()

add_compile_options(/std:c++latest)


set (heif_convert_sources
  encoder.cc
  encoder.h
  org_sleuthkit_autopsy_modules_pictureanalyzer_impls_HeifJNI.cc
  org_sleuthkit_autopsy_modules_pictureanalyzer_impls_HeifJNI.h
)

set (additional_link_directories)
set (additional_libraries)
set (additional_includes
    ${JNI_INCLUDE_DIRS}
)

include (${CMAKE_ROOT}/Modules/FindJPEG.cmake)

include_directories(SYSTEM ${JPEG_INCLUDE_DIR})

include (${CMAKE_ROOT}/Modules/CheckCXXSourceCompiles.cmake)

set(CMAKE_REQUIRED_LIBRARIES ${JPEG_LIBRARIES})

# while the docs say JPEG_INCLUDE_DIRS, my FindJPEG.cmake script returns it in JPEG_INCLUDE_DIR
set(CMAKE_REQUIRED_INCLUDES ${JPEG_INCLUDE_DIRS} ${JPEG_INCLUDE_DIR})

check_cxx_source_compiles("
#include <stddef.h>
#include <stdio.h>
#include <jpeglib.h>

int main() {
  jpeg_write_icc_profile(NULL, NULL, 0);
  return 0;
}
" HAVE_JPEG_WRITE_ICC_PROFILE)
if(HAVE_JPEG_WRITE_ICC_PROFILE)
  add_definitions(-DHAVE_JPEG_WRITE_ICC_PROFILE=1)
endif()

set (heif_convert_sources
  ${heif_convert_sources}
  encoder_jpeg.cc
  encoder_jpeg.h
)
set (additional_libraries
  ${additional_libraries}
  ${JPEG_LIBRARIES}
)
set (additional_includes
  ${additional_includes}
  ${JPEG_INCLUDE_DIRS}
  ${JPEG_INCLUDE_DIR}
)

if(UNIX OR MINGW)
  include (${CMAKE_ROOT}/Modules/FindPkgConfig.cmake)
  pkg_check_modules (LIBPNG libpng)
  if(LIBPNG_FOUND)
    add_definitions(-DHAVE_LIBPNG=1)
    set (heif_convert_sources
      ${heif_convert_sources}
      encoder_png.cc
      encoder_png.h
    )
    set (additional_link_directories
      ${additional_link_directories}
      ${LIBPNG_LIBRARY_DIRS}
    )
    set (additional_libraries
      ${additional_libraries}
      ${LIBPNG_LINK_LIBRARIES} ${LIBPNG_LIBRARIES}
    )
    set (additional_includes
      ${additional_includes}
      ${LIBPNG_INCLUDE_DIRS}
    )
  endif()
endif()

set (heif_info_sources
  heif_info.cc
)

set (heif_enc_sources
  heif_enc.cc
)

set (heif_test_sources
  heif_test.cc
)


add_library(heif-convert SHARED ${heif_convert_sources})

target_link_directories (heif-convert PRIVATE ${additional_link_directories})
target_link_libraries (heif-convert PRIVATE heif ${additional_libraries})
target_include_directories(heif-convert PRIVATE ${additional_includes})
install(TARGETS heif-convert RUNTIME DESTINATION ${CMAKE_INSTALL_BINDIR})
install(FILES heif-convert.1 DESTINATION ${CMAKE_INSTALL_MANDIR}/man1)